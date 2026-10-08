# ItinerAI

## Running locally

Requires Java 25 and Docker Desktop (running). All commands are run from the `ItinerAI/` folder.

```bash
docker compose up -d
./mvnw spring-boot:run
```

`docker compose up -d` starts a local PostgreSQL that matches the defaults in `application.properties`. To use a different database, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

### Google Routes API key

Travel times come from the Google Routes API. The easiest way to give the app your key is a file named `.env` in the `ItinerAI/` folder (next to `pom.xml`) containing one line:

```
GOOGLE_MAPS_API_KEY=your-key
```

`.env` is git-ignored, so it is never committed. Alternatively, set `GOOGLE_MAPS_API_KEY` as an environment variable in your shell or in the IntelliJ run configuration; an environment variable wins over `.env`.

Never put a real key in `application.properties` or commit it. Without a key the app still starts with a placeholder, but travel-time lookups fail with "API key not valid". CI gets the key from the `GOOGLE_ROUTES_API_KEY` repository secret.

## Tests

```bash
./mvnw verify
```

Tests start their own throwaway PostgreSQL through Testcontainers, so Docker must be running, but `docker compose` is not needed.

## Database migrations

The schema is managed by Flyway. Add migrations to `ItinerAI/src/main/resources/db/migration` named `V1__description.sql`, `V2__description.sql`, and so on. Never edit a migration that has already been merged; add a new one instead.
