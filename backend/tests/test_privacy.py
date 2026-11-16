from fastapi.testclient import TestClient
from app.main import app


def test_validation_errors_never_echo_url_tokens():
    with TestClient(app) as client:
        response = client.post("/check", json={"url": "https://person:PRIVATE@example.com/"})
    assert response.status_code == 422
    assert "PRIVATE" not in response.text
    assert "example.com" not in response.text
