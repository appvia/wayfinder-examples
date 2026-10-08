import json
import os
import time

import azure.functions as func
from azure.identity import DefaultAzureCredential
from azure.storage.blob import BlobServiceClient

app = func.FunctionApp()

ACCOUNT = os.environ["DATA_ACCOUNT"]
CONTAINER = os.environ["DATA_CONTAINER"]


@app.route(route="hello", auth_level=func.AuthLevel.ANONYMOUS)
def hello(req: func.HttpRequest) -> func.HttpResponse:
    # DefaultAzureCredential picks up AZURE_CLIENT_ID and authenticates as the
    # Wayfinder-provisioned managed identity attached to this function — no stored
    # credentials of any kind.
    credential = DefaultAzureCredential()
    service = BlobServiceClient(
        f"https://{ACCOUNT}.blob.core.windows.net", credential=credential
    )
    container = service.get_container_client(CONTAINER)

    # Write a small blob — proving this function can WRITE to the container.
    name = f"hello-{int(time.time())}.txt"
    container.upload_blob(
        name,
        b"Hello from a Wayfinder-wired Azure Function!\n",
        overwrite=True,
    )

    # List what's there — proving it can READ too.
    blobs = [b.name for b in container.list_blobs()]

    body = {
        "wrote": name,
        "account": ACCOUNT,
        "container": CONTAINER,
        "blobs": blobs,
        "note": "This function holds zero stored credentials. "
        "Wayfinder brokered its access to the storage account.",
    }
    return func.HttpResponse(json.dumps(body, indent=2), mimetype="application/json")
