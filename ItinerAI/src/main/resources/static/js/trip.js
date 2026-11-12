// The trip day page: show one trip and its items, and add, edit or delete items.
// Uses the `api` helper from api.js.

const tripId = new URLSearchParams(window.location.search).get("id");
const itemsUrl = "/api/trips/" + encodeURIComponent(tripId) + "/items";

const loadingEl = document.getElementById("loading");
const pageErrorEl = document.getElementById("page-error");
const tripPageEl = document.getElementById("trip-page");
const listEl = document.getElementById("item-list");
const emptyEl = document.getElementById("empty");
const listErrorEl = document.getElementById("list-error");

const form = document.getElementById("item-form");
const formTitleEl = document.getElementById("form-title");
const formErrorEl = document.getElementById("form-error");
const submitButton = document.getElementById("submit-button");
const cancelButton = document.getElementById("cancel-button");

const TYPE_LABELS = { activity: "Activity", reservation: "Reservation", transportation: "Transportation" };

// The id of the item being edited, or null when the form is adding a new item
let editingId = null;

// ---------- Loading the trip ----------

async function loadTrip() {
  try {
    const trip = await api.get("/api/trips/" + encodeURIComponent(tripId));
    renderTrip(trip);
    tripPageEl.hidden = false;
  } catch (error) {
    // Only replace the whole page if it never loaded; after that, keep what's on screen.
    if (tripPageEl.hidden) {
      showPageError(error.status === 404 ? "That trip doesn't exist. It may have been deleted." : error.detail);
    } else {
      showMessage(listErrorEl, "Couldn't reload the trip: " + error.detail);
    }
  } finally {
    loadingEl.hidden = true;
  }
}

function showPageError(message) {
  document.getElementById("page-error-message").textContent = message;
  pageErrorEl.hidden = false;
  loadingEl.hidden = true;
}

function renderTrip(trip) {
  document.title = "ItinerAI – " + trip.name;
  document.getElementById("trip-name").textContent = trip.name;
  document.getElementById("trip-meta").textContent =
    `${trip.destination} · ${trip.date} · ${trip.timeZone} · Travel by ${trip.defaultTravelMode.toLowerCase()}`;

  listErrorEl.hidden = true;
  listEl.replaceChildren();
  emptyEl.hidden = trip.items.length > 0;
  for (const item of trip.items) {
    listEl.append(renderItem(item));
  }
}

// "10:00:00" -> "10:00"
function hhmm(time) {
  return time ? time.slice(0, 5) : "";
}

function describePlace(place) {
  let text = place.name;
  if (place.openTime && place.closeTime) {
    text += ` (open ${hhmm(place.openTime)}–${hhmm(place.closeTime)})`;
  }
  return text;
}

// Adds a line of text to a parent element, if the text isn't empty
function addLine(parent, className, text) {
  if (!text) {
    return;
  }
  const el = document.createElement("div");
  el.className = className;
  el.textContent = text;
  parent.append(el);
}

function renderItem(item) {
  const li = document.createElement("li");

  const info = document.createElement("div");
  addLine(info, "item-time", `${hhmm(item.startTime)}–${hhmm(item.endTime)} · ${TYPE_LABELS[item.type] || item.type}`);
  addLine(info, "item-title", item.title);

  if (item.type === "transportation") {
    addLine(info, "item-detail",
      `${item.origin.name} → ${item.destination.name} by ${item.mode.toLowerCase()}`);
  } else {
    addLine(info, "item-detail", describePlace(item.location));
  }
  if (item.type === "activity" && item.category) {
    addLine(info, "item-detail", "Category: " + item.category);
  }
  if (item.type === "reservation") {
    const parts = [];
    if (item.confirmationCode) parts.push("Confirmation: " + item.confirmationCode);
    if (item.partySize) parts.push("Party of " + item.partySize);
    addLine(info, "item-detail", parts.join(" · "));
  }
  addLine(info, "item-notes", item.notes);

  const buttons = document.createElement("div");
  buttons.className = "item-buttons";
  const editButton = document.createElement("button");
  editButton.type = "button";
  editButton.textContent = "Edit";
  editButton.addEventListener("click", () => startEditing(item));
  const deleteButton = document.createElement("button");
  deleteButton.type = "button";
  deleteButton.className = "danger";
  deleteButton.textContent = "Delete";
  deleteButton.addEventListener("click", () => deleteItem(item, deleteButton));
  buttons.append(editButton, deleteButton);

  li.append(info, buttons);
  return li;
}

async function deleteItem(item, button) {
  if (!confirm(`Delete "${item.title}"?`)) {
    return;
  }
  button.disabled = true;
  try {
    await api.del(itemsUrl + "/" + encodeURIComponent(item.id));
  } catch (error) {
    // a 404 means it's already gone, so just reload; anything else is worth showing
    if (error.status !== 404) {
      showMessage(listErrorEl, "Couldn't delete the item: " + error.detail);
      button.disabled = false;
      return;
    }
  }
  if (editingId === item.id) {
    stopEditing();
  }
  await loadTrip();
}

