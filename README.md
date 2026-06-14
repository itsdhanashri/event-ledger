# Event Ledger

Event Ledger is a Java 21 / Spring Boot 3.5 multi-service financial transaction system. It accepts transaction events, persists them idempotently, forwards them to an account service, and keeps balances correct even when events arrive out of order.

## Architecture Overview

- `event-gateway` runs on port `8080` and exposes the public `/events` API.
- `account-service` runs on port `8081` and applies transactions to account state.
- MongoDB stores each service's data in a separate database: `event_gateway_db` and `account_service_db`.
- The gateway calls account-service over synchronous REST and propagates `X-Trace-Id` plus W3C `traceparent` headers.

Request lifecycle:

```text
Client -> Event Gateway -> event_gateway_db
                    |-> Account Service -> account_service_db
```

## Prerequisites

- Java 21
- Maven 3.9+ or the included `./mvnw`
- Docker 24+ and Docker Compose v2
- `curl` or HTTPie for manual API testing

## Running With Docker Compose

Build service jars first, then start the stack:

```bash
cp .env.example .env
./mvnw clean package
docker compose up --build
```

Only the gateway is published to the host on `8080`. Account Service is exposed only inside the Docker network.

## Running Manually

Start MongoDB locally first, then run the services in separate terminals:

```bash
./mvnw -pl account-service spring-boot:run
./mvnw -pl event-gateway spring-boot:run
```

Useful environment overrides:

```bash
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/account_service_db
ACCOUNT_SERVICE_BASE_URL=http://localhost:8081
```

## Running Tests

```bash
./mvnw test
./mvnw -pl account-service test
./mvnw -pl event-gateway test
```

## API Examples

Submit an event:

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z","metadata":{"source":"manual"}}'
```

Get an event:

```bash
curl http://localhost:8080/events/evt-001
```

List events for an account:

```bash
curl 'http://localhost:8080/events?account=acct-123'
```

Get balance:

```bash
curl http://localhost:8081/accounts/acct-123/balance
```

Health checks:

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## Resiliency Pattern: Circuit Breaker

The gateway protects calls to Account Service with a Resilience4j circuit breaker named `accountService` plus a 5 second WebClient timeout. When Account Service is down or the circuit is open, `POST /events` stores the event as `FAILED` and returns `503` with a traceable error response instead of hanging or returning `500`.

Observe circuit breaker state through:

```bash
curl http://localhost:8080/actuator/circuitbreakers
curl http://localhost:8080/health
```

## Observability

- Logs are JSON formatted through Logstash Logback Encoder.
- Each request gets an `X-Trace-Id`; the gateway propagates it to Account Service.
- Prometheus metrics are available at `/actuator/prometheus` on both services.
- Custom metrics include event submission counts, duplicate counts, account-service call outcomes, transactions applied, and circuit breaker state.

## Core Endpoints

Gateway:

- `POST /events`
- `GET /events/{eventId}`
- `GET /events?account={accountId}`
- `GET /health`

Account Service:

- `POST /accounts/{accountId}/transactions`
- `GET /accounts/{accountId}/balance`
- `GET /accounts/{accountId}`
- `GET /health`
