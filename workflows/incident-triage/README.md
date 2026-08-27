# Incident triage — a monitoring alert investigated by an AI agent, acted on by a workflow

A complete, self-contained example of Wayfinder reacting to a production alert
without a human in the loop:

```
SigNoz alert fires
  │
  ├─▶ delivered to an inbound Webhook            (webhook-signoz-alerts.yaml)
  │     │
  │     └─▶ the delivery fires a Workflow        (workflow.yaml)
  │           │
  │           ├─▶ investigate ── an AI agent investigates autonomously and
  │           │                  submits structured findings, including its own
  │           │                  severity judgement    (agents/)
  │           │
  │           ├─▶ raise_issue  ── severity critical or high → open a GitHub issue
  │           │                                        (webapi-github.yaml)
  │           │
  │           └─▶ notify_slack ── severity critical → page a Slack channel with a
  │                               link to that issue   (webapi-slack.yaml)
```

Everything here is **yours to own** — the integration, MCP servers, WebAPIs,
agent, result schema and identity are all defined in these files rather than
installed from Wayfinder's shipped catalogue, so this works on any tenant with
nothing but the credentials you supply.

**Defined at tenant scope, invoked at workspace scope** — with two exceptions.
The `Webhook` and the `Workflow` are workspace-scoped, so the run lands in the
workspace whose team owns the incident response; a tenant-scoped webhook would
also fall outside a workspace workflow's reach. Everything else is tenant-scoped
and shared. See [Known limitations](#known-limitations).

For the narrative walkthrough of how these pieces fit together, see
[Worked Example: Incident Triage](https://on.wayfinder.run/docs/integrations/09-incident-triage).

## What's here

| File | What it is |
| ---- | ---------- |
| `integration.yaml` | The custom `Integration` that owns everything below |
| `agents/incident-investigator.yaml` | The `AIAgent`: read-only, one-shot, autonomous-capable |
| `agents/datatype-incident-findings.yaml` | The `DataType` typing the agent's findings — including the `severity` the workflow branches on |
| `agents/mcp-signoz.yaml` + `agents/secret-signoz-mcp.yaml` | SigNoz MCP server the agent queries observability data through |
| `agents/mcp-github.yaml` + `agents/githuborg.yaml` | GitHub's hosted MCP server (read-only), authenticating with App-minted installation tokens |
| `webhook-signoz-alerts.yaml` | The inbound endpoint SigNoz delivers alerts to |
| `webapi-github.yaml` | Outbound: open a GitHub issue, on the same App installation — no stored credential |
| `webapi-slack.yaml` | Outbound: post to Slack via `chat.postMessage` |
| `secret-slack-token.yaml` | The one credential you supply |
| `identity.yaml` | The read-only `ServiceAccount` the investigation runs as, and its `RoleBinding` |
| `workflow.yaml` | The `Workflow`: the trigger, the three tasks and the severity gates |
| `slack-app-manifest.yaml` | Slack app manifest — pasted into Slack, **not** applied with `wf` |
| `apply-signoz-webhook.sh` | Registers the webhook as a SigNoz notification channel (idempotent) |
| `sample-alert.json` | An Alertmanager-shaped SigNoz payload, for testing by hand |

## Prerequisites

- A workspace named **`my-team`** (or change `metadata.workspace` on the webhook,
  the workflow and the identity — the same value in all three).
- A Wayfinder **GitHub App installation** approved on the organisation you set in
  `agents/githuborg.yaml`.
- A **SigNoz** instance (Cloud or self-hosted) with an admin-role API key.
- A **Slack** app with the `chat:write` bot scope — see
  [Setting up Slack](#setting-up-slack). This is the only credential the example
  stores; GitHub is reached entirely through the App installation above, for both
  reading code and opening issues.

## Placeholders to fill in

Search for `REPLACE-` before applying:

| Where | Replace with |
| ----- | ------------ |
| `workflow.yaml` → `spec.tasks.investigate.serviceAccount` | `<your-tenant>:my-team:incident-investigator` |
| `workflow.yaml` → `spec.config` | Your GitHub org, repo and Slack channel |
| `secret-slack-token.yaml` → `fields.token` | Your Slack bot token |
| `agents/secret-signoz-mcp.yaml` | Your SigNoz API key and instance URL |
| `agents/mcp-signoz.yaml` → `spec.endpoint` | Your SigNoz Cloud region, or your self-hosted MCP endpoint |
| `agents/githuborg.yaml` | Your GitHub organisation and its App installation ID |
| `webapi-github.yaml` + `agents/mcp-github.yaml` → `githubOrgRef` | The same organisation name |

## Setting up Slack

Wayfinder needs exactly one thing from Slack: a **bot token with `chat:write`**.

1. At [api.slack.com/apps](https://api.slack.com/apps) choose **Create New App**
   → **From an app manifest**, pick your workspace, and paste
   [`slack-app-manifest.yaml`](slack-app-manifest.yaml).
2. **Install to Workspace**, then copy the **Bot User OAuth Token** (`xoxb-…`)
   from *OAuth & Permissions* into `secret-slack-token.yaml`.
3. Set `spec.config.slackChannel` in `workflow.yaml`. Prefer the channel **ID**
   (*View channel details* → bottom of the About tab, e.g. `C0123456789`): it
   survives renames and is the only form that works for a private channel.
   `#name` is fine for a public channel.
4. For a **private** channel, invite the bot: `/invite @Wayfinder`. Public
   channels need no invite, thanks to the manifest's `chat:write.public`.

Test the Slack leg on its own before running the whole chain — it isolates Slack
from the trigger path and the agent's turn budget:

```bash
# Is the token valid at all? (Wayfinder not involved)
curl -s -H "Authorization: Bearer xoxb-..." https://slack.com/api/auth.test

# Does Wayfinder render the request correctly? Dry-run: nothing is sent, and the
# token comes back redacted.
wf validate webapi slack/post-message --input channel=C0123456789 --input text=hello

# Post for real, standalone — no workflow, no agent.
wf invoke webapi slack/post-message --input channel=C0123456789 --input text="hello from wayfinder"
wf get webapiinvocations
```

Slack answers **HTTP 200 even when it rejects a call**, putting the verdict in
`body.ok` — which is why the operation's `successCondition` checks both. Common
rejections: `invalid_auth` (bad or revoked token), `missing_scope` (reinstall
after adding the scope), `not_in_channel` (private channel, or the bot needs
inviting), `channel_not_found` (typo, or a private channel it cannot see).

## Applying it

`wf apply` sorts by dependency, so one command does the lot. It warns that it
skipped the three files here that are not Wayfinder resources — this README, the
Slack app manifest and the sample payload — which is expected:

```bash
git clone https://github.com/appvia/wayfinder-examples.git
cd wayfinder-examples

wf apply -f ./workflows/incident-triage/
```

## Connecting SigNoz

Register the webhook as a SigNoz notification channel:

```bash
export SIGNOZ_ENDPOINT="https://<your-instance>.eu.signoz.cloud"
export SIGNOZ_ACCESS_TOKEN="<admin-role API key>"

cd ./workflows/incident-triage/
./apply-signoz-webhook.sh
```

The script reads the webhook's **current** ingest URL from Wayfinder itself
(`wf get webhook … --subresource ingesturl`), so the credential never passes
through your shell history, a file or your clipboard — and because that is a read
rather than `wf rotate webhook`, re-running the script never invalidates a URL
SigNoz is already using. It is idempotent: creates the channel if missing, updates
it only on drift, leaves every other SigNoz channel alone, and never prints the URL.

Two permissions to get right. The SigNoz API key must be **admin-role**
(Settings → Service Accounts) — editor keys manage alerts and dashboards but get
403 on `/api/v1/channels`. And reading an ingest URL is granted alongside
**rotate**, not to viewers, since the URL is itself an ingest credential — so a
viewer-only Wayfinder role fails at the first step.

SigNoz does not sign webhook bodies, so the URL *is* the credential: treat it as a
secret, and if it leaks, `wf rotate webhook signoz-alerts -w my-team` then re-run
this script to point SigNoz at the new one. (SigNoz can send HTTP basic auth, but
Wayfinder's inbound webhook validates an HMAC signature over the body or nothing at
all, so there is no static-token mode to pair it with.)

Then, in SigNoz, attach the channel to the alerts you want triaged. **Start with
one.** Every delivery starts a real AI investigation, so route narrowly until you
are happy with what comes back — and note the workflow's trigger `when:` is a
cheaper, more precise filter than channel routing if you want to narrow further
(for example to `critical` only).

SigNoz groups alerts **by alert name and delivers every 5 minutes**, which caps a
noisy rule at one run per 5 minutes and means a delivery is homogeneous — one rule,
possibly several firing series. The workflow maps `alerts[0]`, so the summary and
severity are right for the delivery; only the service and environment come from the
first series.

## Testing it without waiting for an alert

`wf invoke webhook` performs a real delivery by name, bypassing the HMAC (you are
already authenticated). It records a real `WebhookInvocation`, so it fires the
workflow exactly as SigNoz would:

```bash
wf invoke webhook signoz-alerts -w my-team -f ./workflows/incident-triage/sample-alert.json
```

Then watch the run:

```bash
wf get workflowinvocations -w my-team
wf get workflowinvocation <name> -w my-team -o yaml   # per-task phases
```

Each task's work lives on its own child object — the investigation is an
`AIConversation`, each outbound call a `WebAPIInvocation`:

```bash
wf get aiconversations -w my-team
wf get webapiinvocations -w my-team
```

To try a different verdict, edit `sample-alert.json` so the incident reads as
less severe (a single retried request rather than widespread failure) and invoke
again — the agent should judge it lower, `raise_issue` should skip, and
`notify_slack` should skip with it.

## How the pieces connect

**The trigger.** A delivery records a `WebhookInvocation`; the workflow triggers
on that object's `Completed` event, which the ingest handler emits only *after*
the delivery record is durably written — so a run always sees the payload. The
request envelope is folded into the run as `.Resource.delivery.{body,headers,queryParams}`,
and a JSON body is parsed in-template with sprig `fromJson`.

**The agent's judgement.** The agent's `resultSchema.dataType` points at the
`IncidentFindings` `DataType`, so its `submit_result` payload is validated
server-side against that schema. The workflow therefore branches on
`.Tasks.investigate.output.findings.severity` knowing it is one of four values
and nothing else. A task's `output` is the whole `submit_result` payload:
`.output.result`, `.output.responseToUser` and `.output.findings.*`.

**The gates.** Tasks run in parallel unless they declare `dependsOn`, and each
task's `when:` decides whether it actually runs. A task whose dependency was
skipped is skipped too, so gating `raise_issue` on severity automatically gates
everything downstream of it. A task can only read the outputs of its **direct**
dependencies, which is why `notify_slack` depends on both `investigate` (for the
findings) and `raise_issue` (for the issue URL).

**The identity.** A triggered run has no interactive user, so the `investigate`
task names a `ServiceAccount` and the agent's tools execute with exactly that
account's RBAC — read-only here. Without one the agent has no identity to act as
and every Wayfinder tool it calls fails. Wayfinder checks that whoever authors
the workflow holds a superset of that account's permissions, so a run can never
exceed the person who set it up.

## Known limitations

- **Nothing here is environment-scoped.** `Workflow`, `Webhook` and their
  invocations support tenant and workspace scope only, and a run is placed at its
  Workflow's own scope. A workflow concerned with one environment still runs at the
  workspace and reaches into the environment through its task ServiceAccount — which
  means the run, the investigation and the alert payload are readable by the whole
  workspace. See
  [Integrations & Workflows](https://on.wayfinder.run/docs/integrations/01-overview).
- **Slack still needs a stored token.** GitHub is reached through an App
  installation, so nothing is stored for it; Slack has no equivalent, so its bot
  token lives in a `PlatformSecret`.
- **The investigation is bounded at 60 model turns** — the agent's
  `spec.maxModelTurns` (the platform default is 20, and 200 is the ceiling). An
  investigation that exhausts its bound ends `Failed` rather than submitting
  findings, so raise it if you route alerts that need more digging.
- **`alerts[0]`.** SigNoz delivers a batch; the templated inputs map the first
  alert. The agent is handed the whole body as `details`, so nothing is lost, but
  a batch of unrelated alerts produces one investigation framed by the first.
- **The webhook stores nothing on your resources.** It exists to carry the alert
  into a run. Add a `storage:` block to also record each alert on the
  `StackInstance` it fired on.
