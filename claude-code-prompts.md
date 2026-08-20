# Claude Code — Session Prompts

Paste the relevant prompt at the start of each session. `CLAUDE.md` is read
automatically, so there is no need to restate the design.

---

## Day 2 — Domain model and schema

```
Read CLAUDE.md first.

Today is Day 2: the domain model and database schema. Work on a branch called
feature/domain-model.

Before writing anything, read docs/diagrams/02-class-diagram.puml and
docs/diagrams/06-er-diagram.puml — they are authoritative for entity structure and
constraints.

Set up:

1. Parent pom.xml with the three modules (sdcms-common, sdcms-server, sdcms-client),
   Java 17, Spring Boot 3.2 parent.
2. sdcms-common: the enums (Role, AppointmentStatus, PaymentStatus, Gender).
3. sdcms-server: JPA entities matching the class diagram — User (abstract, with
   Administrator/Receptionist/Dentist), Patient, Appointment, Treatment, Bill,
   AuditLog.
4. schema.sql with the full DDL, including the composite UNIQUE constraint on
   (dentist_id, appointment_date, appointment_time) and the indexes from the ER
   diagram.
5. data.sql with seed data: 3 users (one per role), 3 dentists, 8 treatments with
   realistic LKR prices, 5 patients.

Constraints to respect: money is BigDecimal/DECIMAL(10,2); Patient stores
dateOfBirth and derives age; use the Builder pattern for Appointment.

Work incrementally and commit as you go using the convention in CLAUDE.md. Explain
your reasoning for the inheritance mapping strategy you choose for User — I need to
be able to defend it.
```

---

## Day 3 — Persistence layer, tests first

```
Read CLAUDE.md.

Day 3: repositories, on branch feature/persistence. This is the first test-first day
and the commit order matters — it is assessed evidence of TDD.

For each repository, in this order:
1. Write the failing test, run it, commit it with a test(...) message.
2. Implement until green, commit with a feat(...) message.
3. Refactor if needed, commit separately.

Repositories needed: PatientRepository, DentistRepository, TreatmentRepository,
AppointmentRepository, BillRepository, UserRepository — all extending JpaRepository
via a shared generic contract where sensible.

The critical query is the availability check on AppointmentRepository. Write a test
that proves a second booking for the same dentist/date/time is rejected, and a
separate test proving the database constraint holds even if the service check is
bypassed.

Use @DataJpaTest with an H2 in-memory database for speed, but verify the UNIQUE
constraint against real MySQL — H2 and MySQL differ on constraint behaviour and I
need the real one proven.
```

---

## Day 4 — Authentication

```
Read CLAUDE.md.

Day 4: authentication and authorisation, branch feature/authentication. Test-first
as before.

Build:
- BCrypt password hashing (never store plaintext)
- JWT issuance on successful login, 30-minute expiry, encoding userId and role
- Spring Security filter chain validating the token on every request
- @PreAuthorize on controller methods per the role table in CLAUDE.md
- Account lockout: 3 failed attempts → 15-minute lock

Tests to write first: valid login returns a token; invalid password returns 401;
third consecutive failure locks the account; a locked account rejects even correct
credentials; an expired token is refused; a RECEPTIONIST token is refused on an
admin-only endpoint.

Follow docs/diagrams/03-sequence-login.puml for the interaction flow.
```

---

## Day 5 — Appointment booking

```
Read CLAUDE.md and docs/diagrams/04-sequence-appointment.puml.

Day 5: appointment booking, branch feature/appointment-booking. This is the core of
the system — the double-booking prevention is the single most important behaviour.

Test-first. The tests that matter:
- Booking a free slot succeeds and returns APT-YYYYMMDD-NNNN format
- Booking an occupied slot throws SlotUnavailableException
- Two concurrent bookings for the same slot: exactly one succeeds
- Booking outside 08:00–20:00 is rejected
- Booking more than 90 days ahead is rejected
- An existing patient is reused, not duplicated, when contact number matches
- Cancelling more than 2 hours ahead succeeds; inside 2 hours is rejected

Implement AppointmentServiceImpl with the Builder pattern for construction and the
Observer pattern for post-booking notification. Keep both the service-level
availability check and the database constraint, and catch
DataIntegrityViolationException to translate the constraint violation into
SlotUnavailableException.

Write the concurrency test properly — two threads, a latch, assert exactly one
success. A test that merely calls the method twice sequentially does not prove
anything about the race.
```

