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

## Neysa AI Usage

I used Claude Code mainly for checking my scaffolding of the domain model and constraints, generating tests,
and debugging build/infrastructure issues (a missing validation dependency, missing Testcontainers dependencies).

**Domain model.** Claude Code built out `ItineraryItem` and its subclasses iteratively — adding `title`/`notes` once I confirmed
(by checking the DTOs) all three item types needed them, then `id` once persistence was being designed. I made the call on where each
field lived (e.g. `category` on `Activity` only, not the shared base).

**`TravelTimeConstraint`.** This took the most iteration. Claude Code caught that measuring travel time to an item's `location`
would misfire whenever the second item was a `Transportation` leg, since `location` there is the destination, not where the
traveler currently is — we fixed this by measuring to its `origin` instead. I also decided `TravelTimeConstraint` should skip
pairs `OverlapConstraint` already flagged, rather than double-reporting the same problem two ways.

**Review process.** I caught a naming mismatch between a new `Violation` record and the existing `ConflictResponse` DTO and renamed it to
`Conflict` before it caused problems later. I asked Claude Code to confirm a specific edge case (two back-to-back items with zero gap) was
actually tested — it wasn't, so I had it add a test for it. I made the scope calls Claude Code flagged but didn't resolve on its own, like
deferring overnight operating hours as a known limitation.


## Ansh AI Usage
I mainly used AI to navigate the unfamiliar (to me) Spring framework and the Google Routes API, and write tests.

My assistant of choice was Claude Code, mostly running Opus 5.5 on high or on max.

The GoogleRoutesService and all of the controllers and DTOs were substantially assisted by Claude Code.

I always have Claude a plan on what I wanted it to implement, and, before it wrote any code,
I would have it repeat the plan back to me in a step-by-step list to make sure it was planning
on doing exactly what I wanted it to. After it wrote the code I approved it to write, I would read
through the code, and ask it clarifying questions about how it wrote to the code and choices that it made.