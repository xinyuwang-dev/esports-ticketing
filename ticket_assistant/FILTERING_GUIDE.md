# Ticket category filtering guide

The requirement is to query event 1 and keep categories with a current price
of at most 100 and at least 2 tickets available. Use the backend's `currentPrice`
and `availableStock`; both conditions must be met.
Prices use the backend's numeric units. The DTO has no currency field.

The key concepts are filtering, validation and query-time snapshots.
A query does not reserve stock.

## Start with the call chain

```text
Agent CLI → Runner → the only tool, query_ticket_categories
  Without filters → query layer's query_ticket_categories
  With filters → filter_ticket_categories → query_ticket_categories
  → fetch_categories → GET /api/events/{event_id}/ticket-categories
  → validate the list and fields → filter → tool result → Runner → answer
```

- `fetch_categories` in [ticket_query.py](ticket_query.py) handles HTTP and response validation.
- `query_ticket_categories(event_id)` in the same file keeps the ordinary query, including zero-stock categories.
- `filter_ticket_categories(event_id, min_stock=1, max_price=None)` applies the filters.
- [agent_tools.py](agent_tools.py) defines the tool and Agent instructions. It registers only this tool.
- `_tool_records` in [agent_cli.py](agent_cli.py) pairs arguments and results using `call_id`.

The ordinary command `python -B -m ticket_assistant` still queries all categories for event 1.
The tool also queries all categories when both filters are omitted or `null`.
Filtering applies when a stock minimum or budget is provided. A budget-only query uses a stock minimum of 1.
Pass `min_stock=1` explicitly when asking for available tickets.

## The core function

Start with this query-layer function. `_is_nonnegative_number` rejects booleans,
strings, negative numbers, NaN and infinity. All three parameter checks happen
before the HTTP request.

```python
def filter_ticket_categories(
    event_id: int, min_stock: int = 1, max_price: int | float | None = None
) -> dict:
    """Filter a validated backend snapshot without reserving tickets."""
    if type(event_id) is not int or event_id <= 0:
        return {"ok": False, "error": "event_id must be a positive integer"}
    if type(min_stock) is not int or min_stock <= 0:
        return {"ok": False, "error": "min_stock must be a positive integer"}
    if max_price is not None and not _is_nonnegative_number(max_price):
        return {"ok": False, "error": "max_price must be a finite non-negative number or None"}

    filters = {"min_stock": min_stock, "max_price": max_price}
    source = query_ticket_categories(event_id)
    if not source["ok"]:
        return {**source, "event_id": event_id, "filters": filters}

    matches = []
    for category in source["categories"]:
        if category["availableStock"] < min_stock:
            continue
        if max_price is not None and category["currentPrice"] > max_price:
            continue
        matches.append(category)

    return {
        "ok": True,
        "event_id": event_id,
        "filters": filters,
        "source_count": source["count"],
        "count": len(matches),
        "categories": matches,
    }
```

`continue` moves to the next record. `append` keeps every original field of a match.
It does not substitute `basePrice` for `currentPrice` or calculate a new quote.

## A fixed simulated example

The following data is for learning and offline testing. It does not represent
live stock or experimental results. The complete tool arguments are shown below.

```json
{"event_id": 1, "min_stock": 2, "max_price": 100}
```

The simulated backend returns the following list.

```json
[
  {"id": 1, "categoryName": "Standard", "currentPrice": 100,
   "availableStock": 2, "basePrice": 80, "initialStock": 10,
   "dynamicPricing": true},
  {"id": 2, "categoryName": "VIP", "currentPrice": 101.0, "availableStock": 5},
  {"id": 3, "categoryName": "Balcony", "currentPrice": 70.0, "availableStock": 1},
  {"id": 4, "categoryName": "Sold out", "currentPrice": 0.0, "availableStock": 0}
]
```

1. Standard has exactly 2 tickets available and a price of exactly 100, so both
   inclusive boundaries pass. `matches` changes from `[]` to `[Standard]`.
2. VIP has enough stock, but its price of 101 exceeds the limit. Skip it;
   `matches` remains `[Standard]`.
3. Balcony has only 1 ticket available. Skip it before checking the price.
   Its lower price cannot compensate for insufficient stock; `matches` remains `[Standard]`.
4. Sold out has zero stock. Skip it; `matches` remains `[Standard]`.

The complete tool result follows.

```json
{
  "ok": true,
  "event_id": 1,
  "filters": {"min_stock": 2, "max_price": 100},
  "source_count": 4,
  "count": 1,
  "categories": [
    {"id": 1, "categoryName": "Standard", "currentPrice": 100,
     "availableStock": 2, "basePrice": 80, "initialStock": 10,
     "dynamicPricing": true}
  ]
}
```

