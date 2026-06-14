# Product Requirements Document (PRD)
# Event Ledger — Distributed Financial Transaction System

**Version:** 1.0.0
**Status:** Final
**Last Updated:** 2026-06-14
**Author:** Engineering Team
**Target Stack:** Java 21 · Spring Boot 3.5.x · MongoDB · Docker Compose

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Technology Stack](#3-technology-stack)
4. [Project Structure](#4-project-structure)
5. [Data Models & MongoDB Schema](#5-data-models--mongodb-schema)
6. [Service 1 — Event Gateway API](#6-service-1--event-gateway-api)
7. [Service 2 — Account Service](#7-service-2--account-service)
8. [Inter-Service Communication](#8-inter-service-communication)
9. [Idempotency & Out-of-Order Handling](#9-idempotency--out-of-order-handling)
10. [Distributed Tracing](#10-distributed-tracing)
11. [Observability — Structured Logging & Metrics](#11-observability--structured-logging--metrics)
12. [Resiliency Patterns](#12-resiliency-patterns)
13. [Graceful Degradation](#13-graceful-degradation)
14. [Docker Compose Setup](#14-docker-compose-setup)
15. [Security Considerations](#15-security-considerations)
16. [Automated Tests](#16-automated-tests)
17. [README Requirements](#17-readme-requirements)
18. [Bonus Enhancements](#18-bonus-enhancements)
19. [Acceptance Criteria Checklist](#19-acceptance-criteria-checklist)

---

## 1. Project Overview

### 1.1 Background

Financial upstream systems dispatch transaction events to a central processing platform. These
upstream systems are not perfectly synchronized — events may arrive **out of order** and may be
**delivered more than once**. The Event Ledger system must handle both scenarios correctly and
must behave gracefully when parts of the system are unavailable.

### 1.2 Goals

- Build two independently deployable Spring Boot microservices.
- Accept, validate, deduplicate, and persist financial transaction events.
- Maintain accurate account balances regardless of event arrival order.
- Expose rich observability: structured logs, distributed traces, and custom metrics.
- Demonstrate production-grade resiliency patterns.

### 1.3 Non-Goals

- No authentication/authorization (out of scope for this exercise; see §15 for notes).
- No asynchronous messaging (Kafka/RabbitMQ) — synchronous REST only.
- No multi-currency conversion (currency is stored as-is).

### 1.4 Architecture Decision Records (ADRs)

| # | Decision | Rationale |
|---|----------|-----------|
| ADR-01 | MongoDB for both services | Schema-flexible document store; ideal for event and account state; each service gets its own database instance, satisfying the "no shared DB" constraint. |
| ADR-02 | Resilience4j Circuit Breaker | Native Spring Boot 3.x integration via `spring-cloud-circuitbreaker-resilience4j`; battle-tested in production. |
| ADR-03 | OpenTelemetry via Micrometer Tracing | Spring Boot 3.x auto-configures Micrometer Tracing; bridges to OpenTelemetry with minimal boilerplate. |
| ADR-04 | Logback + Logstash JSON encoder | De-facto standard for structured JSON logging in Spring Boot apps. |
| ADR-05 | Separate MongoDB databases per service | `event_gateway_db` and `account_service_db` on the same MongoDB instance in Docker Compose, mimicking isolated storage per service. |
| ADR-06 | Java 21 (LTS) | Latest LTS release; virtual threads (Project Loom) available via Spring Boot 3.2+ for better throughput under load. |

---

## 2. Architecture Overview

```
                          ┌──────────────────────────────────┐
  Browser / Client ──────▶│       Event Gateway API          │  Port 8080
                          │  (Spring Boot 3.5 · Java 21)     │
                          │  Database: event_gateway_db       │
                          └──────────────┬───────────────────┘
                                         │  REST (sync)
                                         │  Headers: X-Trace-Id, traceparent
                                         ▼
                          ┌──────────────────────────────────┐
                          │       Account Service            │  Port 8081
                          │  (Spring Boot 3.5 · Java 21)     │
                          │  Database: account_service_db    │
                          └──────────────────────────────────┘
                                         │
                          ┌──────────────▼───────────────────┐
                          │           MongoDB                │  Port 27017
                          │  event_gateway_db                │
                          │  account_service_db              │
                          └──────────────────────────────────┘
```

### 2.1 Request Lifecycle — POST /events

```
Client
  │
  ▼
[Event Gateway]
  1. Parse & validate request body
  2. Check idempotency (lookup eventId in MongoDB)
  3. If duplicate → return 200 with original event
  4. Persist event record (status=PENDING)
  5. Call Account Service POST /accounts/{id}/transactions
       └─ attach X-Trace-Id, traceparent headers
  6. On success → update event status=PROCESSED
  7. On Account Service failure → update status=FAILED, return 503
  8. Return 201 with event resource
```

---

## 3. Technology Stack

### 3.1 Both Services — Common

| Concern | Library / Version |
|---------|------------------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.5.x |
| Build Tool | Maven 3.9.x (with Maven Wrapper `mvnw`) |
| Database Driver | `spring-boot-starter-data-mongodb` |
| HTTP Client | `spring-boot-starter-webflux` (WebClient — non-blocking) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation 3) |
| Tracing | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` |
| Metrics | `micrometer-registry-prometheus` |
| Logging | Logback + `logstash-logback-encoder` 7.x |
| Resiliency | `spring-cloud-starter-circuitbreaker-resilience4j` |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers (MongoDB) |
| Containerization | Docker + Docker Compose v2 |

### 3.2 Spring Boot Parent POM

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
</parent>
```

### 3.3 Java 21 Virtual Threads (Loom)

Enable virtual threads in both services for better concurrency under I/O load:

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

---

## 4. Project Structure

### 4.1 Repository Layout

```
event-ledger/
├── event-gateway/                    # Microservice 1
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/eventledger/gateway/
│   │   │   │   ├── EventGatewayApplication.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── AppConfig.java
│   │   │   │   │   ├── MongoConfig.java
│   │   │   │   │   ├── ResilienceConfig.java
│   │   │   │   │   ├── TracingConfig.java
│   │   │   │   │   └── WebClientConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   ├── EventController.java
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   └── EventRequest.java
│   │   │   │   │   └── response/
│   │   │   │   │       ├── EventResponse.java
│   │   │   │   │       ├── ErrorResponse.java
│   │   │   │   │       └── HealthResponse.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── DuplicateEventException.java
│   │   │   │   │   ├── AccountServiceException.java
│   │   │   │   │   ├── EventNotFoundException.java
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── model/
│   │   │   │   │   └── Event.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── EventRepository.java
│   │   │   │   └── service/
│   │   │   │       ├── EventService.java
│   │   │   │       └── AccountServiceClient.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-docker.yml
│   │   │       └── logback-spring.xml
│   │   └── test/
│   │       └── java/com/eventledger/gateway/
│   │           ├── controller/
│   │           │   └── EventControllerTest.java
│   │           ├── service/
│   │           │   ├── EventServiceTest.java
│   │           │   └── AccountServiceClientTest.java
│   │           └── integration/
│   │               ├── EventIntegrationTest.java
│   │               └── ResiliencyIntegrationTest.java
│   ├── Dockerfile
│   └── pom.xml
│
├── account-service/                  # Microservice 2
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/eventledger/account/
│   │   │   │   ├── AccountServiceApplication.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── AppConfig.java
│   │   │   │   │   ├── MongoConfig.java
│   │   │   │   │   └── TracingConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   ├── AccountController.java
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   └── TransactionRequest.java
│   │   │   │   │   └── response/
│   │   │   │   │       ├── AccountResponse.java
│   │   │   │   │       ├── BalanceResponse.java
│   │   │   │   │       ├── TransactionResponse.java
│   │   │   │   │       ├── ErrorResponse.java
│   │   │   │   │       └── HealthResponse.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── AccountNotFoundException.java
│   │   │   │   │   ├── DuplicateTransactionException.java
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── Account.java
│   │   │   │   │   └── Transaction.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── AccountRepository.java
│   │   │   │   │   └── TransactionRepository.java
│   │   │   │   └── service/
│   │   │   │       └── AccountService.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-docker.yml
│   │   │       └── logback-spring.xml
│   │   └── test/
│   │       └── java/com/eventledger/account/
│   │           ├── controller/
│   │           │   └── AccountControllerTest.java
│   │           ├── service/
│   │           │   └── AccountServiceTest.java
│   │           └── integration/
│   │               └── AccountIntegrationTest.java
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml
├── docker-compose.override.yml       # Local dev overrides
├── .env.example
└── README.md
```

---

## 5. Data Models & MongoDB Schema

### 5.1 Event Gateway — `event_gateway_db`

#### Collection: `events`

```json
{
  "_id": "ObjectId (auto)",
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "ISODate(2026-05-15T14:02:11Z)",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  },
  "status": "PROCESSED",
  "receivedAt": "ISODate(2026-05-15T14:03:00Z)",
  "processedAt": "ISODate(2026-05-15T14:03:01Z)",
  "traceId": "abc123def456",
  "createdAt": "ISODate(2026-05-15T14:03:00Z)",
  "updatedAt": "ISODate(2026-05-15T14:03:01Z)"
}
```

**MongoDB Indexes:**
```javascript
db.events.createIndex({ "eventId": 1 }, { unique: true })       // idempotency
db.events.createIndex({ "accountId": 1, "eventTimestamp": 1 }) // list by account + sort
db.events.createIndex({ "status": 1 })                          // filter by status
db.events.createIndex({ "createdAt": 1 })                       // TTL / time queries
```

**Event Status Enum:**
```
PENDING    → received, not yet forwarded to Account Service
PROCESSED  → successfully applied to account
FAILED     → Account Service returned error or was unreachable
DUPLICATE  → already exists; original returned
```

### 5.2 Account Service — `account_service_db`

#### Collection: `accounts`

```json
{
  "_id": "ObjectId (auto)",
  "accountId": "acct-123",
  "balance": 350.00,
  "currency": "USD",
  "createdAt": "ISODate(...)",
  "updatedAt": "ISODate(...)"
}
```

**MongoDB Indexes:**
```javascript
db.accounts.createIndex({ "accountId": 1 }, { unique: true })
```

#### Collection: `transactions`

```json
{
  "_id": "ObjectId (auto)",
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "ISODate(2026-05-15T14:02:11Z)",
  "appliedAt": "ISODate(2026-05-15T14:03:01Z)",
  "traceId": "abc123def456"
}
```

**MongoDB Indexes:**
```javascript
db.transactions.createIndex({ "eventId": 1 }, { unique: true })              // idempotency
db.transactions.createIndex({ "accountId": 1, "eventTimestamp": 1 })        // sorted history
```

---

## 6. Service 1 — Event Gateway API

**Service Name:** `event-gateway`
**Port:** `8080`
**Base Package:** `com.eventledger.gateway`
**MongoDB Database:** `event_gateway_db`

### 6.1 API Endpoints

#### POST /events — Submit a Transaction Event

**Request Body:**
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

**Validation Rules:**

| Field | Rule |
|-------|------|
| `eventId` | Required, non-blank, max 100 chars |
| `accountId` | Required, non-blank, max 100 chars |
| `type` | Required, must be `CREDIT` or `DEBIT` (case-insensitive) |
| `amount` | Required, must be > 0, max 2 decimal places |
| `currency` | Required, non-blank, 3-char ISO 4217 code |
| `eventTimestamp` | Required, valid ISO 8601 UTC datetime |
| `metadata` | Optional, free-form JSON object |

**Responses:**

| HTTP Status | Condition | Body |
|-------------|-----------|------|
| `201 Created` | New event accepted and processed | Event resource |
| `200 OK` | Duplicate `eventId` received | Original event resource |
| `400 Bad Request` | Validation failure | `ErrorResponse` with field errors |
| `503 Service Unavailable` | Account Service unreachable / circuit open | `ErrorResponse` |
| `500 Internal Server Error` | Unexpected Gateway error | `ErrorResponse` |

**Success Response (201/200):**
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "status": "PROCESSED",
  "receivedAt": "2026-05-15T14:03:00Z",
  "processedAt": "2026-05-15T14:03:01Z",
  "traceId": "abc123def456",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

**Error Response:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "traceId": "abc123def456",
  "timestamp": "2026-05-15T14:03:00Z",
  "errors": [
    { "field": "amount", "message": "must be greater than 0" },
    { "field": "type", "message": "must be CREDIT or DEBIT" }
  ]
}
```

---

#### GET /events/{id} — Retrieve Event by ID

**Path Parameter:** `id` — the `eventId` string (not MongoDB `_id`)

**Responses:**

| HTTP Status | Condition |
|-------------|-----------|
| `200 OK` | Event found |
| `404 Not Found` | No event with given `eventId` |

**Note:** This endpoint reads from the Gateway's own MongoDB only — works even when Account Service is down.

---

#### GET /events?account={accountId} — List Events for Account

**Query Parameter:** `account` — required

**Response:** Array of event objects, sorted ascending by `eventTimestamp`

```json
[
  {
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "status": "PROCESSED",
    ...
  }
]
```

**Responses:**

| HTTP Status | Condition |
|-------------|-----------|
| `200 OK` | List returned (may be empty array `[]`) |
| `400 Bad Request` | Missing `account` query param |

**Note:** Returns Gateway-local data only — works even when Account Service is down.

---

#### GET /health — Gateway Health Check

**Response:**
```json
{
  "service": "event-gateway",
  "status": "UP",
  "timestamp": "2026-05-15T14:03:00Z",
  "version": "1.0.0",
  "checks": {
    "mongodb": {
      "status": "UP",
      "responseTimeMs": 3
    },
    "accountService": {
      "status": "UP",
      "circuitBreakerState": "CLOSED"
    }
  }
}
```

| HTTP Status | Condition |
|-------------|-----------|
| `200 OK` | All critical checks pass |
| `503 Service Unavailable` | MongoDB down (critical dependency) |

---

### 6.2 EventRequest DTO

```java
// com.eventledger.gateway.dto.request.EventRequest

public record EventRequest(
    @NotBlank(message = "eventId is required")
    @Size(max = 100)
    String eventId,

    @NotBlank(message = "accountId is required")
    @Size(max = 100)
    String accountId,

    @NotNull(message = "type is required")
    @Pattern(regexp = "CREDIT|DEBIT", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "type must be CREDIT or DEBIT")
    String type,

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-character ISO 4217 code")
    String currency,

    @NotNull(message = "eventTimestamp is required")
    Instant eventTimestamp,

    Map<String, Object> metadata
) {}
```

### 6.3 Event Domain Model

```java
// com.eventledger.gateway.model.Event

@Document(collection = "events")
public class Event {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String accountId;

    private String type;          // CREDIT | DEBIT
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private Map<String, Object> metadata;

    private EventStatus status;   // PENDING | PROCESSED | FAILED | DUPLICATE
    private Instant receivedAt;
    private Instant processedAt;
    private String traceId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

### 6.4 EventRepository

```java
public interface EventRepository extends MongoRepository<Event, String> {

    Optional<Event> findByEventId(String eventId);

    // Returns events sorted ascending by eventTimestamp
    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    boolean existsByEventId(String eventId);
}
```

### 6.5 EventService — Business Logic

```java
// Pseudocode — implement fully

@Service
@Transactional
public class EventService {

    public EventResponse submitEvent(EventRequest request, String traceId) {

        // Step 1: Idempotency check
        Optional<Event> existing = eventRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event received: eventId={}, traceId={}", request.eventId(), traceId);
            return toResponse(existing.get());  // Return 200 with original
        }

        // Step 2: Persist with PENDING status
        Event event = buildEvent(request, traceId);
        event.setStatus(EventStatus.PENDING);
        event = eventRepository.save(event);

        // Step 3: Call Account Service (with circuit breaker)
        try {
            accountServiceClient.applyTransaction(request, traceId);
            event.setStatus(EventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        } catch (AccountServiceException ex) {
            event.setStatus(EventStatus.FAILED);
            eventRepository.save(event);
            throw ex;  // Propagates to controller → 503
        }

        // Step 4: Save final state
        event = eventRepository.save(event);
        return toResponse(event);
    }

    public EventResponse getEvent(String eventId) {
        return eventRepository.findByEventId(eventId)
            .map(this::toResponse)
            .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public List<EventResponse> listEventsByAccount(String accountId) {
        return eventRepository
            .findByAccountIdOrderByEventTimestampAsc(accountId)
            .stream()
            .map(this::toResponse)
            .toList();
    }
}
```

---

## 7. Service 2 — Account Service

**Service Name:** `account-service`
**Port:** `8081`
**Base Package:** `com.eventledger.account`
**MongoDB Database:** `account_service_db`

> **Access restriction:** This service must only be reachable internally (via Docker network).
> Do NOT expose port 8081 externally in production. In Docker Compose, use an internal network for inter-service communication.

### 7.1 API Endpoints

#### POST /accounts/{accountId}/transactions — Apply Transaction

**Path Parameter:** `accountId`

**Request Body:**
```json
{
  "eventId": "evt-001",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z"
}
```

**Logic:**

1. Check for duplicate `eventId` in `transactions` collection → if exists, return `200` (idempotent).
2. Find or create account by `accountId`.
3. Insert transaction record.
4. Recompute balance as `SUM(CREDIT amounts) - SUM(DEBIT amounts)` from all transactions for this account.
5. Update `account.balance` atomically.
6. Return transaction response.

**Responses:**

| HTTP Status | Condition |
|-------------|-----------|
| `201 Created` | Transaction applied |
| `200 OK` | Duplicate `eventId` — already applied |
| `400 Bad Request` | Validation failure |

**Response Body:**
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "newBalance": 350.00,
  "appliedAt": "2026-05-15T14:03:01Z"
}
```

---

#### GET /accounts/{accountId}/balance — Get Account Balance

**Response:**
```json
{
  "accountId": "acct-123",
  "balance": 350.00,
  "currency": "USD",
  "transactionCount": 3,
  "lastUpdated": "2026-05-15T14:03:01Z"
}
```

| HTTP Status | Condition |
|-------------|-----------|
| `200 OK` | Account found |
| `404 Not Found` | Account does not exist |

---

#### GET /accounts/{accountId} — Get Account Details

**Response:**
```json
{
  "accountId": "acct-123",
  "balance": 350.00,
  "currency": "USD",
  "createdAt": "2026-05-15T10:00:00Z",
  "updatedAt": "2026-05-15T14:03:01Z",
  "recentTransactions": [
    {
      "eventId": "evt-001",
      "type": "CREDIT",
      "amount": 150.00,
      "eventTimestamp": "2026-05-15T14:02:11Z"
    }
  ]
}
```

**Note:** `recentTransactions` returns the last 20 transactions sorted by `eventTimestamp` ascending.

| HTTP Status | Condition |
|-------------|-----------|
| `200 OK` | Account found |
| `404 Not Found` | Account does not exist |

---

#### GET /health — Account Service Health Check

```json
{
  "service": "account-service",
  "status": "UP",
  "timestamp": "2026-05-15T14:03:00Z",
  "version": "1.0.0",
  "checks": {
    "mongodb": {
      "status": "UP",
      "responseTimeMs": 2
    }
  }
}
```

---

### 7.2 TransactionRequest DTO

```java
public record TransactionRequest(

    @NotBlank(message = "eventId is required")
    String eventId,

    @NotNull
    @Pattern(regexp = "CREDIT|DEBIT", flags = Pattern.Flag.CASE_INSENSITIVE)
    String type,

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal amount,

    @NotBlank
    @Size(min = 3, max = 3)
    String currency,

    @NotNull
    Instant eventTimestamp
) {}
```

### 7.3 Account Domain Model

```java
@Document(collection = "accounts")
public class Account {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountId;

    private BigDecimal balance = BigDecimal.ZERO;
    private String currency;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

### 7.4 Transaction Domain Model

```java
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String accountId;

    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private Instant appliedAt;
    private String traceId;

    @CreatedDate
    private Instant createdAt;
}
```

### 7.5 Balance Computation Strategy

Balance must always be **recomputed from the full transaction history** to handle out-of-order events correctly:

```java
// In AccountService
public BigDecimal computeBalance(String accountId) {
    List<Transaction> txns = transactionRepository.findByAccountId(accountId);
    BigDecimal credits = txns.stream()
        .filter(t -> "CREDIT".equalsIgnoreCase(t.getType()))
        .map(Transaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal debits = txns.stream()
        .filter(t -> "DEBIT".equalsIgnoreCase(t.getType()))
        .map(Transaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return credits.subtract(debits);
}
```

> **Important:** After each transaction insert, recompute and update `account.balance` to keep it consistent. The stored balance is a cached value; truth lives in the `transactions` collection.

---

## 8. Inter-Service Communication

### 8.1 WebClient Configuration (Event Gateway)

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient accountServiceWebClient(
            @Value("${account-service.base-url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(1 * 1024 * 1024))
            .build();
    }
}
```

### 8.2 AccountServiceClient

```java
@Service
public class AccountServiceClient {

    private final WebClient webClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public TransactionResponse applyTransaction(EventRequest request, String traceId) {
        CircuitBreaker cb = circuitBreakerFactory.create("accountService");
        return cb.run(
            () -> webClient.post()
                .uri("/accounts/{id}/transactions", request.accountId())
                .header("X-Trace-Id", traceId)
                .header("traceparent", buildTraceParent(traceId))
                .bodyValue(toTransactionRequest(request))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xx)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xx)
                .bodyToMono(TransactionResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block(),
            throwable -> { throw new AccountServiceException("Account Service unavailable", throwable); }
        );
    }
}
```

### 8.3 HTTP Headers for Trace Propagation

| Header | Description | Example |
|--------|-------------|---------|
| `X-Trace-Id` | Custom trace ID (readable) | `abc123def456` |
| `traceparent` | W3C Trace Context standard | `00-abc123...-0123...-01` |
| `X-Request-Id` | Per-request correlation ID | `req-uuid-here` |

Both services must log these headers on every incoming request via a `OncePerRequestFilter`.

---

## 9. Idempotency & Out-of-Order Handling

### 9.1 Idempotency Implementation

**Event Gateway Layer:**
- Before saving any event, query `eventRepository.existsByEventId(eventId)`.
- If found: log at INFO level, return `200 OK` with the stored event. No further processing.
- The unique index on `eventId` in MongoDB serves as the database-level guard (race condition safety).

**Account Service Layer:**
- Before applying any transaction, query `transactionRepository.existsByEventId(eventId)`.
- If found: log at INFO level, return `200 OK` with the stored transaction. Balance is NOT modified.
- Unique index on `eventId` in `transactions` provides database-level guard.

**Idempotency Flow:**
```
[Gateway receives evt-001 again]
   │
   ▼
findByEventId("evt-001") → found
   │
   ▼
Return 200 + original event   ← NO call to Account Service
```

### 9.2 Out-of-Order Event Handling

Events are stored with their original `eventTimestamp` (not `receivedAt`). Sorting is always done by `eventTimestamp`.

**Scenario:**
```
T=10:00 — evt-002 arrives (timestamp 09:00) → applied to account
T=10:01 — evt-001 arrives (timestamp 08:00) → applied to account
GET /events?account=acct-123
→ [evt-001 (08:00), evt-002 (09:00)]   ← sorted by eventTimestamp, not arrival order
```

**Balance correctness:**
- Balance = SUM(CREDIT) - SUM(DEBIT) across ALL transactions, regardless of order.
- No running-balance approach — always compute from complete history.
- This ensures correctness even with late-arriving events.

---

## 10. Distributed Tracing

### 10.1 Trace ID Strategy

- On every incoming HTTP request to the **Gateway**, generate or extract a `traceId`.
- If `traceparent` W3C header is present → extract trace ID from it.
- If `X-Trace-Id` header is present → use it.
- Otherwise → generate a new UUID-based trace ID.

### 10.2 Spring Boot Micrometer Tracing Configuration

```yaml
# application.yml (both services)
management:
  tracing:
    sampling:
      probability: 1.0   # Trace 100% of requests (lower in production)
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces  # if collector enabled
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

### 10.3 MDC (Mapped Diagnostic Context) Filter

Both services must include an MDC filter that:

1. Extracts `traceId` from incoming headers (`traceparent`, `X-Trace-Id`).
2. Falls back to Micrometer's auto-generated trace ID.
3. Places `traceId` and `spanId` into `MDC` before request processing.
4. Clears MDC after response.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String traceId = extractTraceId(request);
        MDC.put("traceId", traceId);
        MDC.put("service", "event-gateway");
        response.setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 10.4 Trace Propagation to Account Service

When the Gateway calls the Account Service:
- Set `X-Trace-Id: {traceId}` header.
- Set `traceparent: 00-{traceId}-{spanId}-01` header (W3C format).

When the Account Service receives a request:
- Extract `X-Trace-Id` from headers.
- Place in `MDC` for all log statements.
- Include in response body (`traceId` field).

---

## 11. Observability — Structured Logging & Metrics

### 11.1 Structured JSON Logging

**Dependency:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**logback-spring.xml (both services):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="appName" source="spring.application.name"/>
    <springProperty scope="context" name="appVersion" source="spring.application.version" defaultValue="1.0.0"/>

    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${appName}","version":"${appVersion}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

**Every log entry must include:**

| Field | Description |
|-------|-------------|
| `timestamp` | ISO 8601 UTC |
| `level` | INFO / WARN / ERROR / DEBUG |
| `service` | `event-gateway` or `account-service` |
| `version` | App version |
| `traceId` | From MDC |
| `spanId` | From MDC / Micrometer |
| `message` | Human-readable log message |
| `logger_name` | Fully qualified class name |

**Example JSON log output:**
```json
{
  "@timestamp": "2026-05-15T14:03:01.123Z",
  "level": "INFO",
  "service": "event-gateway",
  "version": "1.0.0",
  "traceId": "abc123def456",
  "spanId": "0123456789ab",
  "message": "Event processed successfully",
  "logger_name": "com.eventledger.gateway.service.EventService",
  "eventId": "evt-001",
  "accountId": "acct-123",
  "durationMs": 45
}
```

### 11.2 Metrics

Both services expose a Prometheus-compatible `/actuator/prometheus` endpoint.

**Required custom metrics:**

| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `events_submitted_total` | Counter | Total events received by Gateway | `status` (processed/duplicate/failed) |
| `events_processing_duration_seconds` | Histogram | Time to process an event end-to-end | `type` (CREDIT/DEBIT) |
| `account_service_calls_total` | Counter | Calls made to Account Service | `outcome` (success/failure) |
| `circuit_breaker_state` | Gauge | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) | `name` |
| `transactions_applied_total` | Counter | Transactions applied in Account Service | `type` (CREDIT/DEBIT) |
| `duplicate_events_total` | Counter | Duplicate events detected | `service` |

**Actuator configuration:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics, circuitbreakers
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
```

---

## 12. Resiliency Patterns

### 12.1 Chosen Pattern: Circuit Breaker (Primary) + Timeout (Secondary)

**Rationale:** The Circuit Breaker pattern is the most appropriate choice because:
- It prevents cascading failures from Account Service outages.
- Provides fast-fail behavior, freeing Gateway resources immediately.
- Has clearly observable states (CLOSED / OPEN / HALF_OPEN) useful for monitoring.
- Spring Boot 3.x natively integrates Resilience4j.

**Secondary pattern — Timeout:** Prevents indefinite blocking on Account Service calls.

### 12.2 Resilience4j Configuration

```yaml
# event-gateway/src/main/resources/application.yml

resilience4j:
  circuitbreaker:
    instances:
      accountService:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50          # Open after 50% failures in window
        wait-duration-in-open-state: 30s    # Stay OPEN for 30 seconds
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - com.eventledger.gateway.exception.AccountServiceException
        ignore-exceptions:
          - com.eventledger.gateway.exception.DuplicateEventException

  timelimiter:
    instances:
      accountService:
        timeout-duration: 5s
        cancel-running-future: true

  retry:
    instances:
      accountService:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - org.springframework.web.reactive.function.client.WebClientRequestException
        ignore-exceptions:
          - com.eventledger.gateway.exception.AccountServiceException
```

### 12.3 Circuit Breaker States

```
               Failures ≥ 50% in window
   CLOSED ──────────────────────────────────▶ OPEN
     ▲                                          │
     │                                    wait 30s
     │                                          │
     │     3 probe calls all succeed            ▼
  CLOSED ◀────────────────────────────── HALF_OPEN
                                               │
                                    any probe fails
                                               │
                                               ▼
                                            OPEN
```

### 12.4 Fallback Behavior

When circuit is OPEN or call times out:
```java
@CircuitBreaker(name = "accountService", fallbackMethod = "accountServiceFallback")
public TransactionResponse applyTransaction(EventRequest request, String traceId) { ... }

public TransactionResponse accountServiceFallback(EventRequest request, String traceId, Throwable ex) {
    log.warn("Account Service unavailable, circuit breaker fallback triggered. traceId={}", traceId);
    throw new AccountServiceException("Account Service is currently unavailable. Please retry later.", ex);
}
```

---

## 13. Graceful Degradation

| Endpoint | Account Service UP | Account Service DOWN |
|----------|--------------------|---------------------|
| `POST /events` | 201/200 (normal flow) | 503 with `ErrorResponse` |
| `GET /events/{id}` | 200 (Gateway-local data) | 200 (Gateway-local data — unaffected) |
| `GET /events?account=` | 200 (Gateway-local data) | 200 (Gateway-local data — unaffected) |
| `GET /accounts/{id}/balance` | 200 | 503 with clear message |
| `GET /health` (Gateway) | `accountService.status: UP` | `accountService.status: DOWN`, circuit state |

**503 Error Response when Account Service is down:**
```json
{
  "status": 503,
  "error": "Service Unavailable",
  "message": "Account Service is currently unavailable. Event has not been processed. Please retry.",
  "traceId": "abc123def456",
  "timestamp": "2026-05-15T14:03:00Z",
  "retryAfterSeconds": 30
}
```

---

## 14. Docker Compose Setup

### 14.1 docker-compose.yml

```yaml
version: '3.9'

services:

  mongodb:
    image: mongo:7.0
    container_name: event-ledger-mongodb
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD:-changeme}
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
      - ./mongo-init.js:/docker-entrypoint-initdb.d/mongo-init.js:ro
    networks:
      - event-ledger-network
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  account-service:
    build:
      context: ./account-service
      dockerfile: Dockerfile
    container_name: account-service
    restart: unless-stopped
    depends_on:
      mongodb:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_MONGODB_URI: mongodb://account_svc_user:${ACCOUNT_SVC_DB_PASSWORD:-changeme}@mongodb:27017/account_service_db
      SERVER_PORT: 8081
    expose:
      - "8081"      # Internal only — not published to host
    networks:
      - event-ledger-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s

  event-gateway:
    build:
      context: ./event-gateway
      dockerfile: Dockerfile
    container_name: event-gateway
    restart: unless-stopped
    depends_on:
      mongodb:
        condition: service_healthy
      account-service:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_MONGODB_URI: mongodb://gateway_user:${GATEWAY_DB_PASSWORD:-changeme}@mongodb:27017/event_gateway_db
      ACCOUNT_SERVICE_BASE_URL: http://account-service:8081
      SERVER_PORT: 8080
    ports:
      - "8080:8080"   # Only Gateway is exposed externally
    networks:
      - event-ledger-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s

volumes:
  mongo_data:
    driver: local

networks:
  event-ledger-network:
    driver: bridge
    name: event-ledger-network
```

### 14.2 mongo-init.js — Database & User Initialization

```javascript
// mongo-init.js — runs once on first container startup

// Create event_gateway_db and its user
db = db.getSiblingDB('event_gateway_db');
db.createUser({
  user: 'gateway_user',
  pwd: process.env.GATEWAY_DB_PASSWORD || 'changeme',
  roles: [{ role: 'readWrite', db: 'event_gateway_db' }]
});
db.events.createIndex({ "eventId": 1 }, { unique: true });
db.events.createIndex({ "accountId": 1, "eventTimestamp": 1 });
db.events.createIndex({ "status": 1 });
db.events.createIndex({ "createdAt": 1 });

// Create account_service_db and its user
db = db.getSiblingDB('account_service_db');
db.createUser({
  user: 'account_svc_user',
  pwd: process.env.ACCOUNT_SVC_DB_PASSWORD || 'changeme',
  roles: [{ role: 'readWrite', db: 'account_service_db' }]
});
db.accounts.createIndex({ "accountId": 1 }, { unique: true });
db.transactions.createIndex({ "eventId": 1 }, { unique: true });
db.transactions.createIndex({ "accountId": 1, "eventTimestamp": 1 });
```

### 14.3 .env.example

```dotenv
# Copy to .env and fill values
MONGO_ROOT_PASSWORD=changeme
GATEWAY_DB_PASSWORD=gateway_secret
ACCOUNT_SVC_DB_PASSWORD=account_secret
```

### 14.4 Dockerfile (both services — identical pattern)

```dockerfile
# event-gateway/Dockerfile (and account-service/Dockerfile)
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

> **Multi-stage build recommendation:** Add a `BUILD` stage using `maven:3.9-eclipse-temurin-21` to compile inside Docker, so no local Maven install is required.

### 14.5 Application Configuration (Docker profile)

```yaml
# event-gateway/src/main/resources/application-docker.yml
spring:
  application:
    name: event-gateway
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI}
      database: event_gateway_db

account-service:
  base-url: ${ACCOUNT_SERVICE_BASE_URL:http://account-service:8081}

logging:
  level:
    root: INFO
    com.eventledger: DEBUG
```

```yaml
# account-service/src/main/resources/application-docker.yml
spring:
  application:
    name: account-service
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI}
      database: account_service_db
```

---

## 15. Security Considerations

> Authentication/authorization is out of scope for this exercise, but the following notes document
> what would be added in a production system.

| Concern | Production Approach | Status |
|---------|---------------------|--------|
| Auth on Gateway | OAuth 2.0 / JWT Bearer tokens | Out of scope |
| Network isolation | Account Service not exposed externally | ✅ Implemented via Docker internal network |
| TLS | HTTPS on all endpoints | Out of scope (use a reverse proxy in prod) |
| Secrets | Environment variables via `.env` | ✅ `.env.example` provided |
| MongoDB auth | Username/password per service | ✅ Implemented in `mongo-init.js` |
| Input validation | Bean Validation + controller-level | ✅ Required |
| CORS | Not required (no browser clients) | N/A |

---

## 16. Automated Tests

### 16.1 Testing Dependencies (both services)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
```

### 16.2 Test Categories & Coverage Requirements

#### A. Unit Tests (Service Layer)

| Test Class | What It Tests |
|------------|---------------|
| `EventServiceTest` | Submit event, duplicate detection, event not found |
| `AccountServiceClientTest` | WebClient success path, 4xx/5xx handling, timeout |
| `AccountServiceTest` | Apply transaction, balance computation, idempotency, out-of-order |

#### B. Controller / Slice Tests (`@WebMvcTest`)

| Test Class | What It Tests |
|------------|---------------|
| `EventControllerTest` | All endpoints, validation errors, 400/404/503 responses |
| `AccountControllerTest` | All endpoints, 400/404 responses |

Test cases must include:

- `POST /events` with valid payload → `201`
- `POST /events` with duplicate `eventId` → `200` (same body)
- `POST /events` with missing required field → `400` with field-level errors
- `POST /events` with `amount = 0` → `400`
- `POST /events` with `type = "TRANSFER"` → `400`
- `GET /events/{id}` for existing event → `200`
- `GET /events/{id}` for unknown event → `404`
- `GET /events?account=acct-123` → `200` sorted list
- `GET /events` without `account` param → `400`

#### C. Integration Tests (`@SpringBootTest` + Testcontainers)

| Test Class | What It Tests |
|------------|---------------|
| `EventIntegrationTest` | Full stack: POST → MongoDB → GET, out-of-order sorting |
| `AccountIntegrationTest` | Full stack: transaction applied, balance computed correctly |

Test cases must include:

- **Out-of-order:** Submit `evt-002` (timestamp 09:00) then `evt-001` (timestamp 08:00) → GET list returns `[evt-001, evt-002]`
- **Balance after out-of-order:** 2 CREDITs + 1 DEBIT in random order → correct final balance
- **Full Gateway → Account Service flow:** Mock or real Account Service called, traceId propagated

#### D. Resiliency Tests

| Test Class | What It Tests |
|------------|---------------|
| `ResiliencyIntegrationTest` | Circuit breaker behavior when Account Service is down |

Test cases must include:

- Start Gateway pointing at a `MockWebServer` that returns 500.
- After N failures (per `sliding-window-size`), circuit breaker transitions to OPEN.
- Gateway returns `503` with meaningful error (not 500, not timeout hang).
- Circuit breaker is in OPEN state (verify via `/actuator/circuitbreakers`).
- After `wait-duration-in-open-state`, circuit transitions to HALF_OPEN.

#### E. Trace Propagation Tests

| Test Class | What It Tests |
|------------|---------------|
| `TracePropagationTest` | Trace IDs flow from Gateway to Account Service |

Test cases must include:

- Verify Gateway generates `X-Trace-Id` header on each request.
- Verify Gateway propagates `X-Trace-Id` to Account Service call.
- Verify Account Service response includes same `traceId`.
- Verify both service logs contain same `traceId` for a single request.

### 16.3 Running Tests

```bash
# Run all tests in both services
cd event-gateway && ./mvnw test
cd account-service && ./mvnw test

# Run with coverage report
./mvnw verify -Pcoverage

# Run only integration tests
./mvnw test -Dtest="*IntegrationTest"

# Run only unit tests
./mvnw test -Dtest="*Test" -DexcludedGroups="integration"
```

---

## 17. README Requirements

The `README.md` at the repo root must include all of the following sections:

### 17.1 Required Sections

```markdown
# Event Ledger

## Architecture Overview
- Brief description of both services
- How they interact (request lifecycle diagram)
- MongoDB database separation

## Prerequisites
- Java 21
- Maven 3.9+ (or use ./mvnw)
- Docker 24+ and Docker Compose v2
- curl / HTTPie (optional, for testing)

## Running with Docker Compose (recommended)
cp .env.example .env        # edit values if needed
docker compose up --build   # starts MongoDB + Account Service + Gateway

## Running Manually (without Docker)
# Start MongoDB locally first
# Start Account Service
cd account-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Start Gateway
cd event-gateway && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

## Running Tests
cd event-gateway && ./mvnw test
cd account-service && ./mvnw test

## API Examples (curl)
# Submit event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

# Get event
curl http://localhost:8080/events/evt-001

# List events for account
curl http://localhost:8080/events?account=acct-123

# Get balance
curl http://localhost:8081/accounts/acct-123/balance

# Health checks
curl http://localhost:8080/health
curl http://localhost:8081/health

## Resiliency Pattern — Circuit Breaker
Explanation of why Circuit Breaker was chosen, how it is configured,
and how to observe it via /actuator/circuitbreakers.

## Observability
- How to read JSON logs
- Prometheus metrics at /actuator/prometheus
- How to trace a request across both services using traceId
```

---

## 18. Bonus Enhancements

These are not required but are recommended to demonstrate production readiness:

| Enhancement | Implementation Guide |
|-------------|---------------------|
| **OpenTelemetry Collector + Jaeger** | Add `otel-collector` and `jaeger` services in Docker Compose; configure OTLP exporter in both services |
| **Prometheus + Grafana** | Add `prometheus` and `grafana` services; import Spring Boot dashboard |
| **Retry with exponential backoff + jitter** | Configure `resilience4j.retry.instances.accountService.enable-exponential-backoff=true` with jitter factor |
| **Rate limiting on Gateway** | Use `resilience4j.ratelimiter` or Spring Cloud Gateway's rate limiter on `POST /events` |
| **Async fallback queue** | When Account Service is down, store events with `status=PENDING` and process them via a scheduled `@Scheduled` task when service recovers |
| **Contract tests (Pact)** | Add `pact-jvm-provider` and `pact-jvm-consumer` to both services; define contract for `POST /accounts/{id}/transactions` |
| **Pagination on GET /events** | Add `page` and `size` query params; return `Page<EventResponse>` with metadata |

---

## 19. Acceptance Criteria Checklist

Use this checklist to verify the implementation meets all requirements before submission.

### Core Functionality
- [ ] `POST /events` returns `201` for a new valid event
- [ ] `POST /events` returns `200` for a duplicate `eventId` (no duplicate in DB)
- [ ] `POST /events` returns `400` with field-level errors for invalid input
- [ ] `GET /events/{id}` returns `200` for existing event, `404` for unknown
- [ ] `GET /events?account=` returns list sorted by `eventTimestamp` ascending
- [ ] Balance = SUM(CREDITs) - SUM(DEBITs) is always correct
- [ ] Out-of-order events produce correct sorted list and correct balance

### Service Separation
- [ ] Event Gateway runs on port 8080, Account Service on port 8081
- [ ] Each service has its own MongoDB database (`event_gateway_db`, `account_service_db`)
- [ ] No shared state or in-process calls between services
- [ ] Account Service port not exposed externally in Docker Compose

### Distributed Tracing
- [ ] Gateway generates `traceId` on every request
- [ ] Gateway propagates `X-Trace-Id` and `traceparent` headers to Account Service
- [ ] Account Service logs the received `traceId`
- [ ] Both services return `traceId` in error responses
- [ ] Automated test verifies trace propagation

### Observability
- [ ] Logs are JSON-structured with `timestamp`, `level`, `service`, `traceId`, `message`
- [ ] `GET /health` returns `200` with MongoDB connectivity check on both services
- [ ] At least one custom metric exposed via `/actuator/prometheus`
- [ ] Metrics cover: request count, error rate, circuit breaker state

### Resiliency
- [ ] Circuit Breaker configured on Gateway → Account Service call
- [ ] Circuit transitions CLOSED → OPEN after failure threshold
- [ ] Circuit transitions OPEN → HALF_OPEN after wait duration
- [ ] Fallback returns `503` (not `500`, not a hang) when circuit is open
- [ ] Timeout of 5 seconds on Account Service calls
- [ ] Automated test verifies circuit breaker opens on repeated failures

### Graceful Degradation
- [ ] `POST /events` → `503` when Account Service is down (not 500)
- [ ] `GET /events/{id}` works when Account Service is down
- [ ] `GET /events?account=` works when Account Service is down
- [ ] Balance endpoint returns clear error when Account Service is unreachable

### Infrastructure
- [ ] `docker-compose.yml` starts MongoDB, Account Service, Gateway with one command
- [ ] `mongo-init.js` creates databases, users, and indexes on first run
- [ ] `.env.example` documents all required environment variables
- [ ] Both services have `Dockerfile` using `eclipse-temurin:21-jre-alpine`
- [ ] Java 21 virtual threads enabled in both services

### Testing
- [ ] Unit tests for service layer (idempotency, balance, out-of-order)
- [ ] Controller slice tests for all endpoints (happy + error paths)
- [ ] Integration tests using Testcontainers (MongoDB)
- [ ] Resiliency test: circuit breaker opens, `503` returned
- [ ] Trace propagation test: `traceId` flows from Gateway to Account Service
- [ ] All tests pass with `./mvnw test`

### Documentation
- [ ] `README.md` covers architecture, setup, run, test, and resiliency explanation
- [ ] Commit history shows incremental progress (not squashed)
- [ ] ADRs documented (at minimum in README or this PRD)

---

*End of PRD — Event Ledger v1.0.0*
