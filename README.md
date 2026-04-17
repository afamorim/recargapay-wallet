# Wallet Management Service

> RecargaPay – Technical Assessment
> Java 25 · Spring Boot 4 · PostgreSQL 17 · Redis 7 · Docker
> Modular Monolith · Distributed Redis Lock · Redis Cache · Historical Balance O(log n)

**Presentation:** [docs/Interview Solution Presentation.pdf](docs/Interview%20Solution%20Presentation.pdf)

## Motivation

The Wallet Management Service was developed as a Coding Test for RecargaPay.

## What the project does

Mission-critical REST API for digital wallets exposing:
- Create digital wallets for users.
- Retrieve current balance.
- Retrieve historical balance at a specific point in time (O(log n)).
- Wallet transactions: Deposit, Withdraw and Transfer (atomic, anti-deadlock).
- Paginated transaction history.

## Architecture — Modular Monolith

Three explicit modules with a clear boundary, ready to be extracted to microservices:

| Module | Components |
|---|---|
| **Wallet** | `WalletController`, `WalletService`, `WalletRepository`, `Wallet` entity, Request/Response DTOs |
| **Transaction** | `TransactionController`, `TransactionService`, `WalletComponent`*, `TransactionRepository`, `Transaction` entity |
| **Shared** | `DistributedLockService`, `CacheConfig`, `GlobalExceptionHandler`, Enums / Exceptions |

> `WalletComponent` is an integration proxy. Replaced by Feign/RestClient when extracting to microservices — no changes to `TransactionService`.

## Data Model — PostgreSQL

**`tb_wallet`**
- `id` UUID (PK)
- `user_id` UUID UNIQUE
- `balance` DECIMAL(19,4)
- `currency` VARCHAR(3) — `'BRL'`
- `created_at` / `updated_at` TIMESTAMPTZ
- CHECK (`balance >= 0`)

**`tb_transaction`**
- `id` UUID (PK)
- `wallet_id` UUID (FK)
- `type` — `DEPOSIT | WITHDRAWAL | TRANSFER_IN | TRANSFER_OUT`
- `amount` DECIMAL(19,4) — CHECK (`amount > 0`)
- `balance_after` DECIMAL(19,4) — CHECK (`balance_after >= 0`)
- `reference_id` UUID (nullable) — bidirectional link between the two records of a transfer
- `description` VARCHAR(255)
- `created_at` TIMESTAMPTZ

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/wallets` | Create new wallet |
| `GET` | `/api/v1/wallets/{walletId}` | Return wallet details |
| `GET` | `/api/v1/wallets/{walletId}/balance` | Current balance |
| `GET` | `/api/v1/wallets/{walletId}/balance/history?at=` | Balance at a point in time |
| `POST` | `/api/v1/wallets/{walletId}/deposit` | Deposit funds |
| `POST` | `/api/v1/wallets/{walletId}/withdraw` | Withdraw funds |
| `POST` | `/api/v1/wallets/transfer` | Transfer (atomic, anti-deadlock) |
| `GET` | `/api/v1/wallets/{walletId}/transactions` | Paginated transaction history |
| `GET` | `/actuator/health` | Health check |

## Key Design Decisions

| ID | Decision | Rationale |
|---|---|---|
| DA-01 | Modular monolith | `WalletComponent` isolates the boundary — extractable to microservices without changing `TransactionService` |
| DA-02 | Redis lock vs. DB lock | `SET NX PX` works in any topology; `SELECT FOR UPDATE` breaks in microservices |
| DA-03 | Release lock after TX commit | `TransactionSynchronization.afterCompletion` eliminates the read-after-write inconsistency window |
| DA-04 | `balance_after` denormalized | O(log n) history lookup via composite index vs O(n) replay. Trade-off: extra storage per transaction record |
| DA-05 | DB CHECK constraints | Second line of defence against bugs and direct DB access |
| Scope | No auth / rate limit | OAuth2/JWT and sliding-window rate limiting are production steps — out of scope for this assessment |

### Distributed Lock (DA-02 · DA-03)

1. `SET wallet:lock:{id} {uuid} NX PX 10000` — atomic, only sets if key does not exist; 10s TTL protects against crash
2. If locked → retry every 50ms for up to 5s; timeout raises `LockAcquisitionException` → HTTP 409
3. Execute operation inside the lock
4. Release via atomic Lua script: `if get(key)==uuid then del(key) end` — only the lock owner can release it

**Deadlock prevention:** concurrent A→B and B→A transfers always acquire locks in ascending UUID order.
```java
sortedIds = [sourceId, destId].stream().sorted().toList()
```

### Historical Balance (DA-04)

```sql
SELECT balance_after FROM tb_transaction
WHERE wallet_id = :id
  AND created_at <= :ts
