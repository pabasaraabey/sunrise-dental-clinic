# Development Plan

Ten working days, one release tag per milestone. Commits are made on the day the work
is done — the history is part of the deliverable and cannot be reconstructed later.

| Day | Branch | Work | Tag |
| :--- | :--- | :--- | :--- |
| 1 | `main` | Repository skeleton, UML diagrams, CI pipeline, documentation | `v0.1.0` |
| 2 | `feature/domain-model` | JPA entities, enums, MySQL schema, seed data | `v0.2.0` |
| 3 | `feature/persistence` | Repository interfaces; repository tests written first | — |
| 4 | `feature/authentication` | BCrypt hashing, JWT issuance, role-based authorisation | `v0.3.0` |
| 5 | `feature/appointment-booking` | Availability checking, Builder, double-booking constraint | `v0.4.0` |
| 6 | `feature/billing` | Strategy implementations, `BigDecimal` arithmetic, iText receipts | `v0.5.0` |
| 7 | `feature/rest-api` | Controllers, DTO mapping, exception handling, status codes | — |
| 8 | `feature/javafx-client` | FXML screens, view controllers, client-side validation | `v0.6.0` |
| 9 | `feature/reporting` | Six operational reports, export | `v0.9.0` |
| 10 | `develop` → `main` | Integration testing, README, final polish | `v1.0.0` |

## Daily Rhythm

Each day follows the same loop:

```bash
git checkout develop && git pull
git checkout -b feature/<name>

# RED — write the failing test, commit it
git commit -m "test(<scope>): <behaviour being specified>"

# GREEN — implement until it passes, commit
git commit -m "feat(<scope>): <what now works>"

# REFACTOR — tidy without changing behaviour, commit
git commit -m "refactor(<scope>): <what was cleaned up>"

git push -u origin feature/<name>
# open PR into develop → CI runs → merge on green
```

Three to six commits per day is the realistic target. Fewer suggests work is being
batched; many more suggests commits that aren't self-contained.

## Non-Negotiables

- **Never commit directly to `main`.** Every change arrives via `develop`.
- **Test before implementation.** The ordering is visible in `git log` and is the
  evidence that TDD was actually practised.
- **No credentials in the repository.** `application-local.properties` is ignored;
  the committed `application.properties` reads secrets from environment variables.
- **Commit on the day the work happens.** Backdated history is detectable and the
  brief explicitly requires daily progression.
