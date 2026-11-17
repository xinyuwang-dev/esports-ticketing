from .ticket_query import query_ticket_categories


def main() -> None:
    print(query_ticket_categories(1))


if __name__ == "__main__":
    main()
