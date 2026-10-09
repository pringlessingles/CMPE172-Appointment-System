# Doctor Appointment System

CMPE 172 Milestones 1 and 2 project by Newton Tran.

Spring Boot with Thymeleaf pages, Spring Security session login, and plain JDBC SQL. No JPA or Hibernate. Patients browse, book, and cancel appointments; doctors manage their availability and view their appointments.

## Run on Windows

Install a JDK (Java 17 or newer). In PowerShell, from this folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). The wrapper downloads Maven and dependencies on its first run, so internet access is needed initially. On macOS/Linux use `./mvnw spring-boot:run`, or use an installed Maven with `mvn spring-boot:run`.

## Demo accounts

All demo accounts use password `Appointment123!`.

| Account | Email | Database role |
| --- | --- | --- |
| Patient | sofia.reyes@example.com | CUSTOMER |
| Second patient | alex.morgan@example.com | CUSTOMER |
| Doctor Maya Chen | maya.chen@doctor.example | PROVIDER |
| Doctor Jordan Lee | jordan.lee@doctor.example | PROVIDER |

Passwords in `seed.sql` are BCrypt hashes. Roles come from the database, not the submitted booking form. The seeded ADMIN account has no administrative UI in this milestone.

**Demo database:** H2 runs in memory. The schema and sample data reset every startup; bookings do not survive a restart. Sample slots use tomorrow/the following days instead of a fixed date. This setup is for coursework, not real patient information. A durable deployment would need a persistent database and migrations instead of the reset script. The H2 console is disabled.

## Browser flow

Home -> available slots -> patient login -> booking form -> confirmation -> my appointments.

- Filter available slots by doctor, service, and date. SQL applies `LIMIT` and `OFFSET`.
- Patients can cancel only their own future, booked appointments.
- Doctors use `/doctor` to create service-specific slots, remove available slots, and see appointments with them. Removal marks a slot BLOCKED instead of deleting appointment history.
- Finished appointments display COMPLETED immediately; a scheduled transactional task persists that status every minute.
- Patient-only and doctor-only routes enforce roles; authenticated users with the wrong role receive HTTP 403. POST forms include CSRF protection.

## Transaction and concurrency design

`BookingService.book()` uses `@Transactional(isolation = Isolation.READ_COMMITTED)`. It checks the role, selected service, active doctor/service, and future availability, then performs an atomic optimistic version update:

```sql
UPDATE availability_slots
SET status = 'BOOKED', version = version + 1
WHERE id = :id AND version = :version
  AND status = 'AVAILABLE' AND starts_at > CURRENT_TIMESTAMP;
```

Exactly one changed row means the claim succeeded. Zero means stale/unavailable data and returns HTTP 409. The appointment insert is in the same transaction; an insert failure rolls back the slot claim. Cancellation likewise updates the appointment and releases the slot in one transaction.

The database adds a second guard: `uq_appointments_slot` is unique on generated `active_slot_id`, which equals `availability_slot_id` only while the appointment is BOOKED. Cancelled/completed history has NULL there. A permanent UNIQUE on `availability_slot_id` would prevent rebooking even after cancellation, so the active-only constraint preserves history and still prevents two active bookings. Composite foreign keys ensure a slot's doctor and service match the appointment.

There is no automatic retry for a booking conflict: the patient must choose again after refreshing availability. Retrying stale intent automatically could book a slot after someone cancelled it. READ_COMMITTED plus the conditional write and unique constraint protects this specific race; it does not make every query serializable. H2 in-memory commits are not durable across application restarts.

## Layers and core files

- `security/SecurityConfig.java`: BCrypt, JDBC account lookup, session login, roles, CSRF.
- `controller/PageController.java`: HTML pages and form handlers.
- `controller/ClinicController.java`: public browsing JSON API.
- `controller/BookingController.java`: protected booking and doctor JSON API.
- `service/ClinicService.java`: filters and pagination validation.
- `service/BookingService.java`: booking/cancellation rules and transactions.
- `repository/ClinicRepository.java`, `BookingRepository.java`: parameterized JDBC SQL and DTO mapping.
- `resources/schema.sql`, `seed.sql`: five entities, constraints, version column, sample accounts.

Requests pass through Spring Security and DispatcherServlet to a controller, service, and repository. HTML controllers return Thymeleaf views; API controllers return JSON DTOs. DispatcherServlet remains Spring MVC's front controller in both cases.

## JSON endpoints

Public: `GET /api/home`, `GET /api/slots` (`/slots` remains an alias).

Patient: `GET /api/appointments`, `POST /api/appointments` with `{"slotId":1,"serviceId":1}`, `POST /api/appointments/{id}/cancel`.

Doctor: `GET /api/doctor/appointments`, `GET /api/doctor/slots`, `POST /api/doctor/slots` with serviceId/startsAt/endsAt, `DELETE /api/doctor/slots/{id}`.

Protected APIs use the login session and require a CSRF token for mutations; they are not anonymous curl endpoints. `/` now serves the website; the old home JSON is at `/api/home`.

## Verify

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Tests cover filtering, BCrypt/session login, role and ownership checks, CSRF, browser page rendering, booking/cancellation, failed-insert rollback, active-booking uniqueness, composite foreign keys, completion, and doctor slot management. The concurrency test uses two threads and a barrier so both read the same slot version before competing to book it: exactly one must succeed.

Reports and the read-aloud video script are maintained outside this repository. The report/video/submission ZIP are separate deliverables; this repository does not claim they are completed.
