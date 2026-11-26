from typing import Annotated

from agents import Agent, function_tool
from pydantic import Field, StrictFloat, StrictInt

from . import ticket_query


@function_tool
def query_ticket_categories(
    event_id: Annotated[StrictInt, Field(gt=0)],
    min_stock: Annotated[StrictInt, Field(gt=0)] | None = None,
    max_price: (
        Annotated[StrictInt, Field(ge=0)]
        | Annotated[StrictFloat, Field(ge=0, allow_inf_nan=False)]
        | None
    ) = None,
) -> dict:
    """Query all categories or filter a read-only price and stock snapshot.

    Args:
        event_id: Positive integer event ID, never a boolean.
        min_stock: Positive minimum available stock. Null means no stock filter
            unless max_price is set, in which case the minimum defaults to 1.
            Set 1 explicitly when the user asks for available tickets.
        max_price: Inclusive finite non-negative current-price limit, or null
            for no price limit. Use backend price units without assuming currency.
    """
    if min_stock is not None or max_price is not None:
        return ticket_query.filter_ticket_categories(
            event_id, min_stock=1 if min_stock is None else min_stock, max_price=max_price
        )

    result = ticket_query.query_ticket_categories(event_id)
    result = {**result, "event_id": event_id, "filters": None}
    if result["ok"]:
        result["source_count"] = result["count"]
    return result


ticket_agent = Agent(
    name="Read-only ticket assistant",
    instructions=(
        "Respond in English by default, including clarification questions, query "
        "failures, empty results and zero-stock reports. Preserve event and ticket "
        "category names exactly as returned by the backend; do not translate them. "
        "If the user has not provided an event ID, ask for it without calling a tool. "
        "For ticket queries with an event ID, use query_ticket_categories, the only "
        "available read-only tool. Pass the user's stock minimum and price limit as "
        "min_stock and max_price. Both limits are inclusive and must be met together. "
        "When no filters are requested, pass null for both to include zero-stock "
        "categories. For available tickets use min_stock=1; a price-only filter also "
        "defaults to min_stock=1. A null max_price means no price limit. Never invent "
        "a missing event ID, and do not coerce invalid parameters. Report the actual "
        "event_id, filters, current prices and stock using only this run's result. "
        "source_count is the original list size; count is the returned list size. "
        "If ok is false or tool input validation fails, report failure, not an empty "
        "successful query. If source_count is positive but count is zero, say that "
        "no categories match the filters. If source_count is zero, say no categories "
        "were returned; this endpoint cannot distinguish a missing event from an "
        "event without categories. Report zero stock as zero. Use currentPrice from "
        "the backend and do not calculate a new quote or invent a currency symbol. "
        "Prices and stock are query-time snapshots; this query reserves no tickets. "
        "The purchase flow must recheck stock and calculate the final transaction "
        "price. Never claim that a successful query guarantees a purchase."
    ),
    tools=[query_ticket_categories],
)