// ---------- The item form ----------

// Shows only the fields for the selected type
function showFieldsForType() {
  const type = form.elements.type.value;
  for (const el of form.querySelectorAll("[data-types]")) {
    el.hidden = !el.dataset.types.split(" ").includes(type);
  }
}

form.elements.type.addEventListener("change", () => {
  showFieldsForType();
  clearErrors();
});

function setValue(field, value) {
  form.elements[field].value = value ?? "";
}

function fillPlace(prefix, place) {
  setValue(prefix + ".name", place.name);
  setValue(prefix + ".latitude", place.latitude);
  setValue(prefix + ".longitude", place.longitude);
  if (form.elements[prefix + ".openTime"]) {
    setValue(prefix + ".openTime", hhmm(place.openTime));
    setValue(prefix + ".closeTime", hhmm(place.closeTime));
  }
}

function startEditing(item) {
  resetForm();
  editingId = item.id;
  formTitleEl.textContent = "Edit item";
  submitButton.textContent = "Save changes";
  cancelButton.hidden = false;

  setValue("type", item.type);
  setValue("title", item.title);
  setValue("startTime", hhmm(item.startTime));
  setValue("endTime", hhmm(item.endTime));
  setValue("notes", item.notes);
  if (item.type === "transportation") {
    fillPlace("origin", item.origin);
    fillPlace("destination", item.destination);
    setValue("mode", item.mode);
  } else {
    fillPlace("location", item.location);
    setValue("category", item.category);
    setValue("confirmationCode", item.confirmationCode);
    setValue("partySize", item.partySize);
  }
  showFieldsForType();
  form.scrollIntoView({ behavior: "smooth" });
}

function stopEditing() {
  editingId = null;
  formTitleEl.textContent = "Add an item";
  submitButton.textContent = "Add item";
  cancelButton.hidden = true;
  resetForm();
}

cancelButton.addEventListener("click", stopEditing);

function resetForm() {
  form.reset();
  clearErrors();
  showFieldsForType();
}

// Blank inputs are sent as null, so the server reports a clear message for required ones.
function text(field) {
  const value = form.elements[field].value.trim();
  return value === "" ? null : value;
}

function number(field) {
  const value = text(field);
  return value === null ? null : Number(value);
}

function place(prefix, withHours) {
  const result = {
    name: text(prefix + ".name"),
    latitude: number(prefix + ".latitude"),
    longitude: number(prefix + ".longitude"),
  };
  if (withHours) {
    result.openTime = text(prefix + ".openTime");
    result.closeTime = text(prefix + ".closeTime");
  }
  return result;
}

// Builds the JSON body for the selected type, using only that type's fields
function readItem() {
  const type = form.elements.type.value;
  const item = {
    type,
    title: text("title"),
    startTime: text("startTime"),
    endTime: text("endTime"),
    notes: text("notes"),
  };
  if (type === "activity") {
    item.location = place("location", true);
    item.category = text("category");
  } else if (type === "reservation") {
    item.location = place("location", true);
    item.confirmationCode = text("confirmationCode");
    item.partySize = number("partySize");
  } else {
    item.origin = place("origin", false);
    item.destination = place("destination", false);
    item.mode = text("mode");
  }
  return item;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault(); // stop the browser from reloading the page
  clearErrors();
  const item = readItem();

  // The server would save a place with only one of the two times, so catch it here.
  if (item.location && (item.location.openTime === null) !== (item.location.closeTime === null)) {
    showFieldError("location.hoursValid", "Fill in both opening and closing time, or leave both blank.");
    form.elements[item.location.openTime === null ? "location.openTime" : "location.closeTime"]
      .classList.add("invalid");
    return;
  }

  submitButton.disabled = true;
  try {
    if (editingId === null) {
      await api.post(itemsUrl, item);
    } else {
      await api.put(itemsUrl + "/" + encodeURIComponent(editingId), item);
    }
    stopEditing();
    await loadTrip();
  } catch (error) {
    showErrors(error);
  } finally {
    submitButton.disabled = false;
  }
});

// ---------- Form errors ----------

function clearErrors() {
  formErrorEl.hidden = true;
  for (const el of form.querySelectorAll("[data-error-for]")) {
    el.textContent = "";
  }
  for (const el of form.querySelectorAll(".invalid")) {
    el.classList.remove("invalid");
  }
}

// Puts a message under a field (or a rule like timeRangeValid). Returns false if there's no spot for it.
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

function showErrors(error) {
  const fields = Object.keys(error.errors);
  if (fields.length === 0) {
    showMessage(formErrorEl, error.detail);
    return;
  }
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
}

function showMessage(el, message) {
  el.textContent = message;
  el.hidden = false;
}

// ---------- Start ----------

if (!tripId) {
  showPageError("No trip was chosen.");
} else {
  resetForm();
  loadTrip();
}
