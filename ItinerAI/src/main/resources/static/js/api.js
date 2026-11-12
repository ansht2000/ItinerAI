// Shared helper for calling the ItinerAI API. Every page loads this file first.
//
// Usage:
//   const trips = await api.get("/api/trips");
//   const trip = await api.post("/api/trips", { name: "Paris day", ... });
//   await api.del("/api/trips/42");
//
// On an error response it throws an ApiError, which has:
//   error.status  the HTTP status (400, 404, ...), or 0 if the server couldn't be reached
//   error.detail  the message to show the user
//   error.errors  for validation failures, an object mapping field name to reason
//                 (e.g. { name: "must not be blank" }); otherwise an empty object

class ApiError extends Error {
  constructor(status, detail, errors) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
    this.errors = errors || {};
  }
}

async function request(method, url, body) {
  const options = { method, headers: { Accept: "application/json" } };
  if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, options);
  } catch (networkError) {
    // fetch only rejects when the request never got a response (server down, no network)
    throw new ApiError(0, "Can't reach the server. Is the app running?");
  }

  // Read the body as text first: some responses (like 204 No Content) have none.
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (parseError) {
      data = null;
    }
  }

  if (!response.ok) {
    const detail = (data && (data.detail || data.title)) ||
      `Request failed (${response.status} ${response.statusText})`;
    throw new ApiError(response.status, detail, data && data.errors);
  }

  return data;
}

const api = {
  get: (url) => request("GET", url),
  post: (url, body) => request("POST", url, body),
  put: (url, body) => request("PUT", url, body),
  del: (url) => request("DELETE", url),
};
