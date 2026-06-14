# AGENTS.md

Guidance for coding agents working in this repository.

## Project Overview

Event Ledger is a Java 21 / Spring Boot 3.5.x multi-module Maven project for a distributed financial transaction system. The product direction is documented in `Event_Ledger_PRD.md`.

Current Maven modules:

- `event-gateway`: public API service that receives transaction events, validates/deduplicates them, persists event status, and calls Account Service.
- `account-service`: account state service that applies transactions and maintains balances.

The root `pom.xml` is an aggregator POM with shared Spring Boot dependencies and plugin configuration.

## Repository Layout

- `pom.xml`: parent/aggregator Maven POM.
- `mvnw`, `mvnw.cmd`: root Maven wrapper; prefer this over system Maven.
- `event-gateway/`: Spring Boot service module for event ingestion.
- `account-service/`: Spring Boot service module for account balances.
- `Event_Ledger_PRD.md`: requirements, architecture, acceptance criteria, and target stack.
- `HELP.md`: generated Spring Boot reference links.

Do not edit generated build output under `target/` unless explicitly requested.

## Build And Test Commands

Use the root Maven wrapper from the repository root unless you intentionally need to isolate a module.

- Build and run all tests: `./mvnw clean verify`
- Run all tests without cleaning: `./mvnw test`
- Build one module and required dependencies: `./mvnw -pl event-gateway -am verify`
- Build one module and required dependencies: `./mvnw -pl account-service -am verify`
- Run Event Gateway locally: `./mvnw -pl event-gateway spring-boot:run`
- Run Account Service locally: `./mvnw -pl account-service spring-boot:run`

If a command downloads dependencies, expect network access to be required.

## Coding Conventions

- Use Java 21 language features where they simplify code, but keep service code readable and conventional for Spring Boot.
- Keep package names under `com.github.itsdhanashri.eventledger`.
- Put service-specific code inside its module; avoid cross-module compile-time coupling unless a shared module is deliberately introduced.
- Keep DTOs, domain models, persistence models, and controllers separated once the services grow beyond scaffolding.
- Prefer constructor injection for Spring beans.
- Prefer immutable request/response DTOs where practical, such as records for simple payloads.
- Keep configuration in `application.yaml`; use profile-specific files only when needed.

## Architecture Expectations

Follow `Event_Ledger_PRD.md` as the source of truth for behavior and acceptance criteria. Important constraints:

- Services are independently deployable.
- `event-gateway` and `account-service` must not share a database schema or directly access each other's persistence store.
- Inter-service communication is synchronous REST for this project.
- Event handling must be idempotent by `eventId`.
- Account balance handling must tolerate out-of-order events.
- Preserve trace propagation headers when one service calls another.
- Prefer explicit failure states and observable errors over silent retries.

## Testing Expectations

- Add or update tests with behavior changes.
- Keep the basic Spring context tests passing.
- Use unit tests for domain/business rules such as idempotency, ordering, and balance calculations.
- Use integration tests for persistence and REST boundaries when those layers are introduced.
- Do not rely on existing `target/` test reports as proof of current correctness; rerun Maven tests after changes when feasible.

## Git And Generated Files

- Treat `target/`, IDE metadata, and other generated files as non-source artifacts.
- Do not revert user changes unless explicitly instructed.
- Before broad edits, inspect the current working tree and avoid overwriting unrelated work.
- Keep commits focused by module or feature when possible.

## Documentation

- Update `README.md` if new setup, run, API, Docker, or operational behavior is added.
- Update module docs only when behavior differs per service.
- Keep `Event_Ledger_PRD.md` as requirements context; do not rewrite it casually during implementation work.
