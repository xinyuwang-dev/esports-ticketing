import ast
import copy
import json
import os
import socket
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from unittest.mock import call, patch

import httpx2
from agents import Model, ModelResponse, RunConfig, Runner, Usage
from openai import AsyncOpenAI
from openai.types.responses import ResponseFunctionToolCall, ResponseOutputMessage, ResponseOutputText

from ticket_assistant import agent_cli, ticket_query
from ticket_assistant.agent_tools import ticket_agent


QUESTION = "Please look up the ticket categories, current prices and stock for event ID 1."


class ScriptedModel(Model):
    def __init__(self, final_text: str, arguments: dict | None = None):
        self.final_text = final_text
        self.arguments = {"event_id": 1} if arguments is None else arguments
        self.inputs = []
        self.tool_names = []

    async def get_response(self, system_instructions, input, model_settings, tools,
                           output_schema, handoffs, tracing, **kwargs):
        assert tracing.is_disabled()
        self.inputs.append(copy.deepcopy(input))
        self.tool_names.append([tool.name for tool in tools])
        if len(self.inputs) == 1:
            item = ResponseFunctionToolCall(
                type="function_call", name="query_ticket_categories", call_id="offline-ticket",
                arguments=json.dumps(self.arguments),
            )
        elif len(self.inputs) == 2:
            item = ResponseOutputMessage(
                id="offline-answer", type="message", role="assistant", status="completed",
                content=[ResponseOutputText(type="output_text", text=self.final_text, annotations=[])],
            )
        else:
            raise AssertionError("Unexpected third model call")
        return ModelResponse(output=[item], usage=Usage(), response_id=None)

    def stream_response(self, *args, **kwargs):
        raise AssertionError("This test does not use streaming")


class AgentRunnerTests(unittest.IsolatedAsyncioTestCase):
    async def check_tool_round_trip(self, tool_result: dict, scripted_answer: str):
        model = ScriptedModel(scripted_answer)
        expected_result = {**tool_result, "event_id": 1, "filters": None}
        if tool_result["ok"]:
            expected_result["source_count"] = tool_result["count"]
        blocked = AssertionError("No network calls are allowed in the offline Runner test")
        with (patch.object(ticket_query, "query_ticket_categories", return_value=tool_result) as query,
              patch.object(socket.socket, "connect", side_effect=blocked),
              patch.object(socket, "getaddrinfo", side_effect=blocked)):
            result = await Runner.run(
                ticket_agent, QUESTION, max_turns=4,
                run_config=RunConfig(model=model, tracing_disabled=True),
            )
        query.assert_called_once_with(1)
        self.assertIs(type(query.call_args.args[0]), int)
        self.assertEqual(model.tool_names, [["query_ticket_categories"]] * 2)
        self.assertEqual(len(model.inputs), 2)
        second_input = model.inputs[1]
        calls = [item for item in second_input if item.get("type") == "function_call"]
        outputs = [item for item in second_input if item.get("type") == "function_call_output"]
        self.assertEqual(len(calls), 1)
        self.assertEqual(len(outputs), 1)
        self.assertEqual(calls[0]["name"], "query_ticket_categories")
        self.assertEqual(json.loads(calls[0]["arguments"]), {"event_id": 1})
        self.assertEqual(calls[0]["call_id"], outputs[0]["call_id"])
        # This SDK version converts ordinary dictionary results to Python text for replay.
        self.assertEqual(ast.literal_eval(outputs[0]["output"]), expected_result)
        self.assertEqual(result.final_output, scripted_answer)

        for show_tools in (False, True):
            stdout, stderr = StringIO(), StringIO()
            argv = [QUESTION, "--show-tools"] if show_tools else [QUESTION]
            with (patch.dict(os.environ, {"OPENAI_API_KEY": "offline-test-placeholder"}),
                  patch.object(Runner, "run_sync", return_value=result) as run_sync,
                  redirect_stdout(stdout), redirect_stderr(stderr)):
                self.assertEqual(agent_cli.main(argv), 0)
            self.assertEqual(run_sync.call_args.args, (ticket_agent, QUESTION))
            self.assertTrue(run_sync.call_args.kwargs["run_config"].tracing_disabled)
            self.assertEqual(stdout.getvalue(), scripted_answer + "\n")
            if show_tools:
                self.assertEqual(json.loads(stderr.getvalue()), [{
                    "tool": "query_ticket_categories", "call_id": outputs[0]["call_id"],
                    "arguments": {"event_id": 1}, "result": expected_result,
                }])
            else:
                self.assertEqual(stderr.getvalue(), "")

    async def test_normal_result_with_zero_stock(self):
        await self.check_tool_round_trip({
            "ok": True,
            "categories": [{"id": 4, "categoryName": "VIP", "currentPrice": 360.0, "availableStock": 0}],
            "count": 1,
        }, "VIP: current price 360.0; available stock 0 at query time.")

    async def test_empty_ticket_list(self):
        await self.check_tool_round_trip(
            {"ok": True, "categories": [], "count": 0}, "No ticket categories were returned."
        )

    async def test_backend_failure(self):
        await self.check_tool_round_trip(
            {"ok": False, "error": "backend request failed", "http_status": 500}, "The query failed."
        )


