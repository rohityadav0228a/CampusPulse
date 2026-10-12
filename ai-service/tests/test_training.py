"""Tests for dataset validation, model training pipeline, and artifact generation."""

from pathlib import Path
import joblib
import pandas as pd
import pytest

from app.schemas import ALLOWED_CATEGORIES
from training.train import validate_dataset, train_model

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "demo_complaints.csv"


def test_demo_dataset_file_and_columns():
    """Demo complaints CSV must exist and contain required columns."""
    assert DATA_PATH.exists(), f"Demo dataset missing at {DATA_PATH}"

    df = pd.read_csv(DATA_PATH)
    assert not df.empty, "Dataset must not be empty"
    assert "text" in df.columns
    assert "category" in df.columns


def test_demo_dataset_all_categories_represented():
    """All 6 CampusPulse categories must be represented with at least 5 examples."""
    df = pd.read_csv(DATA_PATH)
    counts = df["category"].value_counts().to_dict()

    for cat in ALLOWED_CATEGORIES:
        assert cat in counts, f"Category '{cat}' missing from demo dataset"
        assert counts[cat] >= 5, f"Category '{cat}' has fewer than 5 samples ({counts[cat]})"

    # Ensure no alien categories exist
    assert set(df["category"].unique()) == set(ALLOWED_CATEGORIES)


def test_validate_dataset_rejects_invalid_categories():
    """Validation must fail if dataset contains categories outside the CampusPulse whitelist."""
    bad_df = pd.DataFrame(
        {
            "text": ["Some complaint text here that is long enough."],
            "category": ["NonExistentCategory"],
        }
    )
    with pytest.raises(ValueError, match="invalid categories"):
        validate_dataset(bad_df)


def test_validate_dataset_rejects_missing_columns():
    """Validation must fail if required columns are missing."""
    bad_df = pd.DataFrame({"wrong_column": [1, 2, 3]})
    with pytest.raises(ValueError, match="must contain 'text' and 'category'"):
        validate_dataset(bad_df)


def test_validate_dataset_rejects_blank_entries():
    """Validation must fail if rows contain empty strings."""
    bad_df = pd.DataFrame(
        {
            "text": ["   "],
            "category": ["Academic Affairs"],
        }
    )
    with pytest.raises(ValueError, match="blank or whitespace"):
        validate_dataset(bad_df)


def test_train_model_produces_valid_artifact(tmp_path):
    """train_model runs end-to-end, logs metrics, and outputs loadable artifacts."""
    model_output = tmp_path / "model.joblib"
    metadata_output = tmp_path / "metadata.json"

    meta = train_model(
        data_path=DATA_PATH,
        model_output_path=model_output,
        metadata_output_path=metadata_output,
        random_state=42,
    )

    assert model_output.exists()
    assert metadata_output.exists()
    assert meta["model_version"] == "1.0.0"
    assert "test_accuracy" in meta["metrics"]
    assert "test_macro_f1" in meta["metrics"]
    assert 0.0 <= meta["metrics"]["test_accuracy"] <= 1.0

    # Ensure model is loadable and performs inference
    loaded = joblib.load(model_output)
    proba = loaded.predict_proba(["Classroom lights flickering in lecture hall."])
    assert len(proba[0]) == len(ALLOWED_CATEGORIES)