`source_count` is the number of categories before filtering; `count` is the number returned.
The ordinary query layer keeps its original `ok/categories/count` response,
where `count` covers all categories. The ordinary tool result additionally includes
`event_id`, `filters=null` and a `source_count` equal to `count`.

## Distinguishing four result paths

- Invalid parameters return `ok=False` immediately, without sending HTTP.
  Python's `bool` is a subclass of `int`, but `type(...) is int` still rejects `True`.
- Connection failures, timeouts, non-200 responses, invalid JSON and field errors return `ok=False`.
  The filter preserves the query error details instead of turning a failure into an empty list.
- An empty source list is a successful query with `source_count=0` and `count=0`.
  This endpoint cannot distinguish a missing event from an event without configured categories.
- A non-empty source with no matches is also successful, with `source_count>0` and `count=0`.
  Report that no categories match the filters, without claiming that the backend has no categories.

For example, the complete result for no matches with the same arguments is shown below.

```json
{"ok": true, "event_id": 1, "filters": {"min_stock": 2, "max_price": 100},
 "source_count": 1, "count": 0, "categories": []}
```

For an empty source list, only `source_count` changes to 0.
The complete HTTP 500 result follows. A failed result has no success-count fields.

```json
{"ok": false, "error": "backend request failed", "http_status": 500,
 "event_id": 1, "filters": {"min_stock": 2, "max_price": 100}}
```

## SDK parameter validation

The local environment uses Agents SDK 0.24.0 and Pydantic 2.14.3.
The tool applies strict integer and numeric types with range constraints.
The schema requires all three properties; use `null` for optional filters.
Local SDK invocation still accepts the old arguments containing only `event_id`,
using the function defaults for the filters. The tool accepts `None` for an omitted
stock filter in an ordinary query. Passing `min_stock=None` directly to the filtering
function is a parameter error.

When SDK parameter validation rejects a call, it returns generic tool-error text
without entering the query function. This is separate from the query layer's
`ok=False` dictionaries. Tests check both the error result and that the query
function was not called.

## Verified behaviour and restarting locally

Run the offline tests from the repository root.

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s ticket_assistant/tests -v
```

All 30 tests in the Python offline regression suite passed.
They cover boundaries, invalid inputs, malformed responses, ordinary queries retaining
zero-stock categories, the actual Runner invoking the registered tool, full results
reaching the next model input, and `--show-tools` pairing by call ID.
The existing OpenAI and Gemini offline CLI tests also run.
Model answers in these tests are scripted; they verify SDK calls and data transfer.
A real model's interpretation of filtering requests and its answers have not been verified
for this extension. The Agent is instructed to answer in English by default and preserve
backend event and category names. Real-model language behaviour has not been rechecked.

A real read-only GET completed on 2026-11-25 at 21:17 Beijing time, using
`http://localhost:18080/api/events/1/ticket-categories`, with HTTP 200.
That response contained Standard at 80.0 with 90 tickets available, and VIP at 360.0
with 5 tickets available. With `min_stock=2, max_price=100`, the filter returned
Standard with `source_count=2, count=1`. Its fields matched the data from the same GET.
These values describe that query-time snapshot only.

The Java process started for that check had PID 20296 and process-level
`APP_ORDER_MODE=mysql`. The startup condition report explicitly excluded
`RedisInventoryInitializer`. Next time, first check whether the service is still
running and verify the actual PID and available port again. To restart it, use the
existing IntelliJ database run configuration and set `APP_ORDER_MODE=mysql` and
`SERVER_PORT=18080` only for the process being started. Once the service is ready,
set the query address in the terminal and run the ordinary entry point.

```powershell
$env:TICKET_SERVICE_BASE_URL = 'http://localhost:18080'
.\.venv\Scripts\python.exe -B -m ticket_assistant
```

No real model requests were made for this extension. Before testing a model's
interpretation of filters and its final answer, separately confirm possible charges
and use a locally configured terminal. Compare the answer with `--show-tools`
records from the same run.

## Try a small change

Keep the filtering rules and response fields intact, but return matches in ascending
`currentPrice` order. Keep the backend order for equal prices.
Modify only `matches`, after the loop and before `return` below.

```python
        matches.append(category)

    return {
        "ok": True,
        # Keep the remaining response fields as they are.
    }
```

First work out the order for the simulated data above with
`min_stock=1, max_price=None`. Then run the offline tests and add an assertion
that checks the order.

---

## Exercise answer

Insert `matches.sort(key=lambda category: category["currentPrice"])` after the loop.
Python's sort is stable, so records with equal prices keep their original order.
For this simulated input, the order is Balcony, Standard, VIP. The zero-stock
record remains excluded. This exercise change has not been added to the implementation.