---

## Day 6 — Billing

```
Read CLAUDE.md and docs/diagrams/05-sequence-billing.puml.

Day 6: billing engine, branch feature/billing. Test-first.

Implement the Strategy pattern: IBillingStrategy with StandardBilling,
SeniorCitizenBilling and InsuranceBilling. BillingService selects the strategy and
delegates — it must contain no pricing arithmetic itself.

Calculation order is fixed and matters: (treatment base cost + consultation fee),
then discount, then 8% VAT on the discounted subtotal. Applying VAT before discount
gives a different and wrong total.

Tests first, including: a patient aged exactly 65 receives the discount; aged 64 does
not; totals round to 2dp HALF_UP; the same inputs always produce the same total; a
bill cannot be generated twice for one appointment.

Then iText 7 PDF receipt generation. Include clinic name, appointment number, patient
name, dentist, treatment, itemised amounts, and total in LKR.

All arithmetic in BigDecimal with explicit RoundingMode.
```

---

## Day 7 — REST API

```
Read CLAUDE.md — the REST contract table is authoritative for endpoints and status
codes.

Day 7: controllers, branch feature/rest-api.

Build the controllers per that table, with:
- DTOs in sdcms-common, never exposing entities
- @Valid bean validation on request bodies
- A @RestControllerAdvice global exception handler mapping each domain exception to
  its status code
- Consistent error response body: timestamp, status, message, path

Status codes must match the table exactly — 409 for an occupied slot, 422 for a late
cancellation. Write MockMvc tests asserting the status code for each failure path,
not just the happy path.
```

---

## Day 8 — JavaFX client

```
Read CLAUDE.md.

Day 8: JavaFX desktop client, branch feature/javafx-client.

Screens: Login, Main Menu (role-dependent), Register Appointment, Search
Appointment, Generate Bill, Reports, Help. MVC structure — FXML for layout,
controller classes for logic.

Client-side validation on every input field: required fields, contact number format,
date not in the past, time within clinic hours. Show errors inline next to the field,
not in a dialog.

Important: client validation is a convenience, not a control. The server validates
independently and the client must handle a server rejection gracefully even when its
own checks passed.

RestApiClient wraps java.net.http.HttpClient with Jackson, attaches the JWT bearer
token, and surfaces HTTP errors as typed exceptions the view controllers can handle.

The main menu must show only the options the logged-in role is permitted to use.
```

---

## Day 9 — Reports

```
Read CLAUDE.md.

Day 9: reporting module, branch feature/reporting.

Six reports:
1. Daily Appointment Schedule — today's bookings by time, with dentist and patient
2. Dentist Workload — appointments per dentist over a date range
3. Revenue Report — daily and monthly totals, with tax and discount breakdown
4. Treatment Popularity — count and revenue by treatment type
5. Patient Visit History — all appointments for one patient
6. No-Show and Cancellation — rate by dentist and by period

Each needs a REST endpoint, a service method, and a JavaFX screen. Export to PDF via
iText.

Restrict revenue reports to ADMINISTRATOR. Write the authorisation test for that.
```

---

## Day 10 — Integration and release

```
Read CLAUDE.md.

Day 10: integration and v1.0.0 release.

- Run the full suite; fix anything failing
- Generate a JaCoCo coverage report and tell me the figure honestly, including which
  areas are weakest
- End-to-end integration test: login → book → bill → report
- Update README with actual setup steps, verified by following them yourself
- Merge develop into main with --no-ff, tag v1.0.0

Then give me a summary of what was built, what was left out, and any known
limitations. I need an accurate list for the report's conclusion — do not overstate
completeness.
```

---

## Useful mid-session prompts

**When something breaks:**
```
Explain what went wrong and why before fixing it. I need to understand the failure,
not just see it disappear.
```

**Before accepting a large change:**
```
Walk me through this as if I were defending it in a viva. What would a marker
question?
```

**When it suggests something outside the design:**
```
CLAUDE.md specifies X. You have proposed Y. Explain the trade-off and let me decide
rather than choosing for me.
```
