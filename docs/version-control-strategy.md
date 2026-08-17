# Version Control Strategy

This document records the branching model, commit conventions and release process
used throughout development of SDCMS.

## Branching Model

A simplified GitFlow is used. Full GitFlow defines five branch types; for a project
of this size that ceremony costs more than it returns, so `release/*` and `hotfix/*`
are omitted.

| Branch | Purpose | Merges into |
| :--- | :--- | :--- |
| `main` | Always-deployable. Tagged releases only. | — |
| `develop` | Integration branch. Features land here first. | `main` |
| `feature/*` | One branch per unit of work. | `develop` |

**Rationale.** `main` is never committed to directly. Every change reaches it through
`develop`, which means `main` always reflects a state that has been integrated and
tested. Feature branches keep unrelated work isolated, so an incomplete feature never
blocks a release.

Branch naming follows the work being done:

```
feature/authentication
feature/appointment-booking
feature/billing-strategy
feature/javafx-client
feature/reporting
```

## Commit Message Convention

Commits follow the Conventional Commits specification:

```
<type>(<scope>): <subject>

<body — why, not what>
```

| Type | Used for |
| :--- | :--- |
| `feat` | New capability |
| `fix` | Defect correction |
| `test` | Adding or amending tests |
| `refactor` | Restructuring without behaviour change |
| `docs` | Documentation only |
| `chore` | Build, dependencies, tooling |

Example:

```
feat(appointment): reject bookings for occupied dentist slots

Adds a composite UNIQUE constraint on (dentist_id, appointment_date,
appointment_time) alongside the service-layer availability check. The
application check alone leaves a race window: two concurrent requests can
both pass validation before either commits. The constraint serialises them
at the storage engine.

Closes #12
```

The body explains *why* the change was made. The diff already shows what changed;
what it cannot show is the reasoning, which is the part a future reader needs.

## Test-Driven Development in the History

Because development is test-first, the commit history is itself evidence of the
process. Each behaviour appears as a pair:

```
test(billing): senior citizen discount applies at 65 and above   [RED]
feat(billing): implement SeniorCitizenBilling strategy           [GREEN]
refactor(billing): extract VAT calculation to shared method      [REFACTOR]
```

The failing test is committed before the implementation that satisfies it. This
ordering is visible in `git log` and cannot be reconstructed after the fact.

## Semantic Versioning

Releases are tagged `MAJOR.MINOR.PATCH`:

| Version | Milestone |
| :--- | :--- |
| `v0.1.0` | Project skeleton, UML documentation |
| `v0.2.0` | Domain model and database schema |
| `v0.3.0` | Authentication and role-based access |
| `v0.4.0` | Appointment booking with double-booking prevention |
| `v0.5.0` | Billing engine and PDF receipts |
| `v0.6.0` | JavaFX client |
| `v0.9.0` | Reporting module |
| `v1.0.0` | Complete system |

## Continuous Integration

`.github/workflows/ci.yml` runs on every push and pull request:

1. Checkout
2. Set up JDK 17
3. `mvn clean verify` — compiles and runs the full test suite
4. Publish JaCoCo coverage report

A pull request whose tests fail cannot be merged into `develop`. This makes the test
suite a gate rather than a formality.

## Typical Workflow

```bash
# Start work
git checkout develop
git pull origin develop
git checkout -b feature/billing-strategy

# Test first
git add sdcms-server/src/test/java/.../BillingServiceTest.java
git commit -m "test(billing): senior discount applies at 65 and above"

# Then implement
git add sdcms-server/src/main/java/.../SeniorCitizenBilling.java
git commit -m "feat(billing): implement SeniorCitizenBilling strategy"

# Integrate
git push -u origin feature/billing-strategy
# → open pull request into develop, CI runs, merge on green

# Release
git checkout main
git merge --no-ff develop
git tag -a v0.5.0 -m "Billing engine and PDF receipts"
git push origin main --tags
```

`--no-ff` is used deliberately on merges to `main`. A fast-forward merge would erase
the fact that the work happened on a branch; `--no-ff` preserves that structure in
the history graph, which is what makes `git log --graph` readable as a record of how
the project developed.
