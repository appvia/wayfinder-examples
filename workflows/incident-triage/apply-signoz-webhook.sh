#!/usr/bin/env bash
#
# apply-signoz-webhook.sh — idempotently register this example's Wayfinder
# inbound webhook as a SigNoz notification channel, so SigNoz alerts fire the
# incident-triage workflow.
#
# SigNoz's Terraform provider does not expose a channel resource, so channels are
# managed through the REST API (POST/PUT /api/v1/channels). This is the
# self-contained equivalent of what saas-envs/signoz/channels/apply-channels.sh
# does for our own Slack channels — kept here so the example needs nothing from
# the rest of the repo.
#
# Usage:
#   SIGNOZ_ENDPOINT=https://your-tenant.eu.signoz.cloud \
#   SIGNOZ_ACCESS_TOKEN=... \
#   ./apply-signoz-webhook.sh
#
# Or export them and run bare. Optional overrides:
#   WEBHOOK_NAME       (default: signoz-alerts)
#   WEBHOOK_WORKSPACE  (default: my-team)
#   CHANNEL_NAME       (default: wf-incident-triage)
#   SEND_RESOLVED      (default: false — see the note below)
#
# Behaviour:
#   - Reads the webhook's CURRENT ingest URL from Wayfinder, so you never paste it
#     around. This is the read sub-resource, not `wf rotate webhook` — it does not
#     mint a new URL, so re-running is safe and will not break a SigNoz channel
#     that is already pointed at the existing one.
#   - Creates the channel if absent; updates it only if the URL or the
#     send-resolved setting has drifted. Any other SigNoz channel is left alone.
#   - Never prints the ingest URL. It is a credential (see below).
#
# Requirements:
#   - jq, and an authenticated `wf` (`wf profiles` to check which tenant you are
#     pointed at).
#   - Permission to read the webhook's ingest URL. It is an ingest credential, so
#     RBAC grants it alongside `rotate` rather than to viewers — a viewer-only role
#     gets a permission error here.
#   - An ADMIN-role SigNoz API key (Settings -> Service Accounts). Editor keys
#     can manage alerts and dashboards but get 403 on /api/v1/channels.
#   - SigNoz v0.7.4 or later for webhook channels (any current Cloud tenant).
#
# TWO THINGS THIS SCRIPT DOES NOT DO:
#   1. It does not create the Wayfinder webhook — apply the manifests first
#      (`wf apply -f .`).
#   2. It does not route any alerts to the channel. Attach it to the alerts you
#      want triaged in the SigNoz UI (or your own alert IaC) once it exists.
#      Start narrow: every delivery starts a real AI investigation.
#
# On the ingest URL as a credential: SigNoz does not sign webhook bodies, so this
# example's Webhook uses `algorithm: none` and the secrecy of the URL is the only
# thing protecting the endpoint. SigNoz can send HTTP basic auth (or a bearer
# token in the password field), but Wayfinder's inbound webhook validates an HMAC
# signature over the body or nothing at all — there is no static-token mode to
# pair it with. So: treat the URL as a secret, never paste it into a terminal or
# a file, and rotate it (`wf rotate webhook`) if it leaks — then re-run this
# script to point SigNoz at the new one.

set -euo pipefail

cd "$(dirname "$0")"

: "${SIGNOZ_ENDPOINT:?SIGNOZ_ENDPOINT must be set}"
: "${SIGNOZ_ACCESS_TOKEN:?SIGNOZ_ACCESS_TOKEN must be set}"

WEBHOOK_NAME="${WEBHOOK_NAME:-signoz-alerts}"
WEBHOOK_WORKSPACE="${WEBHOOK_WORKSPACE:-my-team}"
CHANNEL_NAME="${CHANNEL_NAME:-wf-incident-triage}"
# Default OFF, unlike a Slack channel. The workflow's trigger only fires on
# `status == "firing"`, so a resolution would be delivered, recorded as a
# WebhookInvocation and then discarded — twice the traffic and storage for no
# behaviour. Set SEND_RESOLVED=true if you want the resolutions recorded anyway.
SEND_RESOLVED="${SEND_RESOLVED:-false}"

# Strip whitespace (including a CR from a CRLF paste) and any trailing slash. A
# stray newline here surfaces as curl's opaque "URL rejected: Port number was not
# a decimal number..." rather than anything pointing at the real cause.
SIGNOZ_ENDPOINT="$(printf '%s' "$SIGNOZ_ENDPOINT" | tr -d '[:space:]')"
SIGNOZ_ENDPOINT="${SIGNOZ_ENDPOINT%/}"

