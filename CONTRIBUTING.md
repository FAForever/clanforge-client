# Contributing

Thanks for your interest in the Clan Management Platform.

## Getting started

See the [README](README.md) for how to build and run the app and the test suite.

## Workflow

- Branch from `main`; keep each branch to a single concern.
- Open a pull request into `main`. At least one approving review is required to merge.
- CI must pass: `./mvnw test`, `./mvnw spotbugs:check`, and the Docker Compose
  validation all run on every push and pull request.

## Code conventions

- The business rules live in the `service/` layer; controllers stay thin and never talk
  to repositories directly.
- Keep CSS and JavaScript in separate files — the Content-Security-Policy blocks inline
  styles and scripts.
- Escape user data in templates with `th:text`, never `th:utext`.
- Never commit credentials; configuration comes from environment variables
  (see `.env.example`).
- The database schema is owned by Flyway migrations — add a new `V#__*.sql` file rather
  than editing one that has already been applied.

## Tests

Add or update tests for any behaviour you change. The database-backed tests and the
Selenium suite are described in the README and in `selenium_tests/README.md`.
