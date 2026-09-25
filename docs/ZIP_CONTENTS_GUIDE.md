# ZIP Contents Guide and Milestone 1 Crosswalk

**ZIP:** `CMPE172_Milestone1_Newton_Tran.zip`  
**Project:** Community Health Clinic Appointment System  
**Student:** Newton Tran  
**Student ID:** 015110368

The Java code intentionally uses the short personal namespace `clinic`. Therefore, the useful source packages appear directly under `src/main/java/clinic` instead of a school-specific `edu/sjsu/cmpe172` path.

## What the guideline asks for

The Milestone 1 PDF requires a proposal, database design, wireframes, a running layered JDBC skeleton, a README, a report discussion of request flow and controller patterns, and a minimum five-minute code-walkthrough video. The ZIP contains the local artifacts. The GitHub URL and video URL must be added by you after you publish and record them.

## Do you need to run anything?

Yes, before submitting you should run the application once and run the tests. The guideline calls this a “running skeleton,” and the video is supposed to explain working code. You do not need to implement the later booking, login, notification, or AI features for Milestone 1. You only need to verify the two read endpoints and the included tests.

From the unzipped project folder, use:

```bash
mvn spring-boot:run
```

In a second terminal, use:

```bash
curl http://localhost:8080/
curl "http://localhost:8080/slots?providerId=1&date=2026-10-05&page=0&size=1"
```

Then stop the app and run:

```bash
mvn test
```

You need Java 17 or newer and Maven 3.9 or newer. If Maven is not installed, install it or open the project in an IDE with Maven support. You should not claim the tests passed until you have actually run them on your machine.

## Exact ZIP path map

| Guideline requirement | Exact path inside the ZIP | What to open |
|---|---|---|
| Milestone 1 Report PDF | `output/pdf/CMPE172_Milestone1_Report_Newton_Tran.pdf` | The 11-page report. It contains the scenario, roles, all 12 features, entities, architecture block diagram, ER/schema justification, request flow, and Page Controller vs Front Controller explanation. |
| ER/EER diagram | `output/pdf/CMPE172_Milestone1_ER_Diagram_Newton_Tran.pdf` | One-page diagram showing users, providers, services, availability_slots, appointments, cardinalities, keys, and integrity rules. |
| Relational schema | Inside the report, section 5; implementation source at `src/main/resources/schema.sql` | Section 5 is the submission-friendly schema table. `schema.sql` is the executable definition. |
| Wireframe: Home | Page 1 of `output/pdf/CMPE172_Milestone1_Wireframes_Newton_Tran.pdf` | Clinic landing screen and browse action. |
| Wireframe: View available slots | Page 2 of the wireframes PDF | Provider, service, date filters, slot cards, and pagination. |
| Wireframe: Book appointment form | Page 3 of the wireframes PDF | Selected visit summary, patient contact fields, confirmation checkbox, and booking action. |
| Wireframe: Confirmation | Page 4 of the wireframes PDF | Confirmation number, visit details, notification status, and next actions. |
| Controller layer | `src/main/java/clinic/controller/ClinicController.java` | Defines `GET /` and `GET /slots`. |
| Service layer | `src/main/java/clinic/service/ClinicService.java` | Validates pagination, calculates offsets, and coordinates repository calls. |
| Repository/JDBC layer | `src/main/java/clinic/repository/ClinicRepository.java` | Executes parameterized SQL, filters slots, applies `LIMIT` and `OFFSET`, and maps rows to DTOs. |
| DTOs | `src/main/java/clinic/dto/` | `HomeDto`, `SlotPageDto`, `AvailabilitySlotDto`, `ProviderDto`, `ServiceDto`, and `ErrorDto`. |
| Spring Boot entry point | `src/main/java/clinic/ClinicApplication.java` | Starts the application. |
| SQL tables and constraints | `src/main/resources/schema.sql` | Creates all five required tables, keys, foreign keys, checks, indexes, and the double-booking guard. |
| Sample providers, services, and slots | `src/main/resources/seed.sql` | Loads two providers, four services, a customer, an administrator, six available slots, and one blocked slot. |
| Startup configuration | `src/main/resources/application.properties` | H2 in-memory database, automatic SQL initialization, H2 console, and JSON date settings. |
| Integration tests | `src/test/java/clinic/ClinicControllerIntegrationTest.java` | Tests home data, filtered/paginated slots, and invalid page size handling. |
| How to run | `README.md` | Java/Maven requirements, commands, endpoint examples, architecture, and final submission steps. |
| Written report source | `docs/Milestone1_Report_Source.md` | Editable text source for the report narrative. |
| Short walkthrough outline | `docs/VIDEO_WALKTHROUGH_SCRIPT.md` | Concise timing guide for the required video. |
| Word-for-word walkthrough | `docs/VIDEO_WALKTHROUGH_WORD_FOR_WORD.md` | Read-aloud script for the video. |
| ZIP and submission checklist | `SUBMISSION_CHECKLIST.md` | What is complete and what you still must do before submitting. |

## The five database entities

1. `users`: customer, provider, and administrator accounts.
2. `providers`: provider profile tied one-to-one to a user.
3. `services`: services offered by a provider.
4. `availability_slots`: provider/service-specific open time windows.
5. `appointments`: customer reservations for slots.

## The double-booking protection

Open `src/main/resources/schema.sql` and search for:

```sql
CONSTRAINT uq_appointments_slot UNIQUE (availability_slot_id)
```

That database constraint permits at most one appointment row for any availability slot. The report explains why this database rule is authoritative even if two requests arrive at nearly the same time.

## What is not yet inside the ZIP

- The GitHub repository link: create the repository, push the project, and paste the URL into the submission portal.
- The code-walkthrough video link: record the required minimum five-minute explanation and paste the URL into the submission portal.
- Later-milestone features such as login, booking writes, cancellation, notifications, health/metrics, and AI. The report lists these as roadmap items because Milestone 1 asks for the skeleton only.

## Minimal final submission order

1. Open the report PDF and confirm your name and student ID.
2. Run the application once and confirm `GET /` and `GET /slots` respond.
3. Run `mvn test` and keep the passing result for your own verification.
4. Push the folder to GitHub.
5. Record the video using the word-for-word script.
6. Submit the ZIP, GitHub URL, and video URL.
