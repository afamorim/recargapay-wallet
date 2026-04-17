# Wallet Management Service

## Motivation

The Wallet Management Service was developed as a Coding Test for RecargaPay.

## What the project does

This service offers the following functionalities:
- Create digital wallets for users.
- Retrieve current balance.
- Retrieve historical balance at a specific point in time.
- Wallet transactions: Deposit, Withdraw and Transfer.
- Paginated transaction history.

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
