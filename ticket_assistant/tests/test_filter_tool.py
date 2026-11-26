import ast
import copy
import json
import os
import socket
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from unittest.mock import patch

from agents import RunConfig, Runner
from agents.tool_context import ToolContext
from openai.types.responses import ResponseFunctionToolCall

from ticket_assistant import agent_cli, ticket_query
from ticket_assistant.agent_tools import ticket_agent
from ticket_assistant.tests.test_agent_cli import ScriptedModel
from ticket_assistant.tests.test_ticket_query import CATEGORIES


class FilterToolTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        blocked = AssertionError("Offline tests must not access the network")
        for owner, name in ((socket.socket, "connect"), (socket, "getaddrinfo")):
            patcher = patch.object(owner, name, side_effect=blocked)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.tool, = ticket_agent.tools

    async def invoke(self, arguments):
        raw = json.dumps(arguments)
        context = ToolContext(context=None, tool_name=self.tool.name,
                              tool_call_id="offline-validation", tool_arguments=raw,
                              run_config=RunConfig(tracing_disabled=True))
        return await self.tool.on_invoke_tool(context, raw)

    def test_actual_schema_has_one_tool_and_nullable_filter_parameters(self):
        self.assertEqual(self.tool.name, "query_ticket_categories")
        schema = self.tool.params_json_schema
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(set(schema["properties"]), {"event_id", "min_stock", "max_price"})
        self.assertEqual(set(schema["required"]), {"event_id", "min_stock", "max_price"})
        self.assertEqual(schema["properties"]["event_id"]["type"], "integer")
        self.assertEqual(schema["properties"]["event_id"]["exclusiveMinimum"], 0)
        for name in ("min_stock", "max_price"):
            self.assertIn({"type": "null"}, schema["properties"][name]["anyOf"])

    async def test_sdk_rejects_bad_values_without_calling_the_query_layer(self):
        cases = [{}]
        for field in ("event_id", "min_stock"):
            for value in (True, False, "2", 2.0, 0, -1, [], {}):
                cases.append({"event_id": 1, "min_stock": 2, "max_price": 100, field: value})
        for value in (True, False, "100", -1, float("nan"), float("inf"), float("-inf"), [], {}):
            cases.append({"event_id": 1, "max_price": value})
        cases.append({"event_id": None})
        with (patch.object(ticket_query, "query_ticket_categories") as plain,
              patch.object(ticket_query, "filter_ticket_categories") as filtered,
              patch.object(ticket_query.requests, "get") as get,
              self.assertLogs(level="WARNING")):
            for arguments in cases:
                with self.subTest(arguments=arguments):
                    result = await self.invoke(arguments)
                    self.assertIsInstance(result, str)
                    self.assertEqual(result, "An error occurred while running the tool. Please try again.")
            plain.assert_not_called()
            filtered.assert_not_called()
            get.assert_not_called()

    async def test_price_only_filter_defaults_to_one_and_preserves_numeric_types(self):
        expected = {"ok": True, "event_id": 1, "filters": {"min_stock": 1, "max_price": 100},
                    "source_count": 4, "count": 2, "categories": [CATEGORIES[0], CATEGORIES[2]]}
        with patch.object(ticket_query, "filter_ticket_categories", return_value=expected) as filtered:
            self.assertEqual(await self.invoke({"event_id": 1, "max_price": 100}), expected)
        filtered.assert_called_once_with(1, min_stock=1, max_price=100)
        self.assertIs(type(filtered.call_args.kwargs["max_price"]), int)
        with patch.object(ticket_query, "filter_ticket_categories", return_value=expected) as filtered:
            await self.invoke({"event_id": 1, "min_stock": 2, "max_price": 100.0})
        filtered.assert_called_once_with(1, min_stock=2, max_price=100.0)
        self.assertIs(type(filtered.call_args.kwargs["max_price"]), float)

    async def test_omitted_or_null_filters_keep_ordinary_query_including_zero_stock(self):
        source = {"ok": True, "categories": CATEGORIES, "count": 4}
        for arguments in ({"event_id": 1}, {"event_id": 1, "min_stock": None, "max_price": None}):
            with self.subTest(arguments=arguments):
                with (patch.object(ticket_query, "query_ticket_categories", return_value=source) as plain,
                      patch.object(ticket_query, "filter_ticket_categories") as filtered):
                    self.assertEqual(await self.invoke(arguments), {
                        **source, "event_id": 1, "filters": None, "source_count": 4,
                    })
                plain.assert_called_once_with(1)
                filtered.assert_not_called()
                self.assertEqual(source, {"ok": True, "categories": CATEGORIES, "count": 4})

    async def test_stock_only_filter_allows_null_budget(self):
        with patch.object(ticket_query, "filter_ticket_categories", return_value={"ok": True}) as filtered:
            await self.invoke({"event_id": 1, "min_stock": 2, "max_price": None})
        filtered.assert_called_once_with(1, min_stock=2, max_price=None)

    async def test_runner_replays_full_filter_results_and_cli_records(self):
        arguments = {"event_id": 1, "min_stock": 2, "max_price": 100}
        filters = {"min_stock": 2, "max_price": 100}
        scenarios = [
            (CATEGORIES, 200, {"ok": True, "event_id": 1, "filters": filters,
                              "source_count": 4, "count": 1, "categories": [CATEGORIES[0]]}),
            ([CATEGORIES[1]], 200, {"ok": True, "event_id": 1, "filters": filters,
                                    "source_count": 1, "count": 0, "categories": []}),
            ([], 200, {"ok": True, "event_id": 1, "filters": filters,
                       "source_count": 0, "count": 0, "categories": []}),
            (None, 500, {"ok": False, "error": "backend request failed", "http_status": 500,
                         "event_id": 1, "filters": filters}),
        ]
        for categories, status, expected in scenarios:
            with self.subTest(status=status, categories=categories):
                model = ScriptedModel("Preset offline answer.", arguments)
                with (patch.object(ticket_query.requests, "get") as get,
                      patch.object(ticket_query, "filter_ticket_categories",
                                   wraps=ticket_query.filter_ticket_categories) as filtered):
                    get.return_value.status_code = status
                    get.return_value.json.return_value = copy.deepcopy(categories)
                    result = await Runner.run(
                        ticket_agent, "Find ticket categories for event 1 with a current price at most 100 and at least 2 tickets available.",
                        run_config=RunConfig(model=model, tracing_disabled=True), max_turns=3,
                    )
                filtered.assert_called_once_with(1, min_stock=2, max_price=100)
                self.assertEqual(model.tool_names, [["query_ticket_categories"]] * 2)
                outputs = [item for item in model.inputs[1] if item.get("type") == "function_call_output"]
                self.assertEqual(len(outputs), 1)
                self.assertEqual(ast.literal_eval(outputs[0]["output"]), expected)
                record, = agent_cli._tool_records(result)
                self.assertEqual(record, {
                    "tool": "query_ticket_categories", "call_id": outputs[0]["call_id"],
                    "arguments": arguments, "result": expected,
                })
                self.assertEqual(result.final_output, "Preset offline answer.")

                for show_tools in (False, True):
                    stdout, stderr = StringIO(), StringIO()
                    argv = ["Offline question"] + (["--show-tools"] if show_tools else [])
                    with (patch.dict(os.environ, {"OPENAI_API_KEY": "offline-test-placeholder"}),
                          patch.object(Runner, "run_sync", return_value=result),
                          redirect_stdout(stdout), redirect_stderr(stderr)):
                        self.assertEqual(agent_cli.main(argv), 0)
                    self.assertEqual(stdout.getvalue(), "Preset offline answer.\n")
                    self.assertEqual(stderr.getvalue(), json.dumps([record], ensure_ascii=False) + "\n"
                                     if show_tools else "")

    async def test_records_pair_two_calls_by_id_even_when_outputs_arrive_in_reverse_order(self):
        class TwoCallModel(ScriptedModel):
            async def get_response(self, *args, **kwargs):
                response = await super().get_response(*args, **kwargs)
                if len(self.inputs) == 1:
                    response.output.append(ResponseFunctionToolCall(
                        type="function_call", name="query_ticket_categories", call_id="second-ticket",
                        arguments='{"event_id": 2, "min_stock": 2, "max_price": 100}',
                    ))
                return response

        results = {
            1: {"ok": True, "event_id": 1, "filters": {"min_stock": 2, "max_price": 100},
                "source_count": 1, "count": 1, "categories": [CATEGORIES[0]]},
            2: {"ok": True, "event_id": 2, "filters": {"min_stock": 2, "max_price": 100},
                "source_count": 2, "count": 0, "categories": []},
        }
        model = TwoCallModel("Preset two-event answer.", {"event_id": 1, "min_stock": 2, "max_price": 100})
        with patch.object(ticket_query, "filter_ticket_categories", side_effect=lambda event_id, **kw: results[event_id]):
            result = await Runner.run(ticket_agent, "Offline two-event question",
                                      run_config=RunConfig(model=model, tracing_disabled=True), max_turns=3)
        result.new_items.reverse()
        records = agent_cli._tool_records(result)
        self.assertEqual(len(records), 2)
        for record in records:
            self.assertEqual(record["result"], results[record["arguments"]["event_id"]])


if __name__ == "__main__":
    unittest.main()
