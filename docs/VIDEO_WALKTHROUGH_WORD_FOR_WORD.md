# CMPE 172 Milestone 1 Video - Core-Code Script

**Speaker:** Newton Tran  
**Student ID:** 015110368  
**Target length:** approximately 5 to 6 minutes

This version is intentionally focused on the core implementation. The assignment says to explain the code, not just demonstrate the application. Text in square brackets is a silent screen cue. Everything in quotation marks is intended to be spoken.

## 0:00-0:25 - One-sentence project context

[Show the project name and open the root folder.]

“Hello, my name is Newton Tran, and my student ID is 015110368. This is my CMPE 172 Milestone 1 Community Health Clinic Appointment System. I will focus on the core code that reads available clinic slots from the SQL database through the Controller, Service, and Repository layers.”

## 0:25-0:55 - Show the layered structure

[Expand `src/main/java/clinic/`.]

“The core package is divided into four parts. The controller package handles HTTP requests. The service package handles application logic. The repository package handles JDBC and SQL. The DTO package defines the JSON response objects. The application class starts Spring Boot.

This separation means that the HTTP layer does not contain SQL, and the repository does not decide how an HTTP response should look.”

## 0:55-1:35 - Explain the database contract

[Open `src/main/resources/schema.sql` and show the table names.]

“The database has five required tables: users, providers, services, availability_slots, and appointments.

For this milestone, the most important database rule is the unique constraint on appointments dot availability underscore slot underscore id. That constraint means that one availability slot can have at most one appointment. The database enforces this rule, so the protection still exists if two requests arrive at nearly the same time.

The slot table also has provider and service foreign keys, a time-order check, and a status check. The seed file provides sample rows so the read endpoints have data when the application starts.”

## 1:35-2:25 - Explain the Controller

[Open `src/main/java/clinic/controller/ClinicController.java`.]

“This is ClinicController, and it is annotated with RestController.

The first method maps to GET slash. It calls the service getHome method and returns a HomeDto. The second method maps to GET slash slots. It accepts optional provider ID, service ID, and date filters. It also accepts page and size parameters with defaults of zero and ten.

The controller does not query the database. Its job is to receive request parameters, pass them to the service, and return a DTO. This keeps the transport layer small and easy to test.”

## 2:25-3:10 - Explain the Service

[Open `src/main/java/clinic/service/ClinicService.java`.]

“This is ClinicService. It is the application layer between the controller and repository.

The getHome method asks the repository for active providers and active services, then packages them into HomeDto.

The getAvailableSlots method validates the page and size values. Page cannot be negative, and size must be between one and one hundred. The service converts page and size into a SQL offset. It then asks the repository for the current page and for the total count. Finally, it creates SlotPageDto with the items, page, size, total items, and total pages.

The service owns this pagination behavior so the controller does not need to know how the database calculates offsets.”

## 3:10-4:25 - Explain the Repository and SQL

[Open `src/main/java/clinic/repository/ClinicRepository.java`.]

“This is ClinicRepository. It uses NamedParameterJdbcTemplate from Spring JDBC. There is no JPA, Hibernate, or other ORM in this project.

The findActiveProviders method joins providers to users and returns provider DTOs. The findActiveServices method reads active services and returns service DTOs.

The main method is findAvailableSlots. It joins availability_slots to providers, users, and services. It always filters for available slots. If the caller supplies a provider, service, or date, the repository adds that filter and a named SQL parameter. The query orders the results by start time and provider name, then applies LIMIT and OFFSET for pagination.

The row mapper converts each SQL result row into an AvailabilitySlotDto. The repository also has a count query. The service uses that count to tell the client how many pages exist.

This is the key request path: the controller receives the request, the service validates and coordinates it, the repository executes parameterized SQL, and the DTO comes back as JSON.”

## 4:25-4:55 - Explain the response DTOs

[Open the DTO folder and show `AvailabilitySlotDto.java` and `SlotPageDto.java`.]

“The response objects are Java records. AvailabilitySlotDto contains the slot ID, provider information, service information, and start and end times. SlotPageDto contains the list of slot DTOs plus pagination metadata.

Using DTOs means the API returns only deliberate response fields. Database rows and internal persistence details are not serialized directly.”

## 4:55-5:25 - Show the test that covers the core path

[Open `src/test/java/clinic/ClinicControllerIntegrationTest.java`.]

“The integration test starts the Spring application with the SQL schema and seed data. The first test verifies that GET slash reads two providers and four services. The second test verifies provider and date filtering, page size one, total item count, and total page count for GET slash slots. The third test verifies that an invalid page size returns a bad request.

These tests cover the core database-reading behavior rather than only checking that the application starts.”

## 5:25-5:45 - Optional one-command demonstration and conclusion

[If the application has been started, run `curl "http://localhost:8080/slots?providerId=1&date=2026-10-05&page=0&size=1"`.]

“This response shows one filtered slot and the pagination metadata produced by the Controller, Service, and Repository path I just explained.

That completes my Milestone 1 core-code walkthrough. The implementation demonstrates a layered Spring Boot application using direct JDBC, DTO responses, SQL pagination, startup seed data, and a database-level double-booking guard.”

## Commands to use before recording

```bash
mvn spring-boot:run
mvn test
```

The live curl demonstration is optional for the explanation, but run the application once before submission so you can honestly describe it as a running skeleton.

