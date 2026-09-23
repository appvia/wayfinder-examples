import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";

import { buildApp, send } from "./helpers.js";

test("GET /healthz answers 200 ok without a session", async () => {
  const res = await request(buildApp()).get("/healthz").expect(200);
  assert.deepEqual(res.body, { status: "ok" });
  assert.equal(res.headers["set-cookie"], undefined);
});

test("GET / redirects to the message page", async () => {
  await request(buildApp()).get("/").expect(302).expect("location", "/message");
});

test("the message page shows its title, the demo banner and an empty form", async () => {
  const res = await request(buildApp()).get("/message").expect(200);
  assert.match(res.text, /<title>\s*Send a message/);
  assert.match(res.text, /not a real government service/);
  assert.match(res.text, /name="message"/);
  assert.doesNotMatch(res.text, /Your last message/);
  assert.doesNotMatch(res.text, /http-equiv="refresh"/);
});

test("an empty message is refused and the page says why", async () => {
  const res = await send(request.agent(buildApp()), "   ");
  assert.equal(res.status, 200);
  assert.match(res.text, /There is a problem/);
  assert.match(res.text, /Enter a message/);
});

test("a message over 500 characters is refused", async () => {
  const res = await send(request.agent(buildApp()), "x".repeat(501));
  assert.equal(res.status, 200);
  assert.match(res.text, /Message must be 500 characters or fewer/);
});

test("a form post without a CSRF token is refused", async () => {
  const agent = request.agent(buildApp());
  await agent.get("/message").expect(200);
  const res = await agent.post("/message").type("form").send({ message: "hello" });
  assert.equal(res.status, 403);
});
