"""Tests for POST /predict endpoint."""

import pytest
from app.schemas import ALLOWED_CATEGORIES


def test_predict_success_when_model_loaded(loaded_client):
    """POST /predict classifies text accurately into an allowed category with confidence."""
    payload = {"text": "Water leakage from washroom ceiling in hostel block room 204."}
    response = loaded_client.post("/predict", json=payload)

    assert response.status_code == 200
    data = response.json()

    assert data["predicted_category"] in ALLOWED_CATEGORIES
    assert data["predicted_category"] == "Hostel & Residence"
    assert isinstance(data["confidence"], float)
    assert 0.0 <= data["confidence"] <= 1.0
    assert data["model_version"] == "1.0.0"
    assert "warning" in data
    assert "heuristic" in data["warning"]


def test_predict_various_categories(loaded_client):
    """Verifies that diverse sample queries map to valid CampusPulse categories."""
    samples = [
        ("Final exam timetable clash for CS301 and CS305.", "Academic Affairs"),
        ("Chemistry laboratory fume hood exhaust fan broken.", "Infrastructure & Labs"),
        ("Silent reading room lights flickering and buzzing loudly.", "Library & Study Spaces"),
        ("Lunch served in mess was cold and watery dal unhygienic.", "Canteen & Dining"),
        ("Campus shuttle bus route 2 arrived 40 minutes late.", "Transit & Campus Grounds"),
    ]

    for text, expected_category in samples:
        resp = loaded_client.post("/predict", json={"text": text})
        assert resp.status_code == 200
        data = resp.json()
        assert data["predicted_category"] in ALLOWED_CATEGORIES
        assert data["predicted_category"] == expected_category
        assert data["confidence"] > 0.0


def test_predict_when_model_unloaded_returns_503(unloaded_client):
    """POST /predict must return 503 Service Unavailable when model is not loaded."""
    payload = {"text": "Wi-Fi in library 2nd floor is not working properly."}
    response = unloaded_client.post("/predict", json=payload)

    assert response.status_code == 503
    data = response.json()
    assert "not loaded" in data["detail"].lower()


@pytest.mark.parametrize(
    "invalid_text",
    [
        "",  # empty string
        "   ",  # whitespace only
        "abc",  # less than 5 characters
        "a" * 3001,  # exceeds maximum length of 3000 characters
    ],
)
def test_predict_validation_errors(loaded_client, invalid_text):
    """POST /predict rejects empty, whitespace, too short, and oversized inputs with 422."""
    payload = {"text": invalid_text}
    response = loaded_client.post("/predict", json=payload)

    assert response.status_code == 422


def test_predict_missing_text_field(loaded_client):
    """POST /predict rejects payload missing the required 'text' field."""
    response = loaded_client.post("/predict", json={})
    assert response.status_code == 422
