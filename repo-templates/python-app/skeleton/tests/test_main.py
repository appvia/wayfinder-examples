import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


@pytest.mark.parametrize("path", ["/healthz", "/readyz"])
def test_probes_report_ok(path: str) -> None:
    response = client.get(path)

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_root_reports_the_service_identity() -> None:
    response = client.get("/")

    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "${{ .Inputs.serviceName }}"
    # Always reports something, even 'dev'.
    assert body["release"]
