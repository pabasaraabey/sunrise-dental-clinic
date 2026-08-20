# SDCMS — Project Context for Claude Code

Read this before making changes. It records decisions already taken; do not revisit
them without asking.

## What this is

Sunrise Dental Clinic Management System — a distributed appointment and patient
management system replacing a paper process that produced double bookings, lost
records and billing errors. This is university coursework (ICBT, Semester 5) and is
assessed on design quality, testing discipline and version control practice as much
as on working code.

## Stack — fixed, do not substitute

| Layer | Technology |
| :--- | :--- |
| Language | Java 17 |
| Server | Spring Boot 3.2 (Web, Data JPA, Security, Validation) |
| Client | JavaFX 21 with FXML |
| Database | MySQL 8.0 |
| Build | Maven, multi-module |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| PDF | iText 7 |
| Auth | BCrypt + JWT (jjwt) |

Modules: `sdcms-common` (DTOs, enums), `sdcms-server`, `sdcms-client`.

## Non-negotiable rules

1. **Money is `BigDecimal` and `DECIMAL(10,2)`.** Never `float` or `double`. Always
   specify `RoundingMode.HALF_UP` explicitly. Floating-point drift is the exact
   billing defect this system exists to remove.
2. **Double booking is prevented at two levels.** A composite
   `UNIQUE (dentist_id, appointment_date, appointment_time)` in MySQL *and* a
   service-layer availability check. Keep both: the check gives a clean error in the
   common case, the constraint closes the race window where two clients pass the
   check before either commits.
3. **Tests come first.** Write the failing test, commit it, then implement. The
   commit order is assessed evidence — do not batch tests after implementation.
4. **No credentials in the repository.** Secrets read from environment variables.
   `application-local.properties` is gitignored.
5. **Never commit to `main` directly.** Work on `feature/*`, merge to `develop`,
   release to `main` with `--no-ff`.
6. **Controllers return DTOs, never JPA entities.** Entities leak lazy associations
   and fields like `passwordHash`.

## Domain rules

- Appointment numbers are system-generated: `APT-YYYYMMDD-NNNN`.
- Clinic hours 08:00–20:00, 30-minute slots.
- Consultation fee: LKR 1,500.00 flat, added to every bill.
- VAT: 8%, applied *after* discount.
- Senior citizens (65+, derived from `dateOfBirth`, never stored): 10% discount.
- Cancellation permitted up to 2 hours before; status becomes `CANCELLED`, record is
  retained — never hard-deleted.
- Password policy: min 8 chars, one uppercase, one digit, one special character.
- Account locks for 15 minutes after 3 failed logins.
- Age is always derived from `dateOfBirth`. Never store an `age` column.

## Roles

| Role | Permissions |
| :--- | :--- |
| `ADMINISTRATOR` | Manage users, dentists, treatment prices; all reports |
| `RECEPTIONIST` | Register patients, book/search/cancel appointments, billing |
| `DENTIST` | View own schedule and patient history — read only |

Enforce with `@PreAuthorize` at controller methods.

## Design patterns — implement these specifically

| Pattern | Where | Why |
| :--- | :--- | :--- |
| Strategy | `IBillingStrategy` → Standard / SeniorCitizen / Insurance | Pricing rules change |
| Builder | `Appointment.Builder` | 8 fields; prevents parameter transposition |
| Factory Method | `TreatmentFactory` | Centralised instantiation |
| Singleton | Connection pool (via Spring beans) | Bounded resources |
| Repository | All data access | Testability |
| Observer | Booking confirmation | Notification channels added without edits |
| MVC | JavaFX client | Layout separate from logic |

Do not add patterns beyond these. Unmotivated indirection is worse than plain code.

## REST contract

| Method | Endpoint | Success | Failure |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | 200 | 401 |
| `POST` | `/api/patients` | 201 | 409 duplicate contact |
| `GET` | `/api/patients/{id}` | 200 | 404 |
| `GET` | `/api/appointments/{aptNo}` | 200 | 404 |
| `POST` | `/api/appointments` | 201 | 409 slot taken |
| `GET` | `/api/appointments/availability` | 200 | 400 |
| `PATCH` | `/api/appointments/{aptNo}/cancel` | 200 | 422 inside 2h window |
| `POST` | `/api/bills/generate/{aptNo}` | 201 | 404 |
| `GET` | `/api/bills/{billId}/pdf` | 200 | 404 |
| `GET` | `/api/reports/daily-schedule` | 200 | 403 |

409 for an occupied slot, not 400 — the request is valid but conflicts with resource
state. 422 for a late cancellation — well-formed, but a business rule forbids the
transition.

## Commit convention

```
<type>(<scope>): <subject>

<body — why, not what>
```

Types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.
The body explains reasoning. The diff already shows what changed.

## Build order

| Day | Branch | Deliverable | Tag |
| :--- | :--- | :--- | :--- |
| 2 | `feature/domain-model` | Entities, enums, schema.sql, seed data | `v0.2.0` |
| 3 | `feature/persistence` | Repositories + tests first | — |
| 4 | `feature/authentication` | BCrypt, JWT, `@PreAuthorize` | `v0.3.0` |
| 5 | `feature/appointment-booking` | Availability, Builder, constraint | `v0.4.0` |
| 6 | `feature/billing` | Strategies, BigDecimal, iText | `v0.5.0` |
| 7 | `feature/rest-api` | Controllers, DTOs, exception handler | — |
| 8 | `feature/javafx-client` | FXML screens, validation | `v0.6.0` |
| 9 | `feature/reporting` | Six reports | `v0.9.0` |
| 10 | `develop` → `main` | Integration, polish | `v1.0.0` |

## Reference documents

- `docs/diagrams/` — use case, class, sequence, ER, architecture (PlantUML + SVG)
- `docs/version-control-strategy.md`
- `docs/development-plan.md`

The class diagram and ER diagram are authoritative for entity structure. If code and
diagram diverge, say so rather than silently changing either.

## Working style

- Explain reasoning briefly before non-obvious changes — this is coursework and the
  author must be able to defend every decision in a viva.
- Prefer clear code over clever code.
- Flag anything that looks like it contradicts this document rather than working
  around it.
