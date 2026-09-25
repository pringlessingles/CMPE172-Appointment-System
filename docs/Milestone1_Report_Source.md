# CMPE 172 Milestone 1 - Requirements, Design, and Skeleton

**Project:** Community Health Clinic Appointment System  
**Student:** Newton Tran  
**Student ID:** 015110368

## Proposal

The project is a scheduling system for a small community health clinic. Customers (patients) browse open times and later milestones will let them authenticate, book, review, and cancel appointments. Providers and administrators publish service-specific availability and review their booked visits. The system manages scheduling metadata only; it does not store diagnoses, clinical notes, insurance data, or other protected health information.

The implementation uses Java 17, Spring Boot, SQL, and direct JDBC through Spring JDBC. No ORM is used. It realizes classic enterprise standards on a modern stack: Spring Boot provides the modern J2EE-style application container and REST provides distributed service boundaries analogous to CORBA/distributed objects.

## Roles

- Customer/patient: browses slots and later books, reviews, and cancels their own appointments.
- Provider: publishes availability for services and later views appointments assigned to them.
- Administrator: maintains clinic reference data and operational oversight.

## Twelve-feature roadmap

1. Customer log in.
2. Browse, filter, and paginate available slots by provider, service, and date using SQL LIMIT and OFFSET.
3. Book a slot for a chosen service.
4. View upcoming and historical appointments.
5. Cancel an owned appointment.
6. Provider log in.
7. Provider creates or removes service-specific availability slots.
8. Provider views appointments booked with them.
9. Send a confirmation through a mock external service on book or cancel.
10. Logging, a health endpoint, and at least one metric.
11. Conversational booking and autonomous agents.
12. Grounded Q&A over a small clinic knowledge base.

Milestone 1 implements only the layered skeleton and two database-reading endpoints.

## System boundary

Client requests enter the Spring Boot application, pass through controller, service, and JDBC repository layers, and reach the SQL database. A mock notification boundary is documented for Milestone 3 but not invoked in this milestone.

## Database design

The five entities are users, providers, services, availability_slots, and appointments. Providers are specialized user profiles. Each provider offers one or more services. Each service and provider can own many availability slots. A customer user can have many appointments. Each appointment refers to exactly one slot, provider, and service.

The slot is not modeled as a weak entity because it has its own surrogate identifier and independent lifecycle. Provider remains existence-dependent on user through a mandatory unique foreign key. Appointment depends on existing customer, provider, service, and slot rows.

Double-booking is prevented by `UNIQUE (availability_slot_id)` on appointments. Even if two application requests race, the database can accept at most one appointment for a slot. A separate `UNIQUE (provider_id, starts_at)` constraint prevents a provider from publishing two slots with the same start time. Application transactions in Milestone 2 will complement, not replace, these database guarantees.

## Request flow and controller pattern

For `GET /slots`, Spring's `DispatcherServlet` acts as the Front Controller. It routes the request to `ClinicController`, which parses filters. `ClinicService` validates pagination and coordinates the use case. `ClinicRepository` sends parameterized SQL to H2 using `NamedParameterJdbcTemplate`; the query uses SQL `LIMIT` and `OFFSET`. Rows are mapped to immutable DTO records and serialized as JSON.

The project chooses the Front Controller pattern because it exposes a REST API suitable for a browser SPA or other client. A server-rendered Thymeleaf design would instead emphasize Page Controllers in which handlers select templates for individual pages. The two patterns are not mutually exclusive in Spring, but the REST skeleton keeps the transport boundary explicit.

## Milestone 1 endpoints

- `GET /` returns system metadata plus provider and service DTOs read from the database.
- `GET /slots` returns available slot DTOs, with optional provider, service, and date filters and bounded pagination.

## Constraints and scope

Primary keys are surrogate BIGINT identities. Foreign keys preserve referential integrity. Role and status checks constrain state values. Time, duration, and price checks reject invalid records. Emails are unique. This milestone deliberately excludes authentication, writes, notification calls, clinical data, and AI behavior.

