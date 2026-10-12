"""CampusPulse AI - Offline Baseline Model Training Script.

Trains a scikit-learn TF-IDF + LogisticRegression pipeline on synthetic
demonstration grievance data and persists the fitted model locally with joblib.
"""

import argparse
import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Any, Tuple

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

# Ensure allowed categories match CampusPulse exactly
ALLOWED_CATEGORIES = [
    "Academic Affairs",
    "Hostel & Residence",
    "Infrastructure & Labs",
    "Library & Study Spaces",
    "Canteen & Dining",
    "Transit & Campus Grounds",
]

MODEL_VERSION = "1.0.0"
MIN_SAMPLES_PER_CATEGORY = 5

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("campuspulse-train")


def validate_dataset(df: pd.DataFrame) -> None:
    """Validates structure, cleanliness, and category distribution of training data."""
    if "text" not in df.columns or "category" not in df.columns:
        raise ValueError("Dataset must contain 'text' and 'category' columns.")

    if df.empty:
        raise ValueError("Dataset is empty.")

    # Check for null or whitespace-only records
    cleaned_text = df["text"].astype(str).str.strip()
    cleaned_cat = df["category"].astype(str).str.strip()
    if (cleaned_text == "").any() or (cleaned_cat == "").any():
        raise ValueError("Dataset contains blank or whitespace-only rows.")

    # Validate categories against CampusPulse whitelist
    unique_categories = set(cleaned_cat.unique())
    unknown_categories = unique_categories - set(ALLOWED_CATEGORIES)
    if unknown_categories:
        raise ValueError(
            f"Dataset contains invalid categories not matching CampusPulse: {unknown_categories}. "
            f"Allowed: {ALLOWED_CATEGORIES}"
        )

    # Check sample counts per category
    counts = cleaned_cat.value_counts()
    for cat in ALLOWED_CATEGORIES:
        sample_count = counts.get(cat, 0)
        if sample_count < MIN_SAMPLES_PER_CATEGORY:
            raise ValueError(
                f"Category '{cat}' has only {sample_count} sample(s). "
                f"Minimum required is {MIN_SAMPLES_PER_CATEGORY} for stratified splitting."
            )

    logger.info("Dataset validation passed successfully across all %d categories.", len(ALLOWED_CATEGORIES))


def train_model(
    data_path: Path,
    model_output_path: Path,
    metadata_output_path: Path,
    random_state: int = 42,
) -> Dict[str, Any]:
    """Trains the baseline classifier, logs metrics, and exports local artifacts."""
    logger.info("Loading training data from %s", data_path)
    if not data_path.exists():
        raise FileNotFoundError(f"Training data file not found at: {data_path}")

    df = pd.read_csv(data_path)
    validate_dataset(df)

    df["text"] = df["text"].astype(str).str.strip()
    df["category"] = df["category"].astype(str).str.strip()

    # Stratified split for evaluation
    X = df["text"]
    y = df["category"]

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.2,
        random_state=random_state,
        stratify=y,
    )

    logger.info(
        "Train/test split completed: %d train samples, %d test samples across %d classes.",
        len(X_train),
        len(X_test),
        len(y.unique()),
    )

    # Build scikit-learn Pipeline
    pipeline = Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    ngram_range=(1, 2),
                    min_df=1,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    random_state=random_state,
                    max_iter=1000,
                    C=1.0,
                ),
            ),
        ]
    )

    # Evaluate on held-out test split
    pipeline.fit(X_train, y_train)
    y_pred = pipeline.predict(X_test)

    acc = float(accuracy_score(y_test, y_pred))
    macro_f1 = float(f1_score(y_test, y_pred, average="macro", zero_division=0))

    logger.info("Held-out Test Accuracy: %.4f", acc)
    logger.info("Held-out Macro F1-Score: %.4f", macro_f1)
    logger.warning(
        "DEMO LIMITATION NOTICE: Evaluation is conducted on a small synthetic demo dataset (%d samples). "
        "These metrics do not represent real-world student complaint accuracy or production readiness.",
        len(df),
    )

    # Train final model on full verified dataset for deployment
    final_pipeline = Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    ngram_range=(1, 2),
                    min_df=1,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    random_state=random_state,
                    max_iter=1000,
                    C=1.0,
                ),
            ),
        ]
    )
    final_pipeline.fit(X, y)

    # Persist model artifact locally
    model_output_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(final_pipeline, model_output_path)
    logger.info("Saved fitted pipeline to %s", model_output_path)

    # Persist metadata alongside model
    metadata: Dict[str, Any] = {
        "model_version": MODEL_VERSION,
        "algorithm": "TfidfVectorizer + LogisticRegression",
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "total_samples": int(len(df)),
        "categories": list(final_pipeline.classes_),
        "metrics": {
            "test_accuracy": acc,
            "test_macro_f1": macro_f1,
            "test_samples": int(len(X_test)),
        },
        "limitations": (
            "Trained exclusively on synthetic demo data. "
            "Probabilities are heuristic estimates, not calibrated likelihoods."
        ),
    }

    metadata_output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(metadata_output_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    logger.info("Saved model metadata to %s", metadata_output_path)

    return metadata


def main() -> None:
    """CLI entrypoint for offline model training."""
    base_dir = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Train baseline CampusPulse complaint classification model.")
    parser.add_argument(
        "--data-path",
        type=Path,
        default=base_dir / "data" / "demo_complaints.csv",
        help="Path to labeled CSV dataset",
    )
    parser.add_argument(
        "--model-path",
        type=Path,
        default=base_dir / "models" / "complaint_classifier.joblib",
        help="Path to save fitted model artifact",
    )
    parser.add_argument(
        "--metadata-path",
        type=Path,
        default=base_dir / "models" / "model_metadata.json",
        help="Path to save model metadata JSON",
    )
    args = parser.parse_args()

    train_model(
        data_path=args.data_path,
        model_output_path=args.model_path,
        metadata_output_path=args.metadata_path,
    )


if __name__ == "__main__":
    main()
