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
    response = requests.get(url, timeout=5)
    if response.status_code != 200:
        return {
            "ok": False,
            "error": "backend request failed",
            "http_status": response.status_code,
        }

    categories = response.json()
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
