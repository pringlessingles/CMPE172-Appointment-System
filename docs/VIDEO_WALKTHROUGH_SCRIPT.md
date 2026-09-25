# Minimum 5-Minute Code Walkthrough Script

Use this as a speaking guide. Demonstrate the code and explain it in your own words rather than reading the script verbatim.

## 0:00-0:40 - Project scope

Introduce yourself, the Community Health Clinic Appointment System, and the two Milestone 1 endpoints. Explain that the application schedules visits but intentionally stores no diagnoses, notes, insurance records, or other clinical data.

## 0:40-1:25 - Application structure

Open the package tree. Point out `ClinicController`, `ClinicService`, `ClinicRepository`, and the DTO package. Explain that each layer has one responsibility and that constructors provide dependencies.

## 1:25-2:20 - Database schema

Open `schema.sql`. Walk through users, providers, services, availability_slots, and appointments. Show the primary and foreign keys. Emphasize `uq_appointments_slot UNIQUE (availability_slot_id)`: the database rejects a second appointment for the same slot even if two requests race. Mention the provider/start uniqueness and check constraints.

## 2:20-2:55 - Seed data

Open `seed.sql`. Show the provider users, customer, services, and slots. Explain that Spring loads both SQL files at startup because `spring.sql.init.mode=always`.

## 2:55-3:50 - Request flow

Start at `ClinicController.availableSlots`. Follow the call into `ClinicService`, where page and size are validated and total pages are calculated. Continue to `ClinicRepository`; explain named parameters, optional filters, SQL `LIMIT` and `OFFSET`, and the row mapper that creates an immutable `AvailabilitySlotDto`.

## 3:50-4:25 - Front Controller decision

Explain that Spring's `DispatcherServlet` receives all REST requests as a Front Controller and dispatches them to handlers. Contrast this with a Thymeleaf Page Controller that returns a view for each page. This project uses REST because a separate client can consume JSON.

## 4:25-5:05 - Run and demonstrate

Run `mvn spring-boot:run`. Call `/` and show that the response includes provider and service data from H2. Call `/slots?providerId=1&date=2026-10-05&page=0&size=1` and point out the filtered item and pagination metadata.

## 5:05-5:35 - Tests and milestone boundary

Run `mvn test`. Open the integration test and explain that it verifies startup SQL, both database-reading endpoints, filtering, pagination, and bad-request behavior. Close by stating that booking, cancellation, authentication, notifications, observability, and AI are later-milestone work.

