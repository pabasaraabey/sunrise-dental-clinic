# Sunrise Dental Clinic Management System (SDCMS)

A distributed appointment and patient management system for a private dental clinic,
replacing a paper-based process that suffered from double bookings, lost records and
billing errors.

## Problem

Sunrise Dental Clinic records appointments manually in paper files and notebooks.
Because each record exists in one physical location, it cannot be consulted
concurrently — producing double bookings, lost patient records, long waiting times
and billing errors. This system centralises clinic records on a shared server so that
multiple workstations read and write one authoritative data store.

## Architecture

Three-tier client–server:

| Tier | Technology |
| :--- | :--- |
| Presentation | JavaFX 21 desktop client |
| Application | Spring Boot 3.2 REST web service (Java 17) |
| Data | MySQL 8.0 |

Communication is RESTful JSON over HTTPS, authenticated with stateless JWT bearer
tokens.

## Modules

```
sdcms-common/   Shared DTOs, enums, validation constants
sdcms-server/   Spring Boot REST API, business logic, persistence
sdcms-client/   JavaFX desktop client
docs/           UML diagrams (PlantUML source + rendered SVG)
```

## Key Design Decisions

- **Monetary values use `BigDecimal` / `DECIMAL(10,2)`**, never floating point.
  Binary floating point cannot represent decimal fractions exactly, and the resulting
  drift is precisely the class of billing error this system exists to remove.
- **Double booking is prevented by a database constraint**, not only by application
  logic. A composite `UNIQUE (dentist_id, appointment_date, appointment_time)` closes
  the race condition that occurs when two workstations book the same slot
  concurrently — an application-level check alone cannot, because check-then-act is
  not atomic.
- **Appointment numbers are system-generated** in the format `APT-YYYYMMDD-NNNN`,
  removing manual entry of a value required to be unique.
- **Role-based access control** across Administrator, Receptionist and Dentist,
  applying least privilege and separation of duties.

## Design Patterns

| Pattern | Applied in | Purpose |
| :--- | :--- | :--- |
| Strategy | `BillingService` | Pricing rules extensible without modification |
| Builder | `Appointment` | Prevents parameter transposition; enforces invariants |
| Factory Method | `TreatmentFactory` | Centralised instantiation |
| Singleton | Connection pool | Bounded resource consumption |
| Repository | Data access | Testability; storage independence |
| Observer | Booking confirmation | Notification channels added without edits |
| MVC | JavaFX client | Layout separated from logic |

## Build and Run

Prerequisites: JDK 17+, Maven 3.9+, MySQL 8.0.

```bash
# Create the schema
mysql -u root -p < sdcms-server/src/main/resources/schema.sql

# Run the server
cd sdcms-server && mvn spring-boot:run

# Run the client (separate terminal)
cd sdcms-client && mvn javafx:run
```

Server defaults to `https://localhost:8443`.

## Testing

```bash
mvn test                 # unit tests
mvn verify               # unit + integration tests
mvn jacoco:report        # coverage report → target/site/jacoco/
```

Developed test-first: each behaviour has a failing test committed before its
implementation. See `docs/test-plan.md`.

## Documentation

| Document | Location |
| :--- | :--- |
| UML diagrams | `docs/diagrams/` |
| Version control strategy | `docs/version-control-strategy.md` |
| Test plan | `docs/test-plan.md` |

## Licence

Academic coursework submission.