if [[ ! "$SIGNOZ_ENDPOINT" =~ ^https?://[^:/]+(:[0-9]+)?$ ]]; then
  echo "SIGNOZ_ENDPOINT looks malformed: '$SIGNOZ_ENDPOINT'" >&2
  echo "Expected: https://<host>[:<port>]  (no trailing path, no stray characters)" >&2
  exit 1
fi

if [[ "$SEND_RESOLVED" != "true" && "$SEND_RESOLVED" != "false" ]]; then
  echo "SEND_RESOLVED must be 'true' or 'false', got '$SEND_RESOLVED'" >&2
  exit 1
fi

for tool in jq wf; do
  if ! command -v "$tool" >/dev/null; then
    echo "$tool is required" >&2
    exit 1
  fi
done

# Read the webhook's CURRENT ingest URL. This is the read sub-resource — it does
# NOT rotate, so re-running this script never invalidates a URL SigNoz is already
# using. The URL is captured into a variable and never echoed; a failure here
# prints only the exit path, not the response body, so a partial response can't
# leak it into a log.
echo "==> Reading the ingest URL for webhook '$WEBHOOK_NAME' in workspace '$WEBHOOK_WORKSPACE'..."
if ! ingest_json=$(wf get webhook -w "$WEBHOOK_WORKSPACE" "$WEBHOOK_NAME" --subresource ingesturl -o json 2>/dev/null); then
  echo "    !! could not read the ingest URL." >&2
  echo "       Check that: the webhook exists (wf get webhooks -w $WEBHOOK_WORKSPACE)," >&2
  echo "       you are pointed at the right tenant (wf profiles), and your role can read" >&2
  echo "       an ingest URL (it is granted alongside rotate, not to viewers)." >&2
  exit 1
fi

WF_INGEST_URL=$(printf '%s' "$ingest_json" | jq -r '.url // empty')
if [[ -z "$WF_INGEST_URL" ]]; then
  echo "    !! the ingest URL came back empty — has the webhook been given one yet?" >&2
  echo "       Mint one with: wf rotate webhook $WEBHOOK_NAME -w $WEBHOOK_WORKSPACE" >&2
  exit 1
fi
unset ingest_json

api() {
  local method="$1" path="$2" body="${3:-}"
  local url="$SIGNOZ_ENDPOINT$path"
  # Auth is the SIGNOZ-API-KEY header, NOT `Authorization: Bearer` (that is for
  # JWT login tokens).
  if [[ -n "$body" ]]; then
    curl -fsS --show-error -X "$method" \
      -H "SIGNOZ-API-KEY: $SIGNOZ_ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "$url" || { echo "    !! $method $url failed" >&2; return 1; }
  else
    curl -fsS --show-error -X "$method" \
      -H "SIGNOZ-API-KEY: $SIGNOZ_ACCESS_TOKEN" \
      "$url" || { echo "    !! $method $url failed" >&2; return 1; }
  fi
}

echo "==> Reading existing channels from SigNoz..."
existing=$(api GET /api/v1/channels)

# The webhook channel payload, per
# https://signoz.io/docs/alerts-management/notification-channel/webhook/
#
# The URL field is `url` (not `api_url`, which is the Slack config's field), and
# the documented payload carries NO `type` key — the channel type is inferred
# from which `*_configs` array is present. http_config is omitted entirely: it
# only carries basic-auth credentials, which Wayfinder's ingest does not consume.
payload=$(jq -nc \
  --arg name "$CHANNEL_NAME" \
  --arg url "$WF_INGEST_URL" \
  --argjson send_resolved "$SEND_RESOLVED" \
  '{
    name: $name,
    webhook_configs: [{
      send_resolved: $send_resolved,
      url: $url
    }]
  }')

existing_id=$(echo "$existing" | jq -r --arg n "$CHANNEL_NAME" \
  'try (.data // .) | map(select(.name == $n)) | .[0].id // empty')

if [[ -z "$existing_id" ]]; then
  echo "    + $CHANNEL_NAME (creating)"
  api POST /api/v1/channels "$payload" >/dev/null
  echo
  echo "Done. Created '$CHANNEL_NAME'."
else
  # Drift check. The GET response shape for a channel is
  #   { id, name, type, data: "<stringified JSON of the inner config>", ... }
  # so the webhook_configs array lives inside `.data` as a JSON STRING, not at
  # the top level. The POST/PUT payload uses the un-stringified shape and the
  # server stringifies it on store — so the live side must `fromjson` through
  # `.data` before comparing, or every run would look like drift and PUT.
  current=$(echo "$existing" | jq -c --arg n "$CHANNEL_NAME" \
    'try (.data // .) | map(select(.name == $n)) | .[0]')
  current_url=$(echo "$current" | jq -r '(.data | fromjson).webhook_configs[0].url // ""')
  current_resolved=$(echo "$current" | jq -r '(.data | fromjson).webhook_configs[0].send_resolved // false | tostring')

  if [[ "$current_url" == "$WF_INGEST_URL" && "$current_resolved" == "$SEND_RESOLVED" ]]; then
    echo "    = $CHANNEL_NAME (unchanged)"
    echo
    echo "Done. Nothing to do."
  else
    echo "    ~ $CHANNEL_NAME (updating)"
    api PUT "/api/v1/channels/$existing_id" "$payload" >/dev/null
    echo
    echo "Done. Updated '$CHANNEL_NAME'."
  fi
fi

cat <<'NEXT'

Next:
  1. In SigNoz, attach this channel to the alerts you want triaged
     (Alerts -> the rule -> notification channels). Start with one.
  2. Watch it work, from the delivery down to the agent's findings:
       wf get webhookinvocations -w my-team
       wf get workflowinvocations -w my-team
       wf get aiconversations -w my-team
     A delivery that lands without firing a run means the workflow's trigger
     rejected it — expected for a resolution, or for an alert that is not
     firing.
NEXT
