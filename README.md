# Smart Digital Banking Platform

A full-stack, cloud-native banking application built with a **Spring Boot microservices backend**, a **React frontend**, and fully **Dockerised** for one-command deployment. The system handles user authentication, bank account management, money transfers, real-time fraud detection, and email notifications — all wired together through Apache Kafka and Redis.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Services](#services)
- [Kafka Event Flow](#kafka-event-flow)
- [Redis Usage](#redis-usage)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Run with Docker](#run-with-docker)
  - [Run Locally (without Docker)](#run-locally-without-docker)
- [Configuration](#configuration)
- [Fraud Detection Rules](#fraud-detection-rules)
- [Environment Variables](#environment-variables)

---

## Architecture Overview

```
                        ┌──────────────────────┐
                        │   Config Server :8888 │
                        └──────────┬───────────┘
                                   │ serves config to all services
                        ┌──────────▼───────────┐
                        │  Service Registry     │  Eureka :8761
                        └──────────┬───────────┘
                                   │
   React Frontend                  │
       │  HTTPS                    │
       ▼                           │
┌─────────────────────────────────────────────────────────┐
│                  API Gateway  :8080                     │
│   CorrelationIdFilter → AuthenticationFilter →          │
│   RedisRateLimiter → Route (lb:// via Eureka)           │
└────┬──────────┬──────────┬──────────┬──────────┬───────┘
     │          │          │          │          │
     ▼          ▼          ▼          ▼          ▼
  User Svc  Account Svc  Txn Svc  Fraud Svc  Notif Svc
  :8081      :8082       :8083     :8084      :8085
     │          │          │          │          │
   MySQL      MySQL      MySQL      MySQL      MySQL
  user_db   account_db  txn_db   fraud_db   notif_db

              ┌─────────────────────────────────────┐
              │         Apache Kafka                 │
              │   12 topics · 3 partitions each      │
              └─────────────────────────────────────┘

  Redis — rate limiting · JWT blacklist · idempotency · velocity counters · status cache
```

All business services communicate **exclusively through Kafka** — there are no direct HTTP calls between them. The money transfer flow uses a **choreography-based Saga pattern** across four services and six Kafka topics.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React, Axios, React Router |
| API Gateway | Spring Cloud Gateway (WebFlux / reactive) |
| Backend services | Spring Boot 3.x, Spring Data JPA |
| Service discovery | Netflix Eureka (Spring Cloud Netflix) |
| Configuration | Spring Cloud Config Server |
| Messaging | Apache Kafka 3.x |
| Caching / state | Redis |
| Database | MySQL 8 (one schema per service) |
| Security | Spring Security + JJWT (JWT access & refresh tokens) |
| Email | JavaMailSender (SMTP / Gmail) |
| Containerisation | Docker + Docker Compose |
| Build | Maven (multi-module with shared `common` library) |

---

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| Config Server | 8888 | — | Serves externalised config to every service |
| Service Registry | 8761 | — | Eureka — service discovery and registration |
| API Gateway | 8080 | Redis | JWT validation, rate limiting, routing, CORS, correlation IDs |
| User Service | 8081 | `user_db` + Redis | Registration, JWT auth, login, logout, profile |
| Account Service | 8082 | `account_db` | Account creation, balance management, ownership enforcement |
| Transaction Service | 8083 | `transaction_db` + Redis | Transfer initiation, idempotency, Saga orchestrator |
| Fraud Detection | 8084 | `fraud_db` + Redis | Risk scoring, velocity tracking, fraud alert publishing |
| Notification Service | 8085 | `notification_db` | Email delivery triggered by Kafka events |
| Common Library | — | — | Shared DTOs, event classes, exceptions, utilities |

### User Service `:8081`

Handles all authentication. Issues JWT access tokens (1h) and refresh tokens (7d). On logout, the token is blacklisted in Redis with a TTL equal to its remaining lifetime — the API Gateway rejects blacklisted tokens immediately even if the signature is still valid.

### Account Service `:8082`

Creates and manages bank accounts. Account numbers are generated using a database-locked sequence with the prefix `SBP` (e.g. `SBP0000000001`). Every read endpoint enforces ownership — a user can only access their own accounts, and receives `403 Forbidden` on a mismatch.

### Transaction Service `:8083`

Initiates money transfers and orchestrates the full transfer Saga. Accepts an `X-Idempotency-Key` header to safely handle retried requests — duplicate transfers within 24 hours return the cached response without any side effects. Transfer status can be polled via a lightweight endpoint backed by Redis (60-minute TTL) to avoid repeated database queries.

### Fraud Detection Service `:8084`

Scores every transaction before any money moves. Uses three signals: transaction velocity in the last 60 seconds, same-destination frequency in the last hour, and the transaction amount. Scoring thresholds are fully configurable in `fraud-detection-service.properties` without any code changes.

| Score | Decision |
|---|---|
| 0 – 39 | APPROVED |
| 40 – 69 | APPROVED + flagged for manual review + fraud alert email |
| 70 – 100 | BLOCKED — saga terminates, no money moves |

### Notification Service `:8085`

Pure Kafka consumer — no producers, no external API endpoints. Listens to three topics and sends templated emails for welcome, transaction outcome, and fraud alert scenarios. Uses Gmail SMTP in the default configuration.

### API Gateway `:8080`

Single entry point for all client requests. The filter chain runs in this order on every request:

1. **CorrelationIdFilter** — generates `X-Correlation-ID` if absent, propagates to all downstream services
2. **AuthenticationFilter** — validates JWT, injects `X-User-Uuid` and `X-User-Role` headers into the forwarded request
3. **RedisRateLimiter** — 10 req/s per user (keyed by UUID for authenticated requests, by IP for public routes), burst capacity of 20
4. **Route** — `lb://service-name` resolved via Eureka

Public routes (`/api/auth/register`, `/api/auth/login`) skip the AuthenticationFilter. Downstream services trust the injected headers and never re-validate the JWT.

---

## Kafka Event Flow

### Money Transfer Saga (full sequence)

```
1. POST /api/transactions/transfer
   └─ Transaction Service: idempotency check → save INITIATED → publish transaction.initiated

2. Fraud Detection: consumes transaction.initiated
   └─ Redis velocity check → risk score → publish fraud.result
   └─ (if score ≥ 40) also publish fraud.alert → Notification Service sends security email

3. Transaction Service: consumes fraud.result
   ├─ BLOCKED → status = FAILED → publish transaction.completed (FAILED)
   └─ APPROVED → status = PROCESSING → publish debit.command

4. Account Service: consumes debit.command
   └─ debit source balance → publish account.debited

5. Transaction Service: consumes account.debited
   └─ publish credit.command

6. Account Service: consumes credit.command
   └─ credit destination balance → publish account.credited

7. Transaction Service: consumes account.credited
   └─ status = COMPLETED → update Redis cache → publish transaction.completed (SUCCESS)

8. Notification Service: consumes transaction.completed
   └─ sends success or failure email

── Compensation (if step 6 fails) ─────────────────────────────────
   Account Service → publish credit.failed
   Transaction Service → publish reverse.debit.command
   Account Service → refund source → publish debit.reversed
   Transaction Service → status = FAILED → publish transaction.completed (FAILED)
```

### All 12 Topics

| Topic | Producer | Consumer |
|---|---|---|
| `user-service.user.registered` | User Service | Notification Service |
| `account-service.account.created` | Account Service | Notification Service |
| `transaction-service.transaction.initiated` | Transaction Service | Fraud Detection |
| `fraud-detection-service.fraud.result` | Fraud Detection | Transaction Service |
| `fraud-detection-service.fraud.alert` | Fraud Detection | Notification Service |
| `transaction-service.debit.command` | Transaction Service | Account Service |
| `transaction-service.credit.command` | Transaction Service | Account Service |
| `transaction-service.reverse.debit.command` | Transaction Service | Account Service |
| `account-service.account.debited` | Account Service | Transaction Service |
| `account-service.account.credited` | Account Service | Transaction Service |
| `account-service.debit.reversed` | Account Service | Transaction Service |
| `transaction-service.transaction.completed` | Transaction Service | Notification Service |

All topics use 3 partitions. Consumers use `earliest` auto-offset-reset so no event is missed on restart.

---

## Redis Usage

| Service | Purpose | Key pattern | TTL |
|---|---|---|---|
| API Gateway | Rate limiting | `rate:limit:{uuid}` or `rate:limit:anonymous:{ip}` | Window duration |
| User Service | JWT blacklist | `jwt:blacklist:{token}` | Remaining token lifetime |
| Transaction Service | Idempotency cache | `idempotency:{key}` | 24 hours |
| Transaction Service | Status cache | `txn:status:{ref}` | 60 minutes |
| Fraud Detection | Velocity counters (per-minute) | `velocity:user:{uuid}:60s` | 60 seconds |
| Fraud Detection | Velocity counters (same destination) | `velocity:user:{uuid}:dest:{account}:3600s` | 1 hour |

The velocity counters use Redis `INCR` + `EXPIRE` — the key is created on first event and expires automatically at the end of the window.

---

## API Reference

All protected endpoints require `Authorization: Bearer <access_token>`.

### Auth  (`/api/auth/**`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register user, returns JWT tokens |
| POST | `/api/auth/login` | Public | Authenticate, returns JWT tokens |
| POST | `/api/auth/logout` | Protected | Blacklist current JWT in Redis |
| GET | `/api/auth/users/profile` | Protected | Get own profile |

### Accounts  (`/api/accounts/**`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/accounts/create` | Protected | Create SAVINGS or CURRENT account |
| GET | `/api/accounts/{accountNumber}` | Protected | Get account (ownership enforced) |
| GET | `/api/accounts/{accountNumber}/balance` | Protected | Lightweight balance check |
| GET | `/api/accounts/user/{userUuid}` | Protected | List all accounts for a user |

### Transactions  (`/api/transactions/**`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/transactions/transfer` | Protected + `X-Idempotency-Key` | Initiate transfer — returns `202 Accepted` |
| GET | `/api/transactions/{ref}` | Protected | Full transaction detail |
| GET | `/api/transactions/status/{ref}` | Protected | Lightweight status poll (Redis-backed) |
| GET | `/api/transactions/history/{accountNumber}` | Protected | Transaction history for an account |

### Fraud  (`/api/fraud/**`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/fraud/evaluations` | Protected | List fraud evaluations |

### Notifications  (`/api/notifications/**`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/notifications` | Protected | List notifications for the authenticated user |

---

## Project Structure

```
smart-banking-platform/
│
├── frontend/                        # React application
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/                # Axios API clients
│   │   └── App.jsx
│   ├── Dockerfile
│   └── package.json
│
├── common/                          # Shared Maven library
│   └── src/main/java/com/smartbanking/common/
│       ├── event/                   # All 13 Kafka event classes
│       ├── dto/                     # ApiResponse<T>, PageResponse<T>
│       └── exception/               # BusinessException, ErrorCode
│
├── config-server/                   # Spring Cloud Config Server :8888
│   └── src/main/resources/
│       └── configurations/          # Per-service .properties files
│           ├── user-service.properties
│           ├── user-service-docker.properties
│           └── ...
│
├── service-registry/                # Eureka Server :8761
│
├── api-gateway/                     # Spring Cloud Gateway :8080
│
├── user-service/                    # Auth service :8081
│
├── account-service/                 # Account management :8082
│
├── transaction-service/             # Transfer + Saga :8083
│
├── fraud-detection-service/         # Risk scoring :8084
│
├── notification-service/            # Email delivery :8085
│
└── docker-compose.yml
```

---

## Getting Started

### Prerequisites

- Docker Desktop (for Docker setup)
- Or: Java 17, Maven 3.9+, MySQL 8, Kafka, Redis, Node.js 18+ (for local setup)

### Run with Docker

```bash
# Clone the repository
git clone https://github.com/Avishkar-02/smart-banking-platform.git
cd smart-banking-platform

# Start everything — infrastructure + all services + frontend
docker compose up --build

# Or in detached mode
docker compose up --build -d
```

Docker Compose starts the following containers in dependency order:

| Container | Port | Notes |
|---|---|---|
| `mysql` | 3306 | Creates all 5 databases automatically |
| `kafka` | 9092 | KRaft mode — no Zookeeper required |
| `redis` | 6379 | Single instance |
| `config-server` | 8888 | Starts first; others wait for it |
| `service-registry` | 8761 | Eureka dashboard available |
| `api-gateway` | 8080 | Waits for registry |
| `user-service` | 8081 | |
| `account-service` | 8082 | |
| `transaction-service` | 8083 | |
| `fraud-detection-service` | 8084 | |
| `notification-service` | 8085 | |
| `frontend` | 3000 | React app served via Nginx |

Once running, open **http://localhost:3000** for the React frontend.

The Eureka dashboard is available at **http://localhost:8761**.

To stop everything:

```bash
docker compose down

# To also remove volumes (wipes all databases):
docker compose down -v
```

### Run Locally (without Docker)

You need MySQL, Kafka, and Redis running locally before starting any service.

**1. Start infrastructure**

```bash
# Start Kafka (KRaft mode or with Zookeeper — adjust to your setup)
kafka-server-start.sh config/server.properties

# Start Redis
redis-server

# MySQL should be running on port 3306 with user root / password root
```

**2. Build the common library**

```bash
cd common
mvn clean install -DskipTests
```

**3. Start services in order**

```bash
# Config Server — must start first
cd config-server && mvn spring-boot:run

# Service Registry — must start second
cd service-registry && mvn spring-boot:run

# Business services — can start in any order after the above
cd user-service          && mvn spring-boot:run
cd account-service       && mvn spring-boot:run
cd transaction-service   && mvn spring-boot:run
cd fraud-detection-service && mvn spring-boot:run
cd notification-service  && mvn spring-boot:run

# API Gateway — start last
cd api-gateway && mvn spring-boot:run
```

**4. Start the frontend**

```bash
cd frontend
npm install
npm run dev       # development server on http://localhost:5173
```

All services register with Eureka automatically. Wait about 30 seconds after the last service starts for discovery to propagate before making requests.

---

## Configuration

All backend configuration is externalised to the Config Server. Each service has two property files inside `config-server/src/main/resources/configurations/`:

- `{service-name}.properties` — local development (connects to `localhost`)
- `{service-name}-docker.properties` — Docker deployment (uses container hostnames)

The Docker profile overrides only the values that differ between environments (datasource URL, Kafka bootstrap server, Redis host, Eureka URL). Everything else is inherited from the base file.

To change a configuration value without restarting everything, update the relevant `.properties` file and restart only the affected service.

**Key shared values** (set in `applications.properties` — applies to all services):

```properties
# Logging with correlation ID
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n
logging.level.com.smartbanking=DEBUG
```

---

## Fraud Detection Rules

All thresholds are configurable in `fraud-detection-service.properties` — no code change or rebuild required:

```properties
# Maximum amount before high-risk flag
fraud.rules.max-amount-threshold=100000

# Velocity limits
fraud.rules.max-transactions-per-minute=5
fraud.rules.max-same-destination-per-hour=3

# Score thresholds
fraud.rules.high-risk-score-threshold=70     # BLOCKED
fraud.rules.medium-risk-score-threshold=40   # APPROVED + manual review required

# Unusual hours (transactions in this window score higher)
fraud.rules.unusual-hour-start=1
fraud.rules.unusual-hour-end=5
```

---

## Environment Variables

For production deployments, override these values via environment variables or Docker Compose `environment` blocks:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_USERNAME` | `root` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | MySQL password |
| `JWT_SECRET` | See config | Must be identical in `user-service` and `api-gateway` |
| `SPRING_MAIL_USERNAME` | Gmail address | SMTP sender address |
| `SPRING_MAIL_PASSWORD` | App password | Gmail app password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_PROFILES_ACTIVE` | — | Set to `docker` inside containers |

> **Note:** The JWT secret must be the same value in both `user-service` and `api-gateway`. If they differ, the Gateway cannot validate tokens that the User Service issued and every request will return `401`.

---

## Key Design Decisions

**Why Kafka instead of REST between services?**
Services don't call each other synchronously. Kafka decouples producers from consumers — if Notification Service goes down, events queue up and are replayed on restart. No request fails because of a downstream service being temporarily unavailable.

**Why a choreography-based Saga instead of Orchestration?**
Transaction Service acts as the saga coordinator by publishing command events and reacting to result events. There is no central orchestrator process — each service publishes the next step. This keeps services independently deployable and removes a single point of failure.

**Why per-service databases?**
Each service owns its own MySQL schema. No cross-service joins, no shared connection pools. This means each database can be scaled, backed up, and migrated independently.

**Why Redis for idempotency instead of a database flag?**
Redis lookups are sub-millisecond, and idempotency keys only need to live for 24 hours. Using a database for this would add a synchronous DB write on every transfer request before any business logic runs.

**Why is the JWT validated at the Gateway and not in each service?**
Centralising validation means each business service can stay stateless. The Gateway injects `X-User-Uuid` and `X-User-Role` into every forwarded request — downstream services trust these headers. If the Gateway is compromised, the architecture needs revision anyway.
