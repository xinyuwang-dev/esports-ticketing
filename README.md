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

## Limitations

- Login checks a password but does not create a session or token. Order APIs
  trust the user ID sent by the client.
- Redis mode has no automatic fallback, retry job or stock reconciliation.
- Redis runs as one local container without high-availability setup.
- There are no idempotency tokens, message queues or multi-instance controls.
- This project is designed for local development and MSc evaluation, not live
  ticket sales.
