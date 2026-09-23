// Talks to message-service.

const REQUEST_TIMEOUT_MS = 10000;

/**
 * POST a message. Resolves with the service's response body on 201.
 *
 * @param {string} baseUrl message-service base URL
 * @param {string} message Text to send
 * @returns {Promise<{reference: string, status?: string}>} Created message
 */
export async function submitMessage(baseUrl, message) {
  const response = await fetch(new URL("/api/messages", baseUrl), {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json" },
    body: JSON.stringify({ message }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });

  if (response.status !== 201) {
    const body = await response.text().catch(() => "");
    throw new Error(
      `message-service answered ${response.status} to POST /api/messages: ${body.slice(0, 500)}`,
    );
  }

  const created = await response.json();
  if (!created?.reference) {
    throw new Error("message-service returned 201 with no reference");
  }
  return created;
}

/**
 * GET a message by reference.
 *
 * @param {string} baseUrl message-service base URL
 * @param {string} reference Message reference
 * @returns {Promise<{reference: string, message: string, status: string}>} Message
 */
export async function getMessage(baseUrl, reference) {
  const response = await fetch(
    new URL(`/api/messages/${encodeURIComponent(reference)}`, baseUrl),
    {
      headers: { accept: "application/json" },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    },
  );
  if (!response.ok) {
    throw new Error(
      `message-service answered ${response.status} to GET /api/messages/${reference}`,
    );
  }
  return response.json();
}
