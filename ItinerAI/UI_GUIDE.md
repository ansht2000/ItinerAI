# ItinerAI web UI guide

Everything the web UI needs from the backend: how to run it, where the UI's files go, every endpoint with its JSON, and how the AI agent works. Everything is due **Nov 12**.

## Status (Nov 10)

| Piece | Where it is |
|---|---|
| Trips, itinerary items, feasibility check | On `main`, ready to use |
| `OUTSIDE_OPERATING_HOURS` conflicts, check on transportation-leg durations | PR 1 (`AT/feature/checker-gaps`), in review |
| AI agent endpoints (`/optimize`, `/optimize/apply`) | PR 2, due Nov 11. Until then, build against the example JSON in [The AI agent](#the-ai-agent). |

## Run the backend

You need Java 25 and Docker Desktop running. From the `ItinerAI/` folder (the one with `pom.xml`):

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

The API is then at `http://localhost:8080`. The keys go in a `.env` file next to `pom.xml`. It's git-ignored; Ansh can share the values privately, and they must never be committed:

```
GOOGLE_MAPS_API_KEY=...
ANTHROPIC_API_KEY=...
```

Without the keys the app still starts and trips and items work, but the feasibility check returns 502 and the agent returns 503.

For sample data, open `requests.http` in IntelliJ and run the requests in section 1. They create the Paris trip used in the demo.

## Where the UI goes

Put the UI's files in `src/main/resources/static/`. Spring Boot serves them from the same server as the API: `index.html` opens at `http://localhost:8080/`, and the UI calls the API with relative URLs such as `fetch("/api/trips")`. No CORS setup is needed.

- **Plain HTML, CSS and JS** need no build step. Spring Boot serves them from the build output, so after editing, rebuild in IntelliJ (Build → Build Project) or restart the app, then refresh the page.
- **A framework with a build step** (React with Vite, for example): develop on its dev server with `/api` forwarded to the backend, and copy the production build into `static/` at the end:

  ```js
  // vite.config.js
  export default { server: { proxy: { "/api": "http://localhost:8080" } } };
  ```

- **Routes:** use hash-based routes (`/#/trips/42`). The backend only serves real files, so reloading a path like `/trips/42` would return 404.

## API basics

- **JSON:** send `Content-Type: application/json` with every request body.
- **Dates:** `"2026-11-01"`.
- **Times:** 24-hour, local to the trip's time zone. Send `"10:00"` (`"10:00:00"` works too). Responses always include seconds (`"10:00:00"`), so show the first five characters. Nothing can run past midnight: an item's end must be after its start on the same day, and opening hours can't cross midnight.
- **Time zones:** IANA names such as `"Europe/Paris"`.
- **Ids:** numbers.
- **Missing values:** item and place responses leave out fields that weren't set or don't apply to the type (`notes`, `category`, `openTime`, ...) rather than sending `null`.
- **Order:** items always come back sorted by start time.

| Enum | Values |
|---|---|
| Item `type` | `activity`, `reservation`, `transportation` |
| Travel mode (`defaultTravelMode`, `mode`) | `WALK`, `DRIVE`, `TRANSIT`, `BICYCLE` |
| Conflict `type` | `OVERLAP`, `INSUFFICIENT_TRAVEL_TIME`, `OUTSIDE_OPERATING_HOURS` (from PR 1) |

### Errors

Every error is JSON in the RFC 9457 "problem detail" format (`Content-Type: application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/trips",
  "errors": { "date": "must not be null", "name": "must not be blank" }
}
```

`detail` is the message to show. `errors` appears only when validation fails: it maps each bad field to its reason, so you can show the message next to the right input. Field names follow the JSON, for example `name`, `location.latitude`, and `origin.name`. Two are rules rather than fields: `timeRangeValid` (start isn't before end) and `location.hoursValid` (closing isn't after opening).

| Status | Meaning |
|---|---|
| 400 | Bad input: failed validation (see `errors`), malformed JSON, an unknown item `type`, an invalid time zone |
| 404 | Unknown trip or item |
| 409 | Agent only: the proposal is out of date |
| 502 | Google's travel-time service failed (bad key, outage) |
| 503 | Agent only: Claude is unavailable, or its key isn't set |

## Trips

| Request | Body | Success | Errors |
|---|---|---|---|
| `GET /api/trips` | | 200, list of trips | |
| `POST /api/trips` | trip | 201, the new trip (and a `Location` header) | 400 |
| `GET /api/trips/{tripId}` | | 200, the trip with its items | 404 |
| `PUT /api/trips/{tripId}` | trip | 200, the trip; its items are left alone | 400, 404 |
| `DELETE /api/trips/{tripId}` | | 204; deletes its items too | 404 |

Trip body (every field is required):

```json
{
  "name": "Paris day",
  "destination": "Paris",
  "date": "2026-11-01",
  "timeZone": "Europe/Paris",
  "defaultTravelMode": "WALK"
}
```

`defaultTravelMode` is how the traveler gets between stops, unless a transportation item says otherwise. The response has the same fields plus `id` and `items`, in the item shape below:

```json
{
  "id": 42,
  "name": "Paris day",
  "destination": "Paris",
  "date": "2026-11-01",
  "timeZone": "Europe/Paris",
  "defaultTravelMode": "WALK",
  "items": [ ... ]
}
```

## Itinerary items

| Request | Body | Success | Errors |
|---|---|---|---|
| `GET /api/trips/{tripId}/items` | | 200, list sorted by start time | 404 |
| `POST /api/trips/{tripId}/items` | item | 201, the new item (and a `Location` header) | 400, 404 |
| `GET /api/trips/{tripId}/items/{itemId}` | | 200, the item | 404 |
| `PUT /api/trips/{tripId}/items/{itemId}` | item | 200, the item. Replaces the whole item; the type may change. | 400, 404 |
| `DELETE /api/trips/{tripId}/items/{itemId}` | | 204 | 404 |

Every item body has `type`, `title`, `startTime`, `endTime` and an optional `notes`; the rest depends on the type. A place is `{ "name", "latitude", "longitude", "openTime", "closeTime" }`, where the hours are optional; give both or neither.

```json
{ "type": "activity", "title": "Louvre", "startTime": "10:00", "endTime": "12:00",
  "location": { "name": "Louvre", "latitude": 48.8606, "longitude": 2.3376, "openTime": "09:00", "closeTime": "18:00" },
  "category": "art", "notes": "Book tickets ahead" }
```

```json
{ "type": "reservation", "title": "Lunch at Le Consulat", "startTime": "12:20", "endTime": "13:30",
  "location": { "name": "Le Consulat", "latitude": 48.8866, "longitude": 2.3404 },
  "confirmationCode": "ABC123", "partySize": 2 }
```

```json
{ "type": "transportation", "title": "Metro to the Eiffel Tower", "startTime": "18:30", "endTime": "19:15",
  "origin": { "name": "Sacré-Cœur", "latitude": 48.8867, "longitude": 2.3431 },
  "destination": { "name": "Eiffel Tower", "latitude": 48.8584, "longitude": 2.2945 },
  "mode": "TRANSIT" }
```

| Field | Rules |
|---|---|
| `title`, `startTime`, `endTime` | Required; start before end |
| `location` (activity, reservation) | Required. `name` required, latitude -90 to 90, longitude -180 to 180 |
| `openTime`, `closeTime` | Optional; closing after opening |
| `category` (activity) | Optional free text |
| `confirmationCode` (reservation) | Optional |
| `partySize` (reservation) | Optional; positive |
| `origin`, `destination`, `mode` (transportation) | All required |

Responses have the same fields plus `id`, with seconds on every time and unused fields left out:

```json
{
  "id": 4,
  "type": "transportation",
  "title": "Metro to the Eiffel Tower",
  "startTime": "18:30:00",
  "endTime": "19:15:00",
  "origin": { "name": "Sacré-Cœur", "latitude": 48.8867, "longitude": 2.3431 },
  "destination": { "name": "Eiffel Tower", "latitude": 48.8584, "longitude": 2.2945 },
  "mode": "TRANSIT"
}
```

### Coordinates

Every place needs a latitude and longitude, and the backend has no place search: the PDF's Places-based suggestions were never built. The UI has to get coordinates somehow. Options, simplest first:

- **Number inputs**, with a hint to copy them from Google Maps (right-click a spot; the first menu line is the coordinates).
- **A map picker:** click a map to fill in the coordinates. Leaflet with OpenStreetMap tiles needs no API key.
- **Google Places Autocomplete in the browser.** It needs its own browser key, restricted to your site, and is more setup.

## Feasibility check

`POST /api/trips/{tripId}/feasibility` (no body) checks the saved schedule and returns its conflicts. Nothing is saved, so run it again after edits. It calls Google for travel times, so it can take a few seconds; PR 1 caches travel times, which makes repeat checks faster.

```json
{
  "tripId": 42,
  "feasible": false,
  "conflicts": [
    { "type": "OVERLAP", "itemIds": [2, 3],
      "reason": "\"Lunch at Le Consulat\" (12:20-13:30) overlaps \"Sacré-Cœur\" (13:15-14:30)" },
    { "type": "INSUFFICIENT_TRAVEL_TIME", "itemIds": [1, 2],
      "reason": "not enough time to get from \"Louvre\" (ends 12:00) to \"Lunch at Le Consulat\" (starts 12:20) by WALK: 51 min needed, 20 min available, 31 min short" },
    { "type": "OUTSIDE_OPERATING_HOURS", "itemIds": [1],
      "reason": "\"Louvre\" (08:00-12:00) starts before Louvre opens (open 09:00-18:00)" }
  ]
}
```

| Conflict type | `itemIds` | Meaning |
|---|---|---|
| `OVERLAP` | The two items | Their times overlap; touching (one ends as the next starts) is fine |
| `INSUFFICIENT_TRAVEL_TIME` | Two consecutive items | Not enough time to travel between them |
| `INSUFFICIENT_TRAVEL_TIME` | One transportation item | From PR 1: the leg is shorter than the trip takes by its mode |
| `OUTSIDE_OPERATING_HOURS` | One item | From PR 1: it doesn't fit inside its place's opening hours. Transportation isn't checked, and places without hours count as always open. |

- **Order:** conflicts come grouped by type, in the order above.
- **Highlighting:** use `itemIds` to mark the items involved.
- **`reason`** reads well enough to show as is.
- **Errors:** a 502 means Google failed; `detail` says why.

## The AI agent

Arrives with PR 2. Claude proposes changes to a saved trip and the feasibility check verifies them. Nothing is saved until the user clicks Apply.

### The flow

1. **Optimize.** The user optionally writes a goal ("make it less rushed"), can lock items, and can allow removals. The UI calls `POST /optimize`.
2. **Review.** Show the summary, the changes with their reasons, a before/after of the day, and any remaining conflicts.
3. **Refine** (optional, can repeat). The user writes feedback ("keep the Louvre at 10"). Call `POST /optimize` again with the same goal and locks, the previous response's `changes`, and every feedback message so far with the new one appended.
4. **Apply.** Call `POST /optimize/apply` with the proposal's `changes`, then show the updated trip.
5. **Discard.** Do nothing; the trip was never changed.

Each optimize call takes tens of seconds and can run slightly past 90. Show progress, block repeat clicks, and only start a run when the user asks for one: every run costs money.

### Optimize and refine

`POST /api/trips/{tripId}/optimize`

```json
{
  "goal": "Make the day work, I'd rather not rush.",
  "lockedItemIds": [2],
  "allowRemovals": false
}
```

| Field | Rules |
|---|---|
| `goal` | Optional; up to 500 characters |
| `lockedItemIds` | Optional. Ids of items the agent must not touch; all must belong to the trip (400 otherwise). Leave it out to lock every reservation; send `[]` to lock nothing. |
| `allowRemovals` | Optional, default `false`. Whether the agent may drop items. |
| `changes` | Refine only: the previous response's `changes`, sent back exactly as received |
| `feedback` | Refine only: every feedback message so far, oldest first; each up to 500 characters |

Response (200):

```json
{
  "tripId": 42,
  "feasible": true,
  "finished": true,
  "summary": "Your lunch booking stays at 12:20. I moved the Louvre visit 40 minutes earlier so you have time to walk to Montmartre.",
  "changes": [
    { "type": "RESCHEDULE", "itemId": 1, "title": "Louvre",
      "before": { "startTime": "10:00:00", "endTime": "12:00:00" },
      "after":  { "startTime": "09:20:00", "endTime": "11:20:00" },
      "reason": "The walk to Le Consulat takes about 51 minutes; leaving at 11:20 gets you there in time." }
  ],
  "conflictsBefore": [
    { "type": "INSUFFICIENT_TRAVEL_TIME", "itemIds": [1, 2],
      "reason": "not enough time to get from \"Louvre\" (ends 12:00) to \"Lunch at Le Consulat\" (starts 12:20) by WALK: 51 min needed, 20 min available, 31 min short" }
  ],
  "conflictsAfter": [],
  "items": [
    { "id": 1, "type": "activity", "title": "Louvre", "startTime": "09:20:00", "endTime": "11:20:00",
      "location": { "name": "Louvre", "latitude": 48.8606, "longitude": 2.3376, "openTime": "09:00:00", "closeTime": "18:00:00" },
      "category": "art", "notes": "Book tickets ahead" },
    ...the rest of the proposed day
  ]
}
```

| Field | Meaning |
|---|---|
| `feasible` | `conflictsAfter` is empty |
| `finished` | `false` when the agent hit its time or step limit; show the result as its best attempt |
| `summary` | Two or three sentences for the traveler |
| `changes` | What Apply would do, compared with the saved trip. `RESCHEDULE` has `before` and `after` times; `REMOVE` has `before` and `"after": null`. Shortening an item shows up as a `RESCHEDULE`. |
| `conflictsBefore` | The saved trip's conflicts, in the same shape as the feasibility check |
| `conflictsAfter` | The proposed day's conflicts |
| `items` | The whole proposed day, in the item shape, sorted by start time |

If the trip already has no conflicts and there's no goal or feedback, the response comes back at once with no `changes` and a summary saying there's nothing to fix.

A refine request looks like this:

```json
{
  "goal": "Make the day work, I'd rather not rush.",
  "lockedItemIds": [2],
  "allowRemovals": false,
  "changes": [ ...the previous response's changes... ],
  "feedback": ["I'd rather keep the Louvre at 10"]
}
```

The response has the same shape. Its `changes` are still compared with the saved trip, so they include anything kept from the previous proposal.

### Apply

`POST /api/trips/{tripId}/optimize/apply`

```json
{ "changes": [ ...the proposal's changes, exactly as received... ] }
```

It returns 200 with the updated trip, in the same shape as `GET /api/trips/{tripId}`. All the changes are written together, or none are. A 409 means the trip was edited after the proposal was made: nothing was written, so offer to run optimize again.

### Errors

| Status | When |
|---|---|
| 400 | Goal or feedback over 500 characters, a locked id that isn't in the trip, or a malformed change |
| 404 | Unknown trip |
| 409 | The trip changed since the proposal (refine or apply) |
| 502 | Google's travel-time service failed |
| 503 | Claude is unavailable, or `ANTHROPIC_API_KEY` isn't set |

## Suggested screens

Just suggestions; the UI design is yours.

- **Trips:** a list with name, destination and date; create, edit and delete.
- **Trip day:** the items as a timeline. Each item shows its type, title, times and place, plus the opening hours when it has them.
- **Item form:**
  - fields switch with the type
  - coordinates (see [Coordinates](#coordinates))
  - inline validation messages from `errors`
- **Check feasibility:** a button that lists the conflicts and highlights their items.
- **Optimize with AI:**
  - a goal box, a lock toggle on each item (reservations start locked) and an "allow removals" checkbox
  - the proposal view: summary, a before/after timeline, changes with reasons, remaining conflicts
  - refine (a feedback box), Apply, Discard

## Known limits

- **One day per trip:** nothing past midnight.
- **Shared data:** there are no user accounts, so everyone sees every trip.
- **Transit and driving times:** Google computes them for "leaving now", so they change with the time of day. Walking times don't, which is why the demo uses walking.
- **Saved results:** feasibility results and agent proposals aren't stored.
