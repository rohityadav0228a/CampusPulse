"""Pytest configuration and shared fixtures for CampusPulse AI microservice tests."""

import pytest
from pathlib import Path
from fastapi.testclient import TestClient

from app.main import app
from app.predictor import predictor
from training.train import train_model

BASE_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture(scope="session")
def trained_model_files(tmp_path_factory):
    """Trains a model artifact in a temporary directory for test isolation."""
    tmp_dir = tmp_path_factory.mktemp("models")
    model_path = tmp_dir / "complaint_classifier.joblib"
    metadata_path = tmp_dir / "model_metadata.json"
    data_path = BASE_DIR / "data" / "demo_complaints.csv"

    train_model(
        data_path=data_path,
        model_output_path=model_path,
        metadata_output_path=metadata_path,
    )
    return model_path, metadata_path


@pytest.fixture
def client():
    """Provides a FastAPI TestClient."""
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def loaded_client(client, trained_model_files):
    """Provides a TestClient with a verified model loaded into the predictor."""
    model_path, metadata_path = trained_model_files
    predictor.load(model_path=model_path, metadata_path=metadata_path)
    yield client
    predictor.unload()


@pytest.fixture
def unloaded_client(client):
    """Provides a TestClient with the predictor explicitly unloaded."""
    predictor.unload()
    yield client
