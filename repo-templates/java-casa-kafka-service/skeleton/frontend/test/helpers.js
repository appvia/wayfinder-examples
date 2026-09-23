import { createServer } from "node:http";
import createApp from "../src/create-app.js";

export const quietLog = { info() {}, warn() {}, error() {} };

// Nothing listens on port 1, so every call to it fails to connect.
export function buildApp({ messageServiceUrl = "http://127.0.0.1:1" } = {}) {
  return createApp({ messageServiceUrl, sessionSecret: "test-secret", log: quietLog });
}

export function csrfToken(html) {
  const match = html.match(/name="_csrf" value="([^"]+)"/);
  if (!match) throw new Error("no _csrf token in page");
  return match[1];
}

// GET the page for its CSRF token, then POST the message to it.
export async function send(agent, message) {
  const form = await agent.get("/message").expect(200);
  return agent.post("/message").type("form").send({ _csrf: csrfToken(form.text), message });
}

// A stand-in for message-service: records posted messages, and reports each as
// SUBMITTED the first time it is read and RECEIVED after that.
export async function startFakeMessageService() {
  const received = [];
  const reads = new Map();
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      res.setHeader("content-type", "application/json");
      if (req.method === "POST" && req.url === "/api/messages") {
        received.push(JSON.parse(body));
        res.statusCode = 201;
        res.end(JSON.stringify({ reference: "MSG-TEST0001", status: "SUBMITTED" }));
      } else if (req.method === "GET" && req.url === "/api/messages/MSG-TEST0001") {
        const count = (reads.get(req.url) ?? 0) + 1;
        reads.set(req.url, count);
        const status = count === 1 ? "SUBMITTED" : "RECEIVED";
        res.end(JSON.stringify({ reference: "MSG-TEST0001", message: received.at(-1)?.message, status }));
      } else {
        res.statusCode = 404;
        res.end("{}");
      }
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  return {
    url: `http://127.0.0.1:${port}`,
    received,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}
