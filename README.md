# ItinerAI

## Running locally

Requires Java 25 and Docker Desktop (running). All commands are run from the `ItinerAI/` folder.

```bash
docker compose up -d
./mvnw spring-boot:run
```

`docker compose up -d` starts a local PostgreSQL that matches the defaults in `application.properties`. To use a different database, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

## Tests

```bash
./mvnw verify
```

Tests start their own throwaway PostgreSQL through Testcontainers, so Docker must be running, but `docker compose` is not needed.

## Database migrations

The schema is managed by Flyway. Add migrations to `ItinerAI/src/main/resources/db/migration` named `V1__description.sql`, `V2__description.sql`, and so on. Never edit a migration that has already been merged; add a new one instead.
