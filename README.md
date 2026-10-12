# Family chama

A web app that replaces WhatsApp and Excel for running a family chama: member
contributions, the MMF savings pool, internal member loans (guarantors,
tiered interest, default handling) and the monthly merry-go-round.

## Documents

| Document | What it is |
|---|---|
| [Build brief](docs/chama-build-brief.md) | Source of truth for the domain rules |
| [Decisions](docs/decisions.md) | Answers to the brief's open questions, and deviations from it |
| [Plan](docs/plan.md) | Build phases |
| [Dashboard design](docs/Family_chama_dashboard_design.pdf) | Mockup for layout and tone (the brief wins where they differ) |
| [Loan policy](docs/chama-loan-policy-brief.pdf) | The loan policy as shared with the group |

## Stack

Java 17, Spring Boot 4, Thymeleaf, Spring Data JPA, Flyway, PostgreSQL,
Spring Security (form login, BCrypt). Tests use JUnit 5 and Testcontainers.

## Running locally

Needs Java 17+ and Docker.

```
copy .env.example .env        # then edit the values
docker compose up -d          # Postgres on port 5433
.\mvnw spring-boot:run        # http://localhost:8080
```

Log in with `ADMIN_USERNAME` / `ADMIN_PASSWORD` from `.env`. That account is
created the first time the app starts against an empty database.

## Tests

```
.\mvnw test
```

Docker must be running: tests start their own throwaway Postgres container.

## Data

No real member data or secrets are committed. Members are entered through the
app; credentials come from environment variables.
