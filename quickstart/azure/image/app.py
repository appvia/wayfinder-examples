"""A tiny HTTP server for the Wayfinder Azure quick start.

On every request it writes a small blob and lists the container, using the
Wayfinder-provisioned managed identity attached to it — no stored credentials of
any kind. The result is returned as JSON so you can curl it and see the wiring.

Only the data-plane calls (azure-identity + azure-storage-blob) are Azure-specific;
the rest is plain stdlib. There is nothing Container-Instances-specific here — this
is an ordinary Linux container that would run anywhere.
"""

import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from azure.identity import DefaultAzureCredential
from azure.storage.blob import BlobServiceClient

ACCOUNT = os.environ["DATA_ACCOUNT"]
CONTAINER = os.environ["DATA_CONTAINER"]
PORT = int(os.environ.get("PORT", "80"))


def touch_blob() -> dict:
    # DefaultAzureCredential picks up AZURE_CLIENT_ID and authenticates as the
    # Wayfinder-provisioned managed identity attached to this container.
    credential = DefaultAzureCredential()
    service = BlobServiceClient(
        f"https://{ACCOUNT}.blob.core.windows.net", credential=credential
    )
    container = service.get_container_client(CONTAINER)

    # Write a small blob — proving this container can WRITE to the container.
    name = f"hello-{int(time.time())}.txt"
    container.upload_blob(
        name,
        b"Hello from a Wayfinder-wired Azure container!\n",
        overwrite=True,
    )

    # List what's there — proving it can READ too.
    blobs = [b.name for b in container.list_blobs()]

    return {
        "wrote": name,
        "account": ACCOUNT,
        "container": CONTAINER,
        "blobs": blobs,
        "note": "This container holds zero stored credentials. "
        "Wayfinder brokered its access to the storage account.",
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            body = json.dumps(touch_blob(), indent=2).encode()
            status = 200
        except Exception as exc:  # surface the failure to the caller as JSON
            body = json.dumps({"error": str(exc)}, indent=2).encode()
            status = 500
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # keep container logs quiet


if __name__ == "__main__":
    ThreadingHTTPServer(("", PORT), Handler).serve_forever()
