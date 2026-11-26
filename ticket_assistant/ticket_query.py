import math
import os

import requests


def _validate_category(category: object, record_number: int) -> dict:
    if not isinstance(category, dict):
        return {
            "ok": False,
            "error": "ticket category must be a dictionary",
            "record_number": record_number,
        }

    for field in ("id", "categoryName", "currentPrice", "availableStock"):
        if field not in category:
            return {
                "ok": False,
                "error": "missing required ticket category field",
                "record_number": record_number,
                "field": field,
            }

    field_errors = {
        "id": type(category["id"]) is not int or category["id"] <= 0,
        "categoryName": not isinstance(category["categoryName"], str)
        or not category["categoryName"].strip(),
        "currentPrice": not _is_nonnegative_number(category["currentPrice"]),
        "availableStock": type(category["availableStock"]) is not int
        or category["availableStock"] < 0,
    }
    for field, invalid in field_errors.items():
        if invalid:
            return {
                "ok": False,
                "error": "invalid ticket category field value",
                "record_number": record_number,
                "field": field,
            }

    return {"ok": True}


def _is_nonnegative_number(value: object) -> bool:
    return (
        type(value) in (int, float)
        and value >= 0
        and (type(value) is int or math.isfinite(value))
    )


def fetch_categories(url: str) -> dict:
    """Fetch the ticket-category list from the given endpoint."""
    try:
        response = requests.get(url, timeout=5)
    except requests.exceptions.Timeout:
        return {"ok": False, "error": "ticket service request timed out"}
    except requests.exceptions.ConnectionError:
        return {"ok": False, "error": "cannot connect to ticket service"}

    if response.status_code != 200:
        return {
            "ok": False,
            "error": "backend request failed",
            "http_status": response.status_code,
        }

    try:
        categories = response.json()
    except requests.exceptions.JSONDecodeError:
        return {"ok": False, "error": "ticket service returned invalid JSON"}

    if not isinstance(categories, list):
        return {
            "ok": False,
            "error": "the type of categories is not a list",
        }

    for record_number, category in enumerate(categories, start=1):
        validation = _validate_category(category, record_number)
        if not validation["ok"]:
            return validation

    return {
        "ok": True,
        "categories": categories,
        "count": len(categories),
    }


def query_ticket_categories(event_id: int) -> dict:
    """Query ticket categories for a positive integer event ID."""
    if type(event_id) is not int or event_id <= 0:
        return {"ok": False, "error": "event_id must be a positive integer"}

    base_url = os.getenv("TICKET_SERVICE_BASE_URL", "http://localhost:8080").rstrip("/")
    url = f"{base_url}/api/events/{event_id}/ticket-categories"
    return fetch_categories(url)


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
