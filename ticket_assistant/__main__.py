from .ticket_query import fetch_categories


def main() -> None:
    url = "http://localhost:8080/api/events/1/ticket-categories"
    print(fetch_categories(url))


if __name__ == "__main__":
    main()