ORDER BY created_at DESC
LIMIT 1
```
Composite index `(wallet_id, created_at)` guarantees O(log n). Returns zero balance if no transaction exists before the requested timestamp.

### Redis Cache — Wallet Entity

| Setting | Value |
|---|---|
| Cache name | `wallets` |
| Key | `wallets::{walletId}` |
| TTL | 10 minutes |
| Serialization | JSON + `@class` (polymorphic) |
| Null values | disabled |
| `@Cacheable` | `findById` (read) |
| `@CachePut` | after any write |
| `@CacheEvict` | after transaction operation |

## Error Handling — RFC 7807

All error responses follow RFC 7807 (`ProblemDetail`) — `type`, `title`, `status`, `detail`, `instance`.

| Status | Exception | Reason |
|---|---|---|
| 404 | `WalletNotFoundException` | Wallet not found |
| 409 | `WalletAlreadyExistsException` | User already has a wallet |
| 409 | `LockAcquisitionException` | System under high load — lock not acquired |
| 422 | `InsufficientFundsException` | Insufficient funds for withdrawal/transfer |
| 400 | `InvalidRequestException` | Business rule violation (e.g. transfer to self) |
| 400 | `MethodArgumentNotValidException` | Bean Validation — per-field error map |
| 500 | `Exception` (fallback) | Unexpected error |

## Testing Strategy

| Layer | Coverage | Tools |
|---|---|---|
| Unit | ~60% | Service logic · Edge cases · Mockito |
| Integration | ~30% | Testcontainers · Real PostgreSQL · Real Redis · TX behavior |
| E2E / API | ~10% | Full Spring Boot Test · MockMvc · OpenAPI contract |

Key scenarios: wallet creation · duplicate · insufficient funds · transfer to self · deadlock prevention · historical balance (past timestamp) · paginated history.

## How to Build

To build the project, follow these steps:

1. Ensure you have **Java 25** and **Maven** installed.
2. Clone the repository:
   ```bash
   git clone <REPOSITORY_URL>
   cd <PROJECT_DIRECTORY>
   ```
3. Build the project:
   ```bash
   ./mvnw clean package
   ```

## How to Run

### Prerequisites

Before running the application, start the required infrastructure (PostgreSQL + Redis):

```bash
docker-compose up -d
```

### Quick Start

```bash
docker-compose up -d
mvn spring-boot:run
# Swagger UI → http://localhost:8080/swagger-ui.html
# Redoc      → http://localhost:8080/redoc.html
```

### Run Locally (IDE)

1. Start the infrastructure with `docker-compose up -d`.
2. In your IDE, run the `WalletApplication` class.
3. The application will start with the `local` profile.
4. The service will be available at `http://localhost:8080`.

### Run Locally (CLI)

1. After building the project:
   ```bash
   java -jar target/wallet-0.0.1-SNAPSHOT.jar
   ```
2. The service will be available at `http://localhost:8080`.

### Environment Profiles

| Profile | Database | Redis | Hikari pool | Log |
|---|---|---|---|---|
| `local` | localhost:5432 | localhost:6379 | default | DEBUG |
| `dev` | env vars | env vars | default | DEBUG |
| `stage` | env vars | env vars | 5–10 | INFO |
| `production` | env vars | env vars | 10–20 | WARN |

### Environment Variables

The following environment variables are used in non-local profiles (`dev`, `stage`, `production`):

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Active profile (`local`, `dev`, `stage`, `production`) |
| `DB_HOST` | PostgreSQL host |
| `DB_PORT` | PostgreSQL port (default: `5432`) |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port (default: `6379`) |
| `REDIS_PASSWORD` | Redis password |
| `SERVER_PORT` | HTTP server port (default: `8080`) |

## Useful Endpoints

- **Swagger UI**: Interactive API documentation
  - [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

- **Redoc**: Alternative API documentation
  - [http://localhost:8080/redoc.html](http://localhost:8080/redoc.html)

- **Actuator Health**:
  - [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## How to Test

Run the integration tests (requires Docker — Testcontainers provisions PostgreSQL and Redis automatically):

```bash
./mvnw test
```

## What Technologies Were Used

| Technology | Version | Role |
|---|---|---|
| Java | 25 | Main programming language |
| Spring Boot | 4.0.5 | Application framework |
| Spring Web MVC | (BOM) | REST API |
| Spring Data JPA | (BOM) | Data access |
| Spring Data Redis | (BOM) | Cache and distributed locks |
| PostgreSQL | 17 | Relational database |
| Flyway | (BOM) | Database migrations |
| Redis | 7 | Cache and distributed locking |
| Lombok | (BOM) | Boilerplate reduction |
| SpringDoc OpenAPI | 3.0.2 | Swagger / Redoc documentation |
| Testcontainers | (BOM) | Real infrastructure in integration tests |
| JUnit 5 | (BOM) | Testing framework |