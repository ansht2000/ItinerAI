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

const checkButton = document.getElementById("check-button");
const checkStatusEl = document.getElementById("check-status");
const checkErrorEl = document.getElementById("check-error");
const checkResultsEl = document.getElementById("check-results");
const checkOkEl = document.getElementById("check-ok");
const conflictListEl = document.getElementById("conflict-list");

const TYPE_LABELS = { activity: "Activity", reservation: "Reservation", transportation: "Transportation" };

const CONFLICT_LABELS = {
  OVERLAP: "Overlap",
  INSUFFICIENT_TRAVEL_TIME: "Not enough travel time",
  OUTSIDE_OPERATING_HOURS: "Outside opening hours",
};

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
    listEl.append(renderItem(item, true));
  }
  renderLocks(trip.items);
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

// withButtons is false for the AI's proposed day, which can't be edited directly
function renderItem(item, withButtons) {
  const li = document.createElement("li");
  li.dataset.itemId = item.id; // lets the feasibility check find this item to highlight it

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
  li.append(info);
  if (!withButtons) {
    return li;
  }

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

  li.append(buttons);
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
  clearStaleResults();
  await loadTrip();
}

// After any change to the items, feasibility results and AI proposals no longer match the trip.
function clearStaleResults() {
  clearFeasibility();
  clearProposal();
  agentErrorEl.hidden = true;
}

// ---------- Feasibility check ----------

// Goes up by one whenever results are cleared, so a check that finishes after an edit is ignored.
let checkRun = 0;

function conflictLabel(type) {
  return CONFLICT_LABELS[type] || type;
}

// One conflict as a list item: "Overlap: <reason>". Also used by the AI panel.
function renderConflict(conflict) {
  const li = document.createElement("li");
  const label = document.createElement("strong");
  label.textContent = conflictLabel(conflict.type) + ": ";
  li.append(label, conflict.reason);
  return li;
}

async function checkFeasibility() {
  clearFeasibility();
  const run = checkRun;
  checkButton.disabled = true;
  checkStatusEl.hidden = false;
  try {
    const result = await api.post("/api/trips/" + encodeURIComponent(tripId) + "/feasibility");
    if (run === checkRun) {
      showFeasibility(result.conflicts);
    }
  } catch (error) {
    if (run === checkRun) {
      showMessage(checkErrorEl, "Couldn't check the trip: " + error.detail);
    }
  } finally {
    checkButton.disabled = false;
    checkStatusEl.hidden = true;
  }
}

checkButton.addEventListener("click", checkFeasibility);

function showFeasibility(conflicts) {
  checkResultsEl.hidden = false;
  checkOkEl.hidden = conflicts.length > 0;

  conflicts.forEach((conflict, index) => {
    const number = index + 1;

    conflictListEl.append(renderConflict(conflict));

    // Highlight each item involved and note which conflict it's part of
    for (const itemId of conflict.itemIds) {
      const itemEl = listEl.querySelector(`[data-item-id="${itemId}"]`);
      if (!itemEl) {
        continue;
      }
      itemEl.classList.add("has-conflict");
      let tagsEl = itemEl.querySelector(".conflict-tags");
      if (!tagsEl) {
        tagsEl = document.createElement("div");
        tagsEl.className = "conflict-tags";
        itemEl.firstChild.append(tagsEl);
      }
      const tag = document.createElement("span");
      tag.className = "conflict-tag";
      tag.textContent = `Conflict ${number}: ${conflictLabel(conflict.type)}`;
      tagsEl.append(tag);
    }
  });
}

