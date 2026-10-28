# Doctor Appointment System

CMPE 172 Milestones 1 through 3 project by Newton Tran.

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

**Local demo database:** The command above still uses in-memory H2, so its sample data resets every startup. The Docker deployment below uses persistent PostgreSQL and Flyway migrations instead. This is a coursework demo, not a system for real patient information. The H2 console is disabled.

## Milestone 3 Docker deployment

Requires Docker Desktop with Linux containers (or Docker Engine with Compose on Linux).

```powershell
Copy-Item .env.example .env
# Edit .env and replace BOTH placeholder values before starting.
docker compose up --build -d --wait
```

Open http://localhost:8080. Check health with `curl.exe http://localhost:8080/health`.

Stop with `docker compose down`. The named `appointment-data` volume keeps the database after stopping/recreating containers. Do not use `down -v` unless you intend to delete the stored database. Never commit `.env`.

The `postgres` Spring profile disables the resetting H2 scripts. Flyway applies `db/migration/V1__appointment_system.sql` and `V2__demo_accounts_and_slots.sql` once and validates them on subsequent starts. Existing appointments, notification receipts, and queued events remain. Initial demo slots eventually become past appointments; doctors can create new slots. This does not copy an old in-memory H2 session into PostgreSQL.

The containers bind the website to localhost and use demonstration accounts. Before any public deployment, replace demo credentials, use HTTPS, configure secure session cookies, and review retention and access policies. No cloud deployment, real messages, or paid service is required for this milestone.

## Notifications and distribution boundary

Client -> Appointment Scheduler -> HTTP Notification Gateway -> Mock Notification Service.

`NotificationRequest` carries an event UUID, recipient, type, and message in one coarse-grained call. The receiver needs no follow-up calls to the scheduler. `NotificationGateway` separates the booking system from the transport; `HttpNotificationGateway` implements it using Java's HTTP client. `MockNotificationController` is the required second controller in the same app, standing in for an external service. It stores a receipt/fingerprint, logs the event ID/type, and returns acceptance; it sends no actual email or SMS.

1. `BookingService` saves the appointment/slot changes and a notification-outbox row in ONE database transaction. Cancellation does the same.
2. A `NotificationQueued` event is handled by `BookingNotificationListener` only AFTER_COMMIT. Rolled-back bookings send no notification.
3. The listener calls `NotificationDispatcher` through a Spring proxy with NOT_SUPPORTED propagation. This suspends the completed transaction's remaining resources, keeping the HTTP call outside a database transaction. Receipt/status writes use their own JDBC auto-commit operations.
4. The HTTP client uses a 750 ms connection timeout, 750 ms request timeout, up to 3 application attempts, and 100/200 ms backoffs. It retries transport failures, timeouts, HTTP 429, and HTTP 5xx, not other HTTP 4xx. Foreground notification delivery is synchronous and bounded, so it can briefly delay the response but cannot roll back the committed booking.
5. After failure, the event stays in the outbox. A scheduled worker retries due events every 60 seconds in batches of 20. Failed batches are deferred again. Database failures or a process crash also leave the saved event available for a later run/restart in PostgreSQL.

The event ID and payload remain identical across retries. The receiver's primary key accepts an event once; a duplicate with the same fingerprint returns success, while reuse with different content returns 409. This handles a lost HTTP response and overlapping recovery calls without creating duplicate mock notifications. Delivery is at-least-once, NOT a claim of exactly-once real-world delivery. The outbox `attempts` column counts delivery batches, not individual HTTP requests. A real external receiver must honor the same idempotency contract.

The HTTP endpoint `/api/mock-notifications` requires `X-Notification-Token`. Only that service-to-service endpoint is exempt from CSRF; patient and doctor mutations still require their login session and CSRF token. Its demo token is configured through `NOTIFICATION_TOKEN`; Compose requires an explicit value. Change `NOTIFICATION_URL` to point the gateway to a different implementation with the same contract. Use a consistent token on both sides.

## Health logging and metrics

- Public `GET /health` executes `SELECT 1`: HTTP 200 with `{"status":"UP","database":"UP"}`, or HTTP 503 with both DOWN. It does not expose database credentials or stack traces. A failed notification does not mark the booking database unhealthy.
- Actuator exposes `health` and `metrics`; `/actuator/**` requires ADMIN. Log in through the browser as the seeded `admin@doctor.example` account with the demo password to inspect `/actuator/metrics/appointments.booked`. There is no admin-management UI.
- `appointments.booked` and `appointments.cancelled` count committed actions only. `notifications.delivered` counts acknowledged delivery processing; `notifications.deferred` counts unsuccessful delivery batches. Counters are process-local and reset on restart; the persistent outbox is the delivery record. Under overlapping dispatches, delivery-processing counts may include duplicate acknowledgements, not distinct messages.

Example application log messages (IDs vary):

```text
INFO  appointment_committed appointmentId=8 action=BOOKING_CONFIRMED eventId=<uuid>
INFO  mock_notification_accepted eventId=<uuid> type=BOOKING_CONFIRMED duplicate=false
WARN  notification_remote_failure eventId=<uuid> attempt=1 reason=HttpTimeoutException
ERROR notification_deferred eventId=<uuid> recovery=outbox_retry
INFO  notification_delivered eventId=<uuid>
ERROR health_database_down reason=CannotGetJdbcConnectionException
```

The notification logs do not include recipients, message bodies, passwords, or shared tokens. Log event IDs and appointment IDs for diagnosis. The application error handler reports unexpected request failures separately.

Failure/recovery example: the notification service goes down after a booking commits. Bounded attempts fail, the appointment remains BOOKED, and the outbox retains the confirmation. Once the receiver is available, the scheduled worker delivers that same event and marks it delivered. If the receiver accepted a previous attempt but the response was lost, its saved receipt prevents accepting a duplicate as a new event. Permanently invalid requests remain pending for operator correction; there is no delivery-failure dashboard in this milestone.

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
- `notification/`: DTO, HTTP gateway, after-commit listener, persistent outbox, recovery worker, mock receiver.
- `controller/HealthController.java`: database-connectivity probe.
- `resources/db/migration/`: non-destructive PostgreSQL schema and one-time demo data.

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

Milestone 3 adds an actual unavailable-HTTP-service booking test, timeout and HTTP 503 fallback tests, queued-event recovery, after-commit visibility, cancellation notifications, no notification on rollback, receiver idempotency, internal-token checks, metrics permissions, and database-health failure tests. Most tests use H2 and need no Docker. Outbound notification delivery is disabled by default in tests except the explicit notification integration tests.

`PostgresPersistenceTest` runs only when `PG_TEST_URL` points to a **disposable test database**; it creates booking data and checks PostgreSQL migrations/cancellation/rebooking. Supply `DB_PASSWORD` and `NOTIFICATION_TOKEN` as environment variables too. It is skipped without that database, not counted as locally verified PostgreSQL coverage. Do not point it at an existing personal/deployed database.

`.github/workflows/build.yml` runs on pushes and pull requests: Java 17/Maven verification with a disposable PostgreSQL service, then a Docker Compose build/start and health check. It stops the containers afterward. The workflow only runs on GitHub after you push the changes; no deployment or push is performed by running tests locally.

Implementation references: [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) and [after-completion resource semantics](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html).

Reports and the read-aloud video script are maintained outside this repository. The report/video/submission ZIP are separate deliverables; this repository does not claim they are completed.
