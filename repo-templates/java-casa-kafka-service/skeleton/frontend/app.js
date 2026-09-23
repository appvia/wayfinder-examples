import { randomBytes } from "node:crypto";

import createApp, { consoleLog as log } from "./src/create-app.js";

const port = Number(process.env.PORT ?? 3000);
const messageServiceUrl = process.env.MESSAGE_SERVICE_URL ?? "http://localhost:8080";

let sessionSecret = process.env.SESSION_SECRET;
if (!sessionSecret) {
  sessionSecret = randomBytes(32).toString("hex");
  log.warn(
    "SESSION_SECRET is not set; using a random secret, so sessions end whenever this process restarts",
  );
}

const app = createApp({ messageServiceUrl, sessionSecret, log });

const server = app.listen(port, () => {
  log.info(
    `listening on port ${port}, sending messages to ${messageServiceUrl} (release ${process.env.RELEASE ?? "unknown"})`,
  );
});

let shuttingDown = false;
const shutdown = (signal) => {
  if (shuttingDown) return;
  shuttingDown = true;
  log.info(`${signal} received; finishing open requests`);
  const forceExit = setTimeout(() => {
    log.warn("requests still open after 10s; exiting");
    process.exit(1);
  }, 10000);
  forceExit.unref();
  server.close((err) => {
    if (err) log.error(`closing server: ${err.message}`);
    process.exit(err ? 1 : 0);
  });
  server.closeIdleConnections();
};

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
