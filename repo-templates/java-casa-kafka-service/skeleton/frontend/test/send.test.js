import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";

import { buildApp, send, startFakeMessageService } from "./helpers.js";

let messageService;
before(async () => {
  messageService = await startFakeMessageService();
});
after(async () => {
  await messageService.close();
});

test("sending a message shows its reference, and its status until it is RECEIVED", async () => {
  const agent = request.agent(buildApp({ messageServiceUrl: messageService.url }));

  const sent = await send(agent, "  Hello, Kafka  ");
  assert.equal(sent.status, 302);
  assert.equal(sent.headers.location, "/message");
  assert.deepEqual(messageService.received, [{ message: "Hello, Kafka" }]);

  // First read: not consumed yet, so the page keeps reloading itself.
  const submitted = await agent.get("/message").expect(200);
  assert.match(submitted.text, /id="message-reference">MSG-TEST0001</);
  assert.match(submitted.text, /id="message-status">SUBMITTED</);
  assert.match(submitted.text, /http-equiv="refresh"/);

  // The form is empty again, ready for the next message.
  assert.doesNotMatch(submitted.text, /Hello, Kafka/);

  const received = await agent.get("/message").expect(200);
  assert.match(received.text, /id="message-status">RECEIVED</);
  assert.doesNotMatch(received.text, /http-equiv="refresh"/);
});

test("a message-service failure shows an error page", async () => {
  const res = await send(request.agent(buildApp()), "hello");
  assert.equal(res.status, 502);
  assert.match(res.text, /Sorry, there is a problem with the service/);
});