// Results go stale after any change to the items, so remove them and the highlights.
function clearFeasibility() {
  checkRun++;
  checkResultsEl.hidden = true;
  checkErrorEl.hidden = true;
  conflictListEl.replaceChildren();
  for (const el of listEl.querySelectorAll(".has-conflict")) {
    el.classList.remove("has-conflict");
  }
  for (const el of listEl.querySelectorAll(".conflict-tags")) {
    el.remove();
  }
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
    clearStaleResults();
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

// ---------- Optimize with AI ----------
//
// The flow (see UI_INFO.md, "The AI agent"): Optimize makes a proposal; Refine sends feedback and gets
// a new proposal; Apply saves it; Discard drops it. Nothing is saved until Apply.

const agentInputsEl = document.getElementById("agent-inputs");
const goalInput = document.getElementById("goal");
const lockListEl = document.getElementById("lock-list");
const allowRemovalsInput = document.getElementById("allow-removals");
const optimizeButton = document.getElementById("optimize-button");
const agentStatusEl = document.getElementById("agent-status");
const agentErrorEl = document.getElementById("agent-error");
const proposalEl = document.getElementById("proposal");
const feedbackInput = document.getElementById("feedback");
const refineButton = document.getElementById("refine-button");
const applyButton = document.getElementById("apply-button");
const discardButton = document.getElementById("discard-button");

// Which items the user has locked, by id. Items not in here use the default: reservations are locked.
const locks = new Map();

// While a proposal is open: the request that made it (goal, locks, allowRemovals), its response,
// and every feedback message sent so far. All null/empty when there's no proposal.
let agentRequest = null;
let proposal = null;
let feedbackHistory = [];

// Goes up by one whenever the proposal is cleared, so a run that finishes after an edit is ignored.
let agentRun = 0;

function renderLocks(items) {
  lockListEl.replaceChildren();
  if (items.length === 0) {
    lockListEl.textContent = "No items yet.";
  }
  for (const item of items) {
    const label = document.createElement("label");
    label.className = "checkbox";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = locks.has(item.id) ? locks.get(item.id) : item.type === "reservation";
    checkbox.dataset.itemId = item.id;
    checkbox.addEventListener("change", () => locks.set(item.id, checkbox.checked));
    label.append(checkbox, ` ${hhmm(item.startTime)} ${item.title}`);
    lockListEl.append(label);
  }
}

// The options from the panel. lockedItemIds is always sent, even when empty, so the server
// never falls back to its own default.
function readAgentRequest() {
  const lockedItemIds = [];
  for (const checkbox of lockListEl.querySelectorAll("input[type=checkbox]")) {
    if (checkbox.checked) {
      lockedItemIds.push(Number(checkbox.dataset.itemId));
    }
  }
  const request = { lockedItemIds, allowRemovals: allowRemovalsInput.checked };
  const goal = goalInput.value.trim();
  if (goal !== "") {
    request.goal = goal;
  }
  return request;
}

// Disables every agent button (and the inputs) while a run is going
function setAgentBusy(busy) {
  optimizeButton.disabled = busy;
  refineButton.disabled = busy;
  discardButton.disabled = busy;
  applyButton.disabled = busy || !proposal || proposal.changes.length === 0;
  agentStatusEl.hidden = !busy;
}

let timerId = null;

function startTimer(message) {
  const started = Date.now();
  const update = () => {
    const seconds = Math.round((Date.now() - started) / 1000);
    agentStatusEl.textContent = `${message} ${seconds}s (this can take up to about 90 seconds)`;
  };
  update();
  timerId = setInterval(update, 1000);
}

function stopTimer() {
  clearInterval(timerId);
}

// Sends one optimize request (a first run or a refine) and shows the result.
// feedback is the full history including the new message; it's only kept if the run succeeds.
// Returns true on success, false on an error, and null if the trip was edited while it ran.
async function runAgent(body, feedback, statusMessage) {
  const run = agentRun;
  agentErrorEl.hidden = true;
  setAgentBusy(true);
  startTimer(statusMessage);
  try {
    const result = await api.post("/api/trips/" + encodeURIComponent(tripId) + "/optimize", body);
    if (run !== agentRun) {
      return null; // the trip was edited while this ran, so the result is stale
    }
    feedbackHistory = feedback;
    showProposal(result);
    return true;
  } catch (error) {
    if (run !== agentRun) {
      return null;
    }
    showAgentError(error);
    return false;
  } finally {
    // If the proposal was cleared meanwhile, clearProposal already reset these, and a newer run may own them.
    if (run === agentRun) {
      stopTimer();
      setAgentBusy(false);
    }
  }
}

async function optimize() {
  clearProposal();
  agentRequest = readAgentRequest();
  agentInputsEl.disabled = true; // the goal and locks can't change while a proposal is open
  if (await runAgent(agentRequest, [], "Optimizing…") === false) {
    clearProposal(); // keeps the error message, but unlocks the inputs so the user can try again
  }
}

optimizeButton.addEventListener("click", optimize);

refineButton.addEventListener("click", async () => {
  const message = feedbackInput.value.trim();
  if (message === "") {
    showMessage(agentErrorEl, "Write some feedback first, then click Refine.");
    return;
  }
  const feedback = [...feedbackHistory, message];
  const body = { ...agentRequest, changes: proposal.changes, feedback };
  if (await runAgent(body, feedback, "Refining…")) {
    feedbackInput.value = "";
  }
});

applyButton.addEventListener("click", async () => {
  const run = agentRun;
  agentErrorEl.hidden = true;
  setAgentBusy(true);
  agentStatusEl.textContent = "Saving the changes…";
  try {
    await api.post("/api/trips/" + encodeURIComponent(tripId) + "/optimize/apply", { changes: proposal.changes });
    if (run !== agentRun) {
      return;
    }
    clearStaleResults();
    await loadTrip();
  } catch (error) {
    if (run === agentRun) {
      showAgentError(error);
    }
  } finally {
    if (run === agentRun) {
      setAgentBusy(false);
    }
  }
});

discardButton.addEventListener("click", () => {
  clearProposal();
  agentErrorEl.hidden = true;
});

function clearProposal() {
  agentRun++;
  agentRequest = null;
  proposal = null;
  feedbackHistory = [];
  feedbackInput.value = "";
  proposalEl.hidden = true;
  optimizeButton.hidden = false;
  agentInputsEl.disabled = false;
  stopTimer();
  setAgentBusy(false);
}

function showAgentError(error) {
  agentErrorEl.replaceChildren();
  let message;
  if (error.status === 409) {
    // Nothing was written. The proposal is out of date, so drop it and offer a fresh run.
    clearProposal();
    message = `The trip changed since this proposal was made, so nothing was saved (${error.detail}).`;
  } else if (error.status === 503) {
    message = "The AI assistant is unavailable right now. " + error.detail;
  } else if (error.status === 502) {
    message = "Travel times couldn't be checked, so the AI can't plan the day right now. " + error.detail;
  } else {
    message = error.detail;
    const fieldMessages = Object.entries(error.errors).map(([field, reason]) => `${field}: ${reason}`);
    if (fieldMessages.length > 0) {
      message += "\n" + fieldMessages.join("\n");
    }
  }
  agentErrorEl.append(message);

  if (error.status === 409) {
    const again = document.createElement("button");
    again.type = "button";
    again.textContent = "Optimize again";
    again.addEventListener("click", async () => {
      agentErrorEl.hidden = true;
      await loadTrip(); // pick up whatever changed
      await optimize();
    });
    agentErrorEl.append(" ", again);
  }
  agentErrorEl.hidden = false;
}

function showProposal(result) {
  proposal = result;
  proposalEl.hidden = false;
  optimizeButton.hidden = true; // Refine, Apply or Discard from here

  document.getElementById("proposal-summary").textContent = result.summary;

  const feasibleEl = document.getElementById("proposal-feasible");
  feasibleEl.textContent = result.feasible
    ? "The proposed day has no conflicts."
    : `The proposed day still has ${result.conflictsAfter.length} conflict(s).`;
  feasibleEl.className = result.feasible ? "ok" : "error";

  document.getElementById("proposal-unfinished").hidden = result.finished;

  const changesEl = document.getElementById("proposal-changes");
  changesEl.replaceChildren();
  if (result.changes.length === 0) {
    changesEl.append(emptyNote("No changes proposed."));
  }
  for (const change of result.changes) {
    const li = document.createElement("li");
    const title = document.createElement("strong");
    title.textContent = change.title;
    const before = `${hhmm(change.before.startTime)}–${hhmm(change.before.endTime)}`;
    const after = change.after ? `${hhmm(change.after.startTime)}–${hhmm(change.after.endTime)}` : "removed";
    li.append(title, `: ${before} → ${after}`);
    addLine(li, "item-notes", change.reason);
    changesEl.append(li);
  }

  renderConflicts(document.getElementById("conflicts-before"), result.conflictsBefore);
  renderConflicts(document.getElementById("conflicts-after"), result.conflictsAfter);

  const daysEl = document.getElementById("proposal-items");
  daysEl.replaceChildren();
  for (const item of result.items) {
    daysEl.append(renderItem(item, false));
  }

  const historyEl = document.getElementById("feedback-history");
  historyEl.replaceChildren();
  for (const message of feedbackHistory) {
    const li = document.createElement("li");
    li.textContent = message;
    historyEl.append(li);
  }
  document.getElementById("feedback-history-block").hidden = feedbackHistory.length === 0;
}

function renderConflicts(listElement, conflicts) {
  listElement.replaceChildren();
  if (conflicts.length === 0) {
    listElement.append(emptyNote("None."));
  }
  for (const conflict of conflicts) {
    listElement.append(renderConflict(conflict));
  }
}

function emptyNote(text) {
  const li = document.createElement("li");
  li.className = "empty";
  li.textContent = text;
  return li;
}

// ---------- Start ----------

if (!tripId) {
  showPageError("No trip was chosen.");
} else {
  resetForm();
  loadTrip();
}
