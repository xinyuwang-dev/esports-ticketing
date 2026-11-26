# Esports Ticketing

This is an MSc project for browsing esports events and buying tickets. It uses
Spring Boot 4.1.0, MySQL and an optional Redis stock check. A small web page is
included in `src/main/resources/static`.

## Features

- Browse events and ticket categories.
- Register users and check passwords with BCrypt.
- Buy one ticket per order and view order history.
- Adjust ticket prices from the remaining stock.
- Use a MySQL pessimistic lock to prevent overselling.
- Optionally reject excess requests early with a Redis Lua script.
- Compare the MySQL and Redis-assisted paths under concurrent load.

## Requirements

- JDK 17
- MySQL 8 and its command-line client
- Docker Desktop and Redis 8.2 for Redis mode and the full test suite

The repository includes the Maven Wrapper, so Maven does not need to be
installed separately. The commands below use Windows PowerShell.

## Setup

### 1. Create and import the database

From the repository root, open the MySQL client:

```powershell
mysql -u root -p
```

Create the database:

```sql
CREATE DATABASE esports_tickets
CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;
```

Leave the MySQL client with `exit`, then import the dump:

```powershell
cmd /c "mysql -u root -p esports_tickets < esports_tickets.sql"
```

If your MySQL username is not `root`, replace `root` in these commands.

> **Warning:** The dump contains `DROP TABLE IF EXISTS`, so use a new or
> replaceable development database. Its example passwords are placeholders,
> not BCrypt hashes, and cannot be used to log in. Register a user after the
> application starts.

### 2. Set environment variables

Set these values in the PowerShell window used to start the application:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = Read-Host "MySQL password"
$env:APP_ORDER_MODE = "redis"
```

Use `mysql` instead of `redis` for the MySQL-only order path. In IntelliJ, add
the same variables to the application's Run Configuration.

`.env.example` is a reference only. Spring Boot does not load `.env` or
`.env.example` automatically.

### 3. Start Redis

Redis listens on `localhost:16379`. Create the container the first time:

```powershell
docker run -d --name esports-redis --restart unless-stopped -p 16379:6379 redis:8.2
```

After that, start the existing container instead of creating it again:

```powershell
docker start esports-redis
docker exec esports-redis redis-cli PING
```

The check should return `PONG`. The restart policy lets Docker start the
container again after a restart unless it was stopped manually. Redis is not
needed when the application runs in `mysql` mode.

### 4. Start the application

```powershell
.\mvnw.cmd spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080).

The sample users imported from the SQL file cannot log in. Use the registration
form or `POST /api/users/register` to create a user with a BCrypt password.

## Order modes

| Mode | Order flow |
|---|---|
| `mysql` | MySQL locks the ticket category, updates its stock and creates the order. Redis is not used. |
| `redis` | A Redis Lua script checks and reduces Redis stock first. Accepted requests still use the same MySQL lock, stock update and order creation. |

MySQL stores the final stock and order data in both modes. If MySQL order
creation fails in `redis` mode, the service tries to restore the Redis stock.
Redis failures do not cause an automatic switch to `mysql` mode.

## Quick evaluation

Use the setup steps above with your own MySQL username and password. The
project does not require access to the author's database account.

A newly created `esports_tickets` database is empty until
`esports_tickets.sql` is imported. The dump adds a sample event with Standard
and VIP ticket categories. Its placeholder users cannot log in.

For a short demonstration:

1. Complete the database creation and import steps above.
2. Set your own `DB_USERNAME` and `DB_PASSWORD`, and use
   `APP_ORDER_MODE=mysql` for the basic demonstration.
