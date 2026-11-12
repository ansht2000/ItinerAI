// The trips page: list trips, create one, delete one. Uses the `api` helper from api.js.

const listEl = document.getElementById("trip-list");
const loadingEl = document.getElementById("loading");
const emptyEl = document.getElementById("empty");
const listErrorEl = document.getElementById("list-error");

const form = document.getElementById("trip-form");
const formErrorEl = document.getElementById("form-error");
const createButton = document.getElementById("create-button");
const timeZoneInput = document.getElementById("timeZone");

// The browser's own time zone, e.g. "America/Los_Angeles"
const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "";

// ---------- Trip list ----------

async function loadTrips() {
  listErrorEl.hidden = true;
  try {
    const trips = await api.get("/api/trips");
    renderTrips(trips);
  } catch (error) {
    listEl.replaceChildren();
    emptyEl.hidden = true;
    showMessage(listErrorEl, "Couldn't load trips: " + error.detail);
  } finally {
    loadingEl.hidden = true;
  }
}

function renderTrips(trips) {
  listEl.replaceChildren();
  emptyEl.hidden = trips.length > 0;

  for (const trip of trips) {
    const li = document.createElement("li");

    const info = document.createElement("div");
    const link = document.createElement("a");
    link.href = "trip.html?id=" + encodeURIComponent(trip.id);
    link.className = "trip-name";
    link.textContent = trip.name;
    const meta = document.createElement("div");
    meta.className = "trip-meta";
    meta.textContent = trip.destination + " · " + trip.date;
    info.append(link, meta);

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.className = "danger";
    deleteButton.textContent = "Delete";
    deleteButton.addEventListener("click", () => deleteTrip(trip, deleteButton));

    li.append(info, deleteButton);
    listEl.append(li);
  }
}

async function deleteTrip(trip, button) {
  // confirm() shows the name as plain text, so it's safe to include
  if (!confirm(`Delete "${trip.name}" and all its items? This can't be undone.`)) {
    return;
  }
  button.disabled = true;
  listErrorEl.hidden = true;
  try {
    await api.del("/api/trips/" + encodeURIComponent(trip.id));
  } catch (error) {
    // a 404 means it's already gone, so just refresh; anything else is worth showing
    if (error.status !== 404) {
      showMessage(listErrorEl, "Couldn't delete the trip: " + error.detail);
      button.disabled = false;
      return;
    }
  }
  await loadTrips();
}

// ---------- Create form ----------

function resetForm() {
  form.reset();
  timeZoneInput.value = browserTimeZone;
}

function clearErrors() {
  formErrorEl.hidden = true;
  for (const el of form.querySelectorAll("[data-error-for]")) {
    el.textContent = "";
  }
  for (const el of form.querySelectorAll(".invalid")) {
    el.classList.remove("invalid");
  }
}

// Puts a message under a field. Returns false if the form has no such field.
function showFieldError(field, message) {
  const errorEl = form.querySelector(`[data-error-for="${field}"]`);
  if (!errorEl) {
    return false;
  }
  errorEl.textContent = message;
  const input = form.elements[field];
  if (input) {
    input.classList.add("invalid");
  }
  return true;
}

function showMessage(el, message) {
  el.textContent = message;
  el.hidden = false;
}

// Empty inputs are sent as null so the server reports "must not be null" for that field,
// rather than failing to read "" as a date or time zone.
function valueOrNull(field) {
  const value = form.elements[field].value.trim();
  return value === "" ? null : value;
}

// The server answers a misspelled time zone with a generic "Failed to read request",
// so check it here first to give a clearer message next to the field.
function isValidTimeZone(name) {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: name });
    return true;
  } catch (error) {
    return false;
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault(); // stop the browser from reloading the page
  clearErrors();

  const trip = {
    name: valueOrNull("name"),
    destination: valueOrNull("destination"),
    date: valueOrNull("date"),
    timeZone: valueOrNull("timeZone"),
    defaultTravelMode: valueOrNull("defaultTravelMode"),
  };

  if (trip.timeZone !== null && !isValidTimeZone(trip.timeZone)) {
    showFieldError("timeZone", "Unknown time zone. Use a name like Europe/Paris.");
    return;
  }

  createButton.disabled = true;
  try {
    await api.post("/api/trips", trip);
    resetForm();
    await loadTrips();
  } catch (error) {
    const fields = Object.keys(error.errors);
    if (fields.length > 0) {
      // Show each message next to its field; any we can't place go in the general message.
      const unplaced = [];
      for (const field of fields) {
        if (!showFieldError(field, error.errors[field])) {
          unplaced.push(field + ": " + error.errors[field]);
        }
      }
      if (unplaced.length > 0) {
        showMessage(formErrorEl, unplaced.join("\n"));
      }
    } else {
      showMessage(formErrorEl, error.detail);
    }
  } finally {
    createButton.disabled = false;
  }
});

// ---------- Start ----------

resetForm();
loadTrips();
