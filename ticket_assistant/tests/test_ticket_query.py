import copy
import os
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

import requests

from ticket_assistant import __main__, ticket_query


CATEGORIES = [
    {"id": 1, "categoryName": "Standard", "currentPrice": 100, "availableStock": 2,
     "basePrice": 80, "initialStock": 10, "dynamicPricing": True},
    {"id": 2, "categoryName": "VIP", "currentPrice": 101.0, "availableStock": 5},
    {"id": 3, "categoryName": "Balcony", "currentPrice": 70.0, "availableStock": 1},
    {"id": 4, "categoryName": "Sold out", "currentPrice": 0.0, "availableStock": 0},
]


class TicketQueryTests(unittest.TestCase):
    def setUp(self):
        request_patch = patch.object(ticket_query.requests, "get")
        self.get = request_patch.start()
        self.addCleanup(request_patch.stop)
        self.get.return_value.status_code = 200
        self.get.return_value.json.return_value = copy.deepcopy(CATEGORIES)

    def test_combined_inclusive_filters_preserve_fields_and_backend_price(self):
        result = ticket_query.filter_ticket_categories(1, min_stock=2, max_price=100)
        self.assertEqual(result, {
            "ok": True, "event_id": 1,
            "filters": {"min_stock": 2, "max_price": 100},
            "source_count": 4, "count": 1, "categories": [CATEGORIES[0]],
        })
        self.assertEqual(result["categories"][0]["basePrice"], 80)
        self.assertEqual(result["categories"][0]["currentPrice"], 100)
        self.assertEqual(self.get.return_value.json.return_value, CATEGORIES)

    def test_query_keeps_zero_stock_but_default_filter_excludes_it(self):
        plain = ticket_query.query_ticket_categories(1)
        self.assertEqual(plain, {"ok": True, "categories": CATEGORIES, "count": 4})
        filtered = ticket_query.filter_ticket_categories(1)
        self.assertEqual(filtered["filters"], {"min_stock": 1, "max_price": None})
        self.assertEqual(filtered["categories"], CATEGORIES[:3])
        self.assertEqual(filtered["count"], 3)

    def test_omitted_and_explicit_none_budget_match(self):
        self.assertEqual(ticket_query.filter_ticket_categories(1, min_stock=2),
                         ticket_query.filter_ticket_categories(1, min_stock=2, max_price=None))

    def test_finite_integer_float_and_zero_budgets(self):
        self.assertEqual(ticket_query.filter_ticket_categories(1, max_price=100),
                         ticket_query.filter_ticket_categories(1, max_price=100.0))
        self.get.return_value.json.return_value = [
            {"id": 5, "categoryName": "Free", "currentPrice": 0, "availableStock": 1},
        ]
        self.assertEqual(ticket_query.filter_ticket_categories(1, max_price=0)["count"], 1)

    def test_source_empty_and_no_matches_are_distinct(self):
        no_match = ticket_query.filter_ticket_categories(1, min_stock=6)
        self.get.return_value.json.return_value = []
        empty = ticket_query.filter_ticket_categories(1, min_stock=6)
        self.assertEqual((no_match["source_count"], no_match["count"]), (4, 0))
        self.assertEqual((empty["source_count"], empty["count"]), (0, 0))
        self.assertTrue(no_match["ok"] and empty["ok"])
        self.assertEqual(no_match["categories"], empty["categories"])

    def test_invalid_event_ids_never_send_http(self):
        for event_id in (True, False, 0, -1, 1.0, "1", None, [], {}):
            for query in (ticket_query.query_ticket_categories, ticket_query.filter_ticket_categories):
                with self.subTest(event_id=event_id, entry=query.__name__):
                    self.assertEqual(query(event_id), {
                        "ok": False, "error": "event_id must be a positive integer",
                    })
        self.get.assert_not_called()

    def test_invalid_minimum_stock_never_sends_http(self):
        for min_stock in (True, False, 0, -1, 2.0, "2", None, [], {}):
            with self.subTest(min_stock=min_stock):
                self.assertEqual(ticket_query.filter_ticket_categories(1, min_stock), {
                    "ok": False, "error": "min_stock must be a positive integer",
                })
        self.get.assert_not_called()

    def test_invalid_budget_never_sends_http(self):
        for max_price in (True, False, -1, -0.1, "100", float("nan"),
                          float("inf"), float("-inf"), [], {}):
            with self.subTest(max_price=max_price):
                self.assertEqual(ticket_query.filter_ticket_categories(1, max_price=max_price), {
                    "ok": False, "error": "max_price must be a finite non-negative number or None",
                })
        self.get.assert_not_called()

    def test_connection_timeout_and_http_errors_are_not_empty_successes(self):
        for error, message in (
            (requests.exceptions.ConnectionError, "cannot connect to ticket service"),
            (requests.exceptions.Timeout, "ticket service request timed out"),
            (requests.exceptions.ConnectTimeout, "ticket service request timed out"),
        ):
            with self.subTest(error=error):
                self.get.side_effect = error()
                result = ticket_query.filter_ticket_categories(1, 2, 100)
                self.assertEqual(result, {"ok": False, "error": message, "event_id": 1,
                                          "filters": {"min_stock": 2, "max_price": 100}})
        self.get.side_effect = None
        for status in (404, 500):
            with self.subTest(status=status):
                self.get.return_value.status_code = status
                self.get.return_value.json.reset_mock()
                result = ticket_query.filter_ticket_categories(1)
                self.assertEqual(result["http_status"], status)
                self.assertFalse(result["ok"])
                self.assertNotIn("categories", result)
                self.get.return_value.json.assert_not_called()

    def test_invalid_json_is_an_explicit_failure(self):
        self.get.return_value.json.side_effect = requests.exceptions.JSONDecodeError("bad JSON", "x", 0)
        result = ticket_query.filter_ticket_categories(1)
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"], "ticket service returned invalid JSON")

    def test_invalid_outer_and_record_structure(self):
        cases = [
            ({}, {"error": "the type of categories is not a list"}),
            ([CATEGORIES[0], "wrong"], {"error": "ticket category must be a dictionary",
                                         "record_number": 2}),
            ([{"id": 1, "title": "Event"}], {"error": "missing required ticket category field",
                                               "record_number": 1, "field": "categoryName"}),
        ]
        for categories, details in cases:
            with self.subTest(categories=categories):
                self.get.return_value.json.return_value = categories
                self.assertEqual(ticket_query.filter_ticket_categories(1), {
                    "ok": False, **details, "event_id": 1,
                    "filters": {"min_stock": 1, "max_price": None},
                })

    def test_each_required_field_is_reported_when_missing(self):
        for field in ("id", "categoryName", "currentPrice", "availableStock"):
            with self.subTest(field=field):
                category = dict(CATEGORIES[0])
                del category[field]
                self.get.return_value.json.return_value = [category]
                result = ticket_query.filter_ticket_categories(1)
                self.assertFalse(result["ok"])
                self.assertEqual(result["field"], field)
                self.assertEqual(result["record_number"], 1)

    def test_bad_field_values_fail_before_comparison(self):
        invalid = {
            "id": (True, 0, -1, "1", 1.0, None),
            "categoryName": (None, 1, True, "", "  "),
            "currentPrice": (True, "100", None, -1, [], float("nan"), float("inf")),
            "availableStock": (False, "2", None, -1, 2.0, []),
        }
        for field, values in invalid.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    category = {**CATEGORIES[0], field: value}
                    self.get.return_value.json.return_value = [CATEGORIES[0], category]
                    result = ticket_query.filter_ticket_categories(1)
                    self.assertEqual(result, {
                        "ok": False, "error": "invalid ticket category field value",
                        "record_number": 2, "field": field, "event_id": 1,
                        "filters": {"min_stock": 1, "max_price": None},
                    })

    def test_service_address_and_timeout(self):
        with patch.dict(os.environ, {"TICKET_SERVICE_BASE_URL": "http://localhost:18080/"}):
            ticket_query.filter_ticket_categories(1, 2, 100)
        self.get.assert_called_once_with("http://localhost:18080/api/events/1/ticket-categories", timeout=5)
        with patch.dict(os.environ):
            os.environ.pop("TICKET_SERVICE_BASE_URL", None)
            ticket_query.query_ticket_categories(1)
        self.get.assert_called_with("http://localhost:8080/api/events/1/ticket-categories", timeout=5)

    def test_plain_module_entry_uses_the_original_query(self):
        stdout = StringIO()
        with patch.object(__main__, "query_ticket_categories", return_value={"ok": True}) as query:
            with redirect_stdout(stdout):
                __main__.main()
        query.assert_called_once_with(1)
        self.assertEqual(stdout.getvalue(), "{'ok': True}\n")


if __name__ == "__main__":
    unittest.main()
