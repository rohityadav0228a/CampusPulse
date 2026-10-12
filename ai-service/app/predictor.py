"""Predictor module for loading local model artifacts and generating category predictions."""

import json
import logging
from pathlib import Path
from typing import Optional, Tuple, List

import joblib

logger = logging.getLogger("campuspulse-predictor")

DEFAULT_MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "complaint_classifier.joblib"
DEFAULT_METADATA_PATH = Path(__file__).resolve().parent.parent / "models" / "model_metadata.json"


class ModelNotLoadedError(Exception):
    """Raised when prediction is requested but no model artifact is loaded in memory."""
    pass


class ComplaintPredictor:
    """Encapsulates loading, readiness inspection, and inference for the complaint classifier."""

    def __init__(
        self,
        model_path: Path = DEFAULT_MODEL_PATH,
        metadata_path: Path = DEFAULT_METADATA_PATH,
    ) -> None:
        self.model_path = model_path
        self.metadata_path = metadata_path
        self.model = None
        self.model_version: Optional[str] = None
        self.categories: List[str] = []

    def is_ready(self) -> bool:
        """Returns True if a model artifact is successfully loaded and ready for inference."""
        return self.model is not None

    def load(
        self,
        model_path: Optional[Path] = None,
        metadata_path: Optional[Path] = None,
    ) -> bool:
        """Loads model artifact and metadata from disk if available."""
        target_model_path = model_path or self.model_path
        target_metadata_path = metadata_path or self.metadata_path

        if not target_model_path.exists():
            logger.warning("Model artifact not found at %s. Predictor is unloaded.", target_model_path)
            self.model = None
            self.model_version = None
            self.categories = []
            return False

        try:
            loaded_model = joblib.load(target_model_path)
            self.model = loaded_model
            self.categories = [str(c) for c in getattr(loaded_model, "classes_", [])]

            version = "1.0.0"
            if target_metadata_path.exists():
                try:
                    with open(target_metadata_path, "r", encoding="utf-8") as f:
                        meta = json.load(f)
                        version = meta.get("model_version", version)
                except Exception as ex:
                    logger.warning("Could not read metadata at %s: %s", target_metadata_path, ex)

            self.model_version = version
            logger.info("Successfully loaded model version %s with %d categories from %s",
                        self.model_version, len(self.categories), target_model_path)
            return True
        except Exception as ex:
            logger.error("Failed to load model from %s: %s", target_model_path, ex)
            self.model = None
            self.model_version = None
            self.categories = []
            return False

    def unload(self) -> None:
        """Explicitly unloads the model from memory (used in testing)."""
        self.model = None
        self.model_version = None
        self.categories = []
        logger.info("Model explicitly unloaded from memory.")

    def predict(self, text: str) -> Tuple[str, float, str]:
        """Generates category prediction and heuristic confidence score.

        Raises:
            ModelNotLoadedError: If the model has not been loaded.
            ValueError: If input text is empty or blank.
        """
        if not self.is_ready():
            raise ModelNotLoadedError("Classification model is not loaded or has not been trained yet.")

        cleaned = text.strip()
        if not cleaned:
            raise ValueError("Complaint text must not be empty.")

        # Class probabilities from LogisticRegression
        probabilities = self.model.predict_proba([cleaned])[0]
        best_idx = probabilities.argmax()
        predicted_category = str(self.model.classes_[best_idx])
        confidence = float(probabilities[best_idx])

        return predicted_category, round(confidence, 4), self.model_version or "1.0.0"


# Global predictor instance for FastAPI dependency injection and lifespan loading
predictor = ComplaintPredictor()
