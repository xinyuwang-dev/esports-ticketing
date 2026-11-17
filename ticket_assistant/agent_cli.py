import argparse
import asyncio
import json
import os
import sys

from agents import (
    AgentsException,
    OpenAIChatCompletionsModel,
    RunConfig,
    Runner,
    RunResult,
    ToolCallItem,
    ToolCallOutputItem,
)
from openai import APIStatusError, AsyncOpenAI, OpenAIError
from openai.types.responses import ResponseFunctionToolCall

from .agent_tools import ticket_agent

GEMINI_MODEL = "gemini-3.1-flash-lite"
GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"


def _tool_records(result: RunResult) -> list[dict]:
    outputs = {
        item.call_id: item.output
        for item in result.new_items
        if isinstance(item, ToolCallOutputItem)
    }
    records = []
    for item in result.new_items:
        if not isinstance(item, ToolCallItem) or not isinstance(
            item.raw_item, ResponseFunctionToolCall
        ):
            continue
        call = item.raw_item
        try:
            arguments = json.loads(call.arguments)
        except json.JSONDecodeError:
            arguments = call.arguments
        records.append({
            "tool": call.name,
            "call_id": call.call_id,
            "arguments": arguments,
            "result": outputs.get(call.call_id),
        })
    return records


async def _run_gemini(question: str, api_key: str) -> RunResult:
    async with AsyncOpenAI(
        api_key=api_key, base_url=GEMINI_BASE_URL, max_retries=0, timeout=30.0
    ) as client:
        model = OpenAIChatCompletionsModel(model=GEMINI_MODEL, openai_client=client)
        return await Runner.run(
            ticket_agent, question, max_turns=3,
            run_config=RunConfig(model=model, tracing_disabled=True),
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Ask the read-only ticket assistant.")
    parser.add_argument("question", help="The question to ask the assistant.")
    parser.add_argument("--provider", choices=("openai", "gemini"), default="openai",
                        help="Model provider (default: openai).")
    parser.add_argument("--show-tools", action="store_true", help="Print tool records to stderr.")
    args = parser.parse_args(argv)

    key_name = "GEMINI_API_KEY" if args.provider == "gemini" else "OPENAI_API_KEY"
    api_key = os.getenv(key_name, "").strip()
    if not api_key:
        print(f"{key_name} is not configured. Set it locally before running the Agent CLI.",
              file=sys.stderr)
        return 2

    try:
        if args.provider == "gemini":
            result = asyncio.run(_run_gemini(args.question, api_key))
        else:
            result = Runner.run_sync(
                ticket_agent, args.question, max_turns=4, run_config=RunConfig(tracing_disabled=True)
            )
    except APIStatusError as error:
        print(f"Agent run failed ({type(error).__name__}, HTTP {error.status_code}).", file=sys.stderr)
        return 1
    except (AgentsException, OpenAIError) as error:
        print(f"Agent run failed ({type(error).__name__}).", file=sys.stderr)
        return 1

    if args.show_tools:
        print(json.dumps(_tool_records(result), ensure_ascii=False), file=sys.stderr)
    print(result.final_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