3. Start the application with the command in the setup section.
4. Open [http://localhost:8080](http://localhost:8080) and register a new
   account.
5. Log in, view the event's ticket categories, buy one ticket, and select
   **Load My Orders**.

Redis does not need to be running in `mysql` mode. Use `redis` mode when
demonstrating Redis stock pre-decrement or the concurrency comparison.

## Resetting demo data

Purchases made through the web page create real MySQL orders and reduce the
available ticket stock.

To restore stock without removing users or orders, stop the application and
run this statement against the demonstration database:

```sql
UPDATE ticket_category
SET available_stock = initial_stock;
```

If the demonstration used `redis` mode, restart the application after updating
MySQL. The startup initializer will then reload the Redis stock values.

To clear accounts and orders and restore all seeded data, import
`esports_tickets.sql` again using the setup instructions above.

> **Warning:** The SQL file contains `DROP TABLE IF EXISTS` and deletes existing
> data. Re-import it only into a demonstration or test database that may be
> reset.

## Tests

The tests connect to the MySQL and Redis addresses in the project
configuration. Some tests add and remove real database rows, so use a
development or test database that can be changed.

Run the full test suite:

```powershell
.\mvnw.cmd test
```

Run only the concurrency comparison:

```powershell
.\mvnw.cmd "-Dtest=OrderConcurrencyComparisonTest" test
```

The comparison uses real MySQL and Redis. Each scenario starts with 100
tickets and sends 5000 requests through 100 worker threads. There is one
warm-up per mode followed by six measured runs with alternating order. Results
are printed to the test output and are not stored in the repository.

## Read-only ticket assistant

From the repository root, create `.venv` if needed and install the Python dependencies:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r ticket_assistant/requirements.txt
```

With the ticket service running, use the plain Python entry point to query event ID `1`.
The default service URL is `http://localhost:8080`; set `TICKET_SERVICE_BASE_URL`
only if the service uses a different address (for example, `http://localhost:18080`).

```powershell
.\.venv\Scripts\python.exe -B -m ticket_assistant
```

The plain entry point and `query_ticket_categories(event_id)` still return all
categories, including zero stock. Use `filter_ticket_categories` for an inclusive
stock and current-price filter:

```python
from ticket_assistant.ticket_query import filter_ticket_categories

result = filter_ticket_categories(1, min_stock=2, max_price=100)
print(result)
```

`event_id` and `min_stock` must be positive integers, excluding booleans.
Filtering defaults to `min_stock=1`; an omitted or `None` budget has no price
limit. A provided budget must be a finite, non-negative integer or float,
excluding booleans and strings. Invalid parameters return an error before HTTP.
Malformed backend records also return an error, including the record number and
field when applicable. The filter uses `currentPrice` from the backend and keeps
all fields of each match.

The Agent CLI prints the final answer to stdout; add `--show-tools` for tool records
and results on stderr. Prices and stock are snapshots from the time of the query.
Both providers use the same read-only tool, with tracing disabled.
The Agent responds in English by default and preserves event and category names
as returned by the backend.

```powershell
# OpenAI (default): set OPENAI_API_KEY in the local shell before running.
.\.venv\Scripts\python.exe -B -m ticket_assistant.agent_cli "Please list the ticket categories, current prices and available stock for event ID 1."

# Gemini: set GEMINI_API_KEY in the local shell before running.
.\.venv\Scripts\python.exe -B -m ticket_assistant.agent_cli --provider gemini --show-tools "Please list the ticket categories, current prices and available stock for event ID 1."

# Filtered Agent query (also sends model API requests)
.\.venv\Scripts\python.exe -B -m ticket_assistant.agent_cli --provider gemini --show-tools "Find ticket categories for event 1 with a current price at most 100 and at least 2 tickets available."

# Offline tests
.\.venv\Scripts\python.exe -B -m unittest discover -s ticket_assistant/tests -v
```

The Gemini option uses `gemini-3.1-flash-lite` through Google's OpenAI-compatible
Chat Completions endpoint. For a free-tier run, use a key from a confirmed free-tier
project; the CLI cannot verify the project's billing status. Never put API keys in
source files or command examples. Set `$env:TICKET_SERVICE_BASE_URL = 'http://localhost:18080'`
in PowerShell if the ticket backend is listening on port `18080`.

The offline tests use a simulated model and ticket backend, with tracing disabled.
They do not require a running service or an API key.

The single Agent tool accepts `event_id`, nullable `min_stock` and nullable
`max_price`. With both filters omitted or null it queries all categories; with a
stock minimum or budget it filters, defaulting the minimum to 1 for a budget-only
query. Successful tool results include `event_id`, `filters`, `source_count`,
`count` and `categories`. `source_count` is the original list size and `count` is
the returned list size. The plain Python query keeps its original `count` meaning
and response shape. `--show-tools` pairs actual arguments and results by `call_id`.

An empty source list cannot distinguish a missing event from an event without
categories. A non-empty source with no matches is a successful filtered query.
Failures return `ok=False`; SDK parameter rejection produces its own tool error
before the query function runs. A query reserves no stock. The purchase flow
rechecks stock and calculates the final price. The DTO has no currency field;
the assistant uses its numeric price units without adding a currency symbol.

See [the filtering learning guide](ticket_assistant/FILTERING_GUIDE.md) for the
call chain, a fixed offline example and a small exercise.

## Submission documentation

- [`CodeList.txt`](CodeList.txt) records the significant student-written and
  AI-assisted project files.
- [`SoftwarePrereqs.txt`](SoftwarePrereqs.txt) provides the software
  prerequisites and concise setup and run instructions.

## Limitations

- Login checks a password but does not create a session or token. Order APIs
  trust the user ID sent by the client.
- Redis mode has no automatic fallback, retry job or stock reconciliation.
- Redis runs as one local container without high-availability setup.
- There are no idempotency tokens, message queues or multi-instance controls.
- This project is designed for local development and MSc evaluation, not live
  ticket sales.
