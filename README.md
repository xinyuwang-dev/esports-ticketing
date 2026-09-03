# Esports Ticketing Prototype

This repository contains an MSc engineering prototype for browsing esports
events, registering users and purchasing tickets. It compares a MySQL-only
order path with an optional Redis-assisted path under concurrent load. MySQL
remains the authoritative source for orders and ticket inventory in both
modes.

## Features

- Browse events and their ticket categories in the web interface.
- Register users and verify login passwords with BCrypt.
- Create one-ticket orders and view a user's order history.
- Calculate ticket prices from the category's current inventory level.
- Protect MySQL inventory updates with a pessimistic write lock.
- Optionally pre-filter purchase attempts with an atomic Redis Lua stock
  decrement before the MySQL transaction.
- Restore the Redis pre-decrement when MySQL order creation fails.
- Run repeatable MySQL-versus-Redis concurrency experiments against real
  infrastructure.

## Technology stack

- Java 17
- Spring Boot 4.1.0
- Spring Web MVC, Spring Data JPA, Spring Data Redis and Bean Validation
- MySQL 8
- Redis 8.2, or a compatible Redis version
- Maven Wrapper
- HTML, CSS and JavaScript

## Requirements

Install the following before running the project:

- JDK 17
- MySQL 8, including the `mysql` command-line client for the examples below
- Redis 8.2 or a compatible version when using Redis mode or running the full
  test suite
- Docker Desktop if Redis will be run with the Docker commands below

The repository includes `mvnw.cmd`, so Maven does not need to be installed
separately. The commands below use Windows PowerShell and assume the current
directory is the repository root.

Check the Java and Maven Wrapper environment with:

```powershell
java -version
.\mvnw.cmd -v
```

Both commands should report Java 17.

## Database setup

The application connects to the local database `esports_tickets` on MySQL's
default port 3306. Set the database credentials for the current PowerShell
process before starting the application:

```powershell
$env:DB_USERNAME = "replace_with_mysql_username"
$env:DB_PASSWORD = "replace_with_mysql_password"
```

Create the database with the character set and collation used by the supplied
dump:

```powershell
mysql -u $env:DB_USERNAME -p -e "CREATE DATABASE esports_tickets CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

Import the dump from the repository root:

```powershell
cmd /c "mysql -u %DB_USERNAME% -p esports_tickets < esports_tickets.sql"
```

The client prompts for the MySQL password, so the password is not included in
the command history.

> **Warning:** `esports_tickets.sql` contains `DROP TABLE IF EXISTS` statements.
> Import it only into a new development database or a database whose contents
> may be overwritten. The dump uses MySQL 8's `utf8mb4_0900_ai_ci` collation.

The sample users in the SQL file contain `hashed_pwd_*` placeholder values.
They are not BCrypt password hashes and cannot be used to log in. Start the
application and create a login-capable user through the registration form or
`POST /api/users/register`. The request examples in `demo.http` show the
registration flow.

## Environment variables

The application uses these variables:

| Variable | Purpose | Example |
|---|---|---|
| `DB_USERNAME` | MySQL username | `replace_with_mysql_username` |
| `DB_PASSWORD` | MySQL password | `replace_with_mysql_password` |
| `APP_ORDER_MODE` | Selects `mysql` or `redis` order placement | `redis` |

Set all three for the current PowerShell process with:

```powershell
$env:DB_USERNAME = "replace_with_mysql_username"
$env:DB_PASSWORD = "replace_with_mysql_password"
$env:APP_ORDER_MODE = "redis"
```

For IntelliJ IDEA, open **Run > Edit Configurations**, select the Spring Boot
configuration and add the same names under **Environment variables**.

`.env.example` is only a list of required variables. Standard Spring Boot does
not automatically load `.env` or `.env.example`. Set the variables in the
PowerShell process, the operating system environment or the IntelliJ Run
Configuration before starting the application.

If `APP_ORDER_MODE` is not set, the committed configuration currently defaults
to `redis`.

## Order placement modes

### MySQL mode

```powershell
$env:APP_ORDER_MODE = "mysql"
```

`POST /api/orders` calls `OrderService` through `OrderPlacementService`.
`OrderService` locks the selected ticket category with a pessimistic write
lock, checks and decrements MySQL inventory, calculates the price and saves the
order in one transaction. Redis stock operations and the Redis inventory
initializer are bypassed. A Redis server is not required to start or purchase
in this mode.

### Redis mode

```powershell
$env:APP_ORDER_MODE = "redis"
```

At startup, `RedisInventoryInitializer` loads the current MySQL stock values
into Redis. For an order request, `RedisOrderService` first executes a Lua
script that atomically checks and decrements the Redis stock key. Requests that
pass this pre-filter continue to `OrderService`, where MySQL pessimistic
locking remains the final correctness protection. If MySQL order creation
throws a runtime exception, the service attempts to restore the Redis stock.

Redis is an optional high-concurrency pre-filter, not the authoritative
inventory database. Redis-mode startup and purchases may fail when Redis is
unavailable; the prototype does not switch modes automatically.

## Running Redis with Docker

The committed Redis address is `localhost:16379`. The container exposes Redis
port 6379 through host port 16379.

Create the container the first time:

```powershell
docker run -d --name esports-redis --restart unless-stopped -p 16379:6379 redis:8.2
```

Start an existing container and check it:

```powershell
docker start esports-redis
docker exec esports-redis redis-cli PING
```

The health check should return `PONG`. The `--restart unless-stopped` option is
set when the container is created and lets Docker restart it automatically.

## Running the application

After MySQL is running, the schema has been imported and the environment
variables have been set, start the application with:

```powershell
.\mvnw.cmd spring-boot:run
```

In Redis mode, start Redis before the application. Open
[http://localhost:8080](http://localhost:8080) to use the web interface.

## Tests

The tests use the real MySQL database configured by `application.properties`.
Several integration and concurrency tests create, update and delete database
rows. Run them only against a disposable development or test database, never
against production or valuable data. An interrupted test can require manual
cleanup.

The complete test suite requires both MySQL and Redis at the configured
addresses:

```powershell
.\mvnw.cmd test
```

MySQL mode for the running application does not require Redis, but the full
test suite still contains Redis integration tests.

## Concurrency comparison

`OrderConcurrencyComparisonTest` compares the two service paths against real
MySQL and Redis. Each scenario starts with stock 100 and submits 5000 purchase
attempts through 100 worker threads. It performs one unreported warm-up for
each mode, followed by six measured rounds. The execution order alternates so
that each mode runs first three times.

Run only this experiment with:

```powershell
.\mvnw.cmd -Dtest=OrderConcurrencyComparisonTest test
```

The test logs successes, sold-out rejections, unexpected failures, final
stocks, order count, duration and throughput in CSV-compatible lines. It uses
temporary database rows and Redis keys and attempts to remove them after each
scenario. Run it only against a disposable database, and save dissertation
results or screenshots outside the repository unless they are intentionally
reviewed for submission.

## Prototype limitations

This is a single-instance MSc prototype, not a production ticketing platform.
In particular:

- Login verifies credentials but does not create a server-side session or
  token. Order APIs trust the client-provided user ID and do not provide
  production-level authorisation.
- Redis mode has no automatic fallback, retry worker, reconciliation process,
  reservation TTL or high-availability configuration.
- The project does not provide idempotency tokens, a message queue or
  distributed multi-instance coordination.
- The Docker example runs one local Redis container and is intended for
  development and repeatable experimentation.
