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

    return {"ok": True}


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