class AgentCliTests(unittest.TestCase):
    def test_missing_key_stops_before_runner(self):
        stdout, stderr = StringIO(), StringIO()
        with (patch.dict(os.environ), patch.object(Runner, "run_sync") as runner,
              redirect_stdout(stdout), redirect_stderr(stderr)):
            os.environ.pop("OPENAI_API_KEY", None)
            self.assertEqual(agent_cli.main([QUESTION]), 2)
        runner.assert_not_called()
        self.assertEqual(stdout.getvalue(), "")
        self.assertIn("OPENAI_API_KEY is not configured", stderr.getvalue())

    def test_runner_exception_returns_error_even_with_show_tools(self):
        stdout, stderr = StringIO(), StringIO()
        with (patch.dict(os.environ, {"OPENAI_API_KEY": "offline-test-placeholder"}),
              patch.object(agent_cli.Runner, "run_sync",
                           side_effect=agent_cli.AgentsException("offline failure")) as runner,
              redirect_stdout(stdout), redirect_stderr(stderr)):
            exit_code = agent_cli.main([QUESTION, "--show-tools"])

        runner.assert_called_once()
        self.assertEqual(runner.call_args.args, (ticket_agent, QUESTION))
        self.assertEqual(exit_code, 1)
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(stderr.getvalue(), "Agent run failed (AgentsException).\n")

    def test_missing_gemini_key_stops_before_creating_client(self):
        stdout, stderr = StringIO(), StringIO()
        with (patch.dict(os.environ, {"OPENAI_API_KEY": "offline-test-placeholder"}),
              patch.object(agent_cli, "AsyncOpenAI") as client,
              redirect_stdout(stdout), redirect_stderr(stderr)):
            os.environ.pop("GEMINI_API_KEY", None)
            self.assertEqual(agent_cli.main(["--provider", "gemini", QUESTION]), 2)

        client.assert_not_called()
        self.assertEqual(stdout.getvalue(), "")
        self.assertIn("GEMINI_API_KEY is not configured", stderr.getvalue())

    def test_gemini_cli_replays_tool_result_and_thought_signature(self):
        source_result = {
            "ok": True,
            "categories": [{"id": 4, "categoryName": "VIP", "currentPrice": 360.0, "availableStock": 0}],
            "count": 1,
        }
        expected_result = {**source_result, "event_id": 1, "filters": None, "source_count": 1}
        self.check_gemini_cli_replay(
            {"event_id": 1}, "query_ticket_categories", source_result, expected_result, call(1),
        )

    def test_gemini_cli_replays_filter_parameters_and_full_results(self):
        arguments = {"event_id": 1, "min_stock": 2, "max_price": 100}
        expected_result = {
            "ok": True, "event_id": 1, "filters": {"min_stock": 2, "max_price": 100},
            "source_count": 2, "count": 1,
            "categories": [{"id": 3, "categoryName": "Standard", "currentPrice": 100, "availableStock": 2}],
        }
        self.check_gemini_cli_replay(
            arguments, "filter_ticket_categories", expected_result, expected_result,
            call(1, min_stock=2, max_price=100),
        )

    def check_gemini_cli_replay(self, arguments, query_name, query_result, expected_result, expected_call):
        answer = "Preset Gemini answer."
        signature = "mock-gemini-thought-signature"
        call_id = "call_gemini_offline"
        requests_seen = []

        async def handle(request):
            self.assertEqual(request.method, "POST")
            self.assertEqual(str(request.url), agent_cli.GEMINI_BASE_URL + "chat/completions")
            payload = json.loads(request.content)
            requests_seen.append(payload)
            self.assertEqual(payload["model"], agent_cli.GEMINI_MODEL)
            if len(requests_seen) == 1:
                message = {"role": "assistant", "content": None, "tool_calls": [{
                    "id": call_id,
                    "type": "function",
                    "function": {"name": "query_ticket_categories", "arguments": json.dumps(arguments)},
                    "extra_content": {"google": {"thought_signature": signature}},
                }]}
                finish_reason = "tool_calls"
            else:
                self.assertEqual(len(requests_seen), 2)
                message = {"role": "assistant", "content": answer}
                finish_reason = "stop"
            return httpx2.Response(200, json={
                "id": f"gemini-mock-{len(requests_seen)}", "object": "chat.completion",
                "created": 0, "model": agent_cli.GEMINI_MODEL,
                "choices": [{"index": 0, "finish_reason": finish_reason, "message": message}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            })

        def mock_client(**kwargs):
            return AsyncOpenAI(**kwargs, http_client=httpx2.AsyncClient(
                transport=httpx2.MockTransport(handle), trust_env=False,
            ))

        stdout, stderr = StringIO(), StringIO()
        with (patch.dict(os.environ, {"GEMINI_API_KEY": "offline-test-placeholder", "OPENAI_API_KEY": ""}),
              patch.object(agent_cli, "AsyncOpenAI", side_effect=mock_client) as client,
              patch.object(ticket_query, query_name, return_value=query_result) as query,
              redirect_stdout(stdout), redirect_stderr(stderr)):
            exit_code = agent_cli.main(["--provider", "gemini", "--show-tools", QUESTION])

        self.assertEqual(exit_code, 0, stderr.getvalue())
        client.assert_called_once()
        self.assertEqual(client.call_args.kwargs["max_retries"], 0)
        self.assertEqual(query.call_count, 1)
        self.assertEqual(query.call_args, expected_call)
        self.assertEqual(len(requests_seen), 2)
        self.assertEqual([tool["function"]["name"] for tool in requests_seen[0]["tools"]],
                         ["query_ticket_categories"])
        first_messages = requests_seen[0]["messages"]
        self.assertTrue(any(msg["role"] == "user" and msg["content"] == QUESTION for msg in first_messages))
        second_messages = requests_seen[1]["messages"]
        replayed = next(call for msg in second_messages for call in (msg.get("tool_calls") or []))
        self.assertEqual(replayed["id"], call_id)
        self.assertEqual(json.loads(replayed["function"]["arguments"]), arguments)
        self.assertEqual(replayed["extra_content"]["google"]["thought_signature"], signature)
        tool_message = next(msg for msg in second_messages if msg["role"] == "tool")
        self.assertEqual(tool_message["tool_call_id"], call_id)
        self.assertEqual(ast.literal_eval(tool_message["content"]), expected_result)
        self.assertEqual(stdout.getvalue(), answer + "\n")
        self.assertEqual(json.loads(stderr.getvalue()), [{
            "tool": "query_ticket_categories", "call_id": call_id,
            "arguments": arguments, "result": expected_result,
        }])
