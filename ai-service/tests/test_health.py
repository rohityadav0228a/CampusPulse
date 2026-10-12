"""Tests for GET /health endpoint."""

from app.schemas import ALLOWED_CATEGORIES


def test_health_when_model_unloaded(unloaded_client):
    """A running API with no loaded model must NOT claim that predictions are ready."""
    response = unloaded_client.get("/health")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "CampusPulse AI Classifier"
    assert data["model_loaded"] is False
    assert data["can_predict"] is False
    assert data["model_version"] is None
    assert data["supported_categories"] == ALLOWED_CATEGORIES


def test_health_when_model_loaded(loaded_client):
    """When model is loaded, health endpoint reports model_loaded=True and can_predict=True."""
    response = loaded_client.get("/health")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "ok"
    assert data["model_loaded"] is True
    assert data["can_predict"] is True
    assert data["model_version"] == "1.0.0"
    assert len(data["supported_categories"]) == 6
