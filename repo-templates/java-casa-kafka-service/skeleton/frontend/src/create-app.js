import { resolve } from "node:path";
import express from "express";
import { configure } from "@dwp/govuk-casa";

import planFactory from "./plan.js";
import pagesFactory from "./pages.js";

const ROOT = resolve(import.meta.dirname, "..");

export const consoleLog = {
  info: (msg) => console.log(`${new Date().toISOString()} info ${msg}`),
  warn: (msg) => console.warn(`${new Date().toISOString()} warn ${msg}`),
  error: (msg) => console.error(`${new Date().toISOString()} error ${msg}`),
};

// One line per request, once the response is sent. Health checks from the
// load balancer are left out so they do not drown everything else.
const requestLogger = (log) => (req, res, next) => {
  const started = process.hrtime.bigint();
  res.on("finish", () => {
    if (req.path === "/healthz") return;
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    log.info(`${req.method} ${req.originalUrl} ${res.statusCode} ${ms.toFixed(1)}ms`);
  });
  next();
};

/**
 * Build the Express app: /healthz, then the CASA page mounted at "/".
 *
 * @param {object} opts Options
 * @param {string} opts.messageServiceUrl Base URL of message-service
 * @param {string} opts.sessionSecret Secret used to sign session cookies
 * @param {object} [opts.log] Logger with info/warn/error
 * @returns {import("express").Express} App
 */
export default function createApp({ messageServiceUrl, sessionSecret, log = consoleLog }) {
  const { mount } = configure({
    views: [resolve(ROOT, "views")],
    i18n: {
      dirs: [resolve(ROOT, "locales")],
      locales: ["en"],
    },
    session: {
      name: "messagesession",
      secret: sessionSecret,
      ttl: 3600,
      // The load balancer serves plain HTTP behind CloudFront, so a secure
      // cookie would never come back and every form post would fail its CSRF
      // check.
      secure: false,
      cookiePath: "/",
    },
    pages: pagesFactory({ messageServiceUrl, log }),
    plan: planFactory(),
  });

  const casaApp = express();
  // `/` redirects to the page, which is CASA's own way of starting a journey.
  mount(casaApp, { route: "/", serveFirstWaypoint: true });

  const app = express();
  app.disable("x-powered-by");
  app.use(requestLogger(log));
  app.get("/healthz", (req, res) => {
    res.status(200).json({ status: "ok" });
  });
  app.use("/", casaApp);

  return app;
}
