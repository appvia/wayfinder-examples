# frontend

One GOV.UK-styled page built with [CASA](https://github.com/dwp/govuk-casa) 10.
A person types a message and presses Send. The page sends it to message-service
with `POST /api/messages`, then shows the message's reference and its status,
read back from `GET /api/messages/<reference>`:

- `SUBMITTED`: message-service has accepted it and published it to Kafka. The
  page reloads itself every 3 seconds while it shows this.
- `RECEIVED`: message-service has read it back off the topic.

It is a demo, not a real government service: the page carries a "Demo" phase
banner.

## Run it

```sh
npm ci
MESSAGE_SERVICE_URL=http://localhost:8080 npm start   # http://localhost:3000
npm test
npm run lint
```

| Variable              | Default                 | What it does                                                     |
| --------------------- | ----------------------- | ---------------------------------------------------------------- |
| `PORT`                | `3000`                  | Port to listen on                                                |
| `MESSAGE_SERVICE_URL` | `http://localhost:8080` | Where messages are sent                                          |
| `SESSION_SECRET`      | random per process      | Signs session cookies. Without it, sessions end on every restart |

`GET /healthz` answers `200 {"status":"ok"}` without creating a session. `GET /`
redirects to the page, `/message`. Sessions are held in memory, which is enough
for one task: with more than one, a person loses the reference of their last
message whenever the load balancer sends them to a different task.

The app serves every path except `/api/*`, which the load balancer sends to
message-service.

## Layout

- `app.js` reads the environment, starts the server, and shuts down on SIGTERM
- `src/create-app.js` sets up `/healthz` and mounts the CASA page
- `src/plan.js` is the one-page CASA plan
- `src/pages.js` holds the field, its validation, and the hooks that send the
  message and read its status
- `src/message-client.js` calls message-service
- `views/` holds the Nunjucks templates. Keep them at `views/<file>.njk` or
  `views/<dir>/<file>.njk`: the Wayfinder template copies only those paths
  as they are, and renders every other file as a template.
