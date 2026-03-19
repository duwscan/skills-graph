"""Unit tests for the FastAPI API layer."""

from __future__ import annotations

from unittest.mock import MagicMock

from fastapi.testclient import TestClient

from agents.skill_detection.schemas import SkillDetectionResult
from api.app import create_app
from api.deps import get_skill_detection_agent


def _mock_agent() -> MagicMock:
    agent = MagicMock()
    agent.detect.return_value = SkillDetectionResult(input_text="test")
    return agent


def test_health() -> None:
    app = create_app()
    client = TestClient(app, raise_server_exceptions=False)
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_detect_skills() -> None:
    app = create_app()
    mock = _mock_agent()
    app.dependency_overrides[get_skill_detection_agent] = lambda: mock
    client = TestClient(app, raise_server_exceptions=False)
    resp = client.post(
        "/api/v1/agents/skill-detection/detect",
        json={"text": "I work with Python"},
    )
    assert resp.status_code == 200
    mock.detect.assert_called_once_with("I work with Python")


def test_detect_skills_empty_text() -> None:
    app = create_app()
    mock = _mock_agent()
    app.dependency_overrides[get_skill_detection_agent] = lambda: mock
    client = TestClient(app, raise_server_exceptions=False)
    resp = client.post(
        "/api/v1/agents/skill-detection/detect",
        json={"text": ""},
    )
    assert resp.status_code == 422  # validation error: min_length=1
