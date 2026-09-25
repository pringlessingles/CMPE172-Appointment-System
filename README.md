# Community Health Clinic Appointment System

CMPE 172 Milestone 1 project by **Newton Tran** (Student ID: **015110368**).

This repository contains a minimal Spring Boot application that reads clinic provider, service, and availability data from an H2 SQL database through plain JDBC. It intentionally does not use JPA, Hibernate, or another ORM.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

## Run the application

```bash
mvn spring-boot:run
```

The application starts at `http://localhost:8080`. `schema.sql` and `seed.sql` are loaded automatically on every startup.

Try the required database-reading endpoints:

```bash
curl http://localhost:8080/
curl "http://localhost:8080/slots?page=0&size=5"
curl "http://localhost:8080/slots?providerId=1&date=2026-10-05"
```

The embedded H2 console is available at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:clinicdb`, username `sa`, and a blank password. The console is enabled only for this development skeleton.

## Run tests

```bash
mvn test
```

The integration tests start the application, initialize the in-memory database, and verify both required endpoints and slot filtering/pagination.

## Layered request flow

1. `ClinicController` accepts HTTP requests and validates query parameters.
2. `ClinicService` owns use-case orchestration and the pagination contract.
3. `ClinicRepository` executes parameterized SQL with `NamedParameterJdbcTemplate`.
4. H2 enforces keys, foreign keys, checks, and the double-booking constraint.
5. DTO records are serialized to JSON; persistence rows never leak directly from the repository.

## Project layout

```text
src/main/java/.../controller   HTTP endpoints
src/main/java/.../service      application/use-case logic
src/main/java/.../repository   JDBC and SQL mapping
src/main/java/.../dto          response-only records
src/main/resources/schema.sql  five-table relational schema
src/main/resources/seed.sql    sample users, providers, services, slots
src/test                       integration tests
docs                           report source and walkthrough script
output/pdf                     final report, ER diagram, wireframes
```

For a precise requirement-to-file map, open `docs/ZIP_CONTENTS_GUIDE.md`. For a read-aloud video draft, open `docs/VIDEO_WALKTHROUGH_WORD_FOR_WORD.md`.

## Milestone scope

Milestone 1 implements read-only skeleton endpoints. Authentication, booking, cancellation, provider management, notifications, observability, and AI features are documented in the proposal but intentionally deferred to later milestones.

## Before submission

1. Create a GitHub repository and push this folder.
2. Record the walkthrough using `docs/VIDEO_WALKTHROUGH_SCRIPT.md`.
3. Add the GitHub repository URL and video URL to the submission portal.
4. Review and personalize the report so it reflects your own understanding.
