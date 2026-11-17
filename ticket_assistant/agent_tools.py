from agents import Agent, function_tool
from pydantic import StrictInt

from . import ticket_query


@function_tool
def query_ticket_categories(event_id: StrictInt) -> dict:
    """Look up ticket categories, current prices and stock for a positive event ID."""
    return ticket_query.query_ticket_categories(event_id)


ticket_agent = Agent(
    name="Read-only ticket assistant",
    instructions=(
        "If the user has not provided an event ID, ask for it without calling a tool. "
        "For ticket queries with an event ID, use query_ticket_categories, the only "
        "available read-only tool. Report categories, current prices and available "
        "stock using only the tool result from this run. If ok is false, explain that "
        "the query failed. If ok is true and categories is empty, say that no ticket "
        "categories were returned. Report zero stock as zero. Explain that current "
        "prices and stock are snapshots from the time of the query."
    ),
    tools=[query_ticket_categories],
)
