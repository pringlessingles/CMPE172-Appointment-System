# Doctor Appointment System

CMPE 172 Milestones 1 through 3 project by Newton Tran.

Spring Boot with Thymeleaf pages, Spring Security session login, and plain JDBC SQL. No JPA or Hibernate. Patients browse, book, and cancel appointments; doctors manage their availability and view their appointments.

## Run on Windows

Use **JDK 21** (not Java 17 or 25), and set `JAVA_HOME` to the JDK 21 folder. Maven checks this requirement. Normal application runs now require persistent **PostgreSQL**; H2 is confined to automated tests.

The simplest supported startup uses Docker Desktop with Linux containers:

```powershell
Copy-Item .env.example .env
# Replace BOTH placeholders in .env. Do not overwrite an existing .env.
docker compose up --build -d --wait
```

Open [http://localhost:8080](http://localhost:8080). Stop an old application listening on port 8080 before starting the containers. On another computer, copy your local settings separately; `.env` and database contents are not part of a Git clone.

If PostgreSQL is already installed locally, create an empty `appointments` database and an account allowed to create the schema and `btree_gist` extension. Set the connection values in the same PowerShell session before starting:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/appointments'
$env:DB_USER = 'appointments'
$env:DB_PASSWORD = 'your-local-database-password'
$env:NOTIFICATION_TOKEN = 'your-local-notification-token'
.\mvnw.cmd spring-boot:run
```

Spring does not automatically load `.env` when you use Maven directly; Compose does. The wrapper downloads Maven/dependencies on its first run. On macOS/Linux use `bash mvnw spring-boot:run` with the equivalent environment variables.

## Demo accounts

All demo accounts use password `Appointment123!`.

| Account | Email | Database role |
| --- | --- | --- |
| Patient | sofia.reyes@example.com | CUSTOMER |
| Second patient | alex.morgan@example.com | CUSTOMER |
| Doctor Maya Chen | maya.chen@doctor.example | PROVIDER |
| Doctor Jordan Lee | jordan.lee@doctor.example | PROVIDER |

Passwords in `seed.sql` are BCrypt hashes. Roles come from the database, not the submitted booking form. The seeded ADMIN account has no administrative UI in this milestone.

**Persistent database:** Both normal Maven startup and Compose use PostgreSQL without resetting existing records. Old in-memory H2 demo sessions are not automatically transferred into PostgreSQL. This is a coursework demo, not a system for real patient information. H2 and its resetting schema/seed scripts are used only in tests, never in the packaged runtime.

## Milestone 3 Docker deployment

Requires Docker Desktop with Linux containers (or Docker Engine with Compose on Linux).

```powershell
Copy-Item .env.example .env
# Edit .env and replace BOTH placeholder values before starting.
docker compose up --build -d --wait
```

Open http://localhost:8080. Check health with `curl.exe http://localhost:8080/health`.

Stop with `docker compose down`. The named `appointment-data` volume keeps the database after stopping/recreating containers. Do not use `down -v` unless you intend to delete the stored database. Never commit `.env`.

PostgreSQL is the default, and the `postgres` profile remains available for Compose/explicit configuration. Flyway applies `V1__appointment_system.sql`, `V2__demo_accounts_and_slots.sql`, and `V3__prevent_doctor_schedule_overlaps.sql` once and validates them on subsequent starts. Existing appointments, notification receipts, and queued events remain. Initial demo slots eventually become past appointments; doctors can create new slots.

V3 adds the PostgreSQL `btree_gist` extension and an exclusion constraint. The migration deliberately fails if existing active slots overlap; it does not delete or rewrite those appointments. Review and resolve any old scheduling conflicts explicitly before retrying. Your database migration account must be allowed to install this extension.

### Doctor schedule overlap protection

Sessions for the same doctor cannot overlap, including different services. For example, 09:00-09:30 conflicts with 09:15-09:45, but 09:30-10:00 is allowed immediately afterward. Different doctors may work at the same time. BLOCKED slots do not reserve time, while AVAILABLE and BOOKED slots do.

`BookingService` locks the doctor's provider row before changing the schedule. While holding that lock, it checks `existing.starts_at < requested.end AND existing.ends_at > requested.start`. Concurrent requests for one doctor are serialized; different doctors do not share that lock. Booking also checks for overlapping booked legacy slots, and the original slot-version check still protects stale requests. PostgreSQL independently enforces active-slot non-overlap with a half-open timestamp-range exclusion constraint, including writes that bypass the service.

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

There is no automatic retry for a booking conflict: the patient must choose again after refreshing availability. Retrying stale intent automatically could book a slot after someone cancelled it. READ_COMMITTED, per-doctor row locking, conditional version updates, and database constraints protect scheduling operations; this does not make every query serializable. Normal application commits are stored in PostgreSQL and survive application/database restarts when its volume is retained.

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

`PostgresPersistenceTest` uses `PG_TEST_URL` when supplied, otherwise it launches a disposable native PostgreSQL process through a test-only dependency. PostgreSQL tests are no longer silently skipped on a machine without Docker. The first test run downloads native database binaries from Maven Central. Windows may require the Visual C++ runtime identified by the embedded PostgreSQL library if `initdb` cannot start. These binaries are not packaged into the application.

`PostgresRestartTest` books an appointment, adds an account, closes the app and database, then reopens both using the same test-only data directory. It verifies that the booking, account, queued notification, and migration history survived. Scheduling tests cover partial/contained overlaps, adjacent sessions, different doctors, concurrent requests, and direct database constraint violations.

Never point `PG_TEST_URL` at personal/deployed data: tests clear and create records in that database. Use only a disposable test database. Without `PG_TEST_URL`, all native PostgreSQL data is test-owned and removed during test cleanup.

`.github/workflows/build.yml` runs on pushes and pull requests: Java 21/Maven verification with a disposable PostgreSQL service, then a Docker Compose build/start and health check. It stops the containers afterward. The workflow only runs on GitHub after you push the changes; no deployment or push is performed by running tests locally.

## Milestone history tags

Annotated tags preserve the original milestone snapshots without rewriting history:

| Tag | Commit | Snapshot |
| --- | --- | --- |
| `milestone-1` | `0d72a40` | Original Milestone 1 state before professor feedback |
| `milestone-2` | `3849e02` | Booking, security, and initial concurrency work |
| `milestone-3` | `f337b94` | Notification, monitoring, and deployment work before feedback corrections |

The Java 21, persistent-default database, and full overlap corrections are later work. The historical tags do not claim those earlier submissions already met the corrected requirements. Tags must be pushed separately when publishing; creating them locally does not upload anything.

Implementation references: [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) and [after-completion resource semantics](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html).

Reports and the read-aloud video script are maintained outside this repository. The report/video/submission ZIP are separate deliverables; this repository does not claim they are completed.
