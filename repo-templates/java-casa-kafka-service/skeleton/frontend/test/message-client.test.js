import { test, before, after } from "node:test";
import assert from "node:assert/strict";

import { getMessage, submitMessage } from "../src/message-client.js";
import { startFakeMessageService } from "./helpers.js";

let messageService;
before(async () => {
  messageService = await startFakeMessageService();
});
after(async () => {
  await messageService.close();
});

test("submitMessage posts the text and returns the reference", async () => {
  const created = await submitMessage(messageService.url, "hello");
  assert.equal(created.reference, "MSG-TEST0001");
  assert.deepEqual(messageService.received.at(-1), { message: "hello" });
});

test("getMessage fails with the status message-service answered", async () => {
  await assert.rejects(getMessage(messageService.url, "MSG-MISSING"), /answered 404/);
});
