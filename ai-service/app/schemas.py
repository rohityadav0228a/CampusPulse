"""Pydantic schemas and category definitions for CampusPulse AI service."""

from typing import List, Optional
from pydantic import BaseModel, Field, field_validator

# Exact category definitions matching Java CampusPulse ComplaintService.ALLOWED_CATEGORIES
ALLOWED_CATEGORIES: List[str] = [
    "Academic Affairs",
    "Hostel & Residence",
    "Infrastructure & Labs",
    "Library & Study Spaces",
    "Canteen & Dining",
    "Transit & Campus Grounds",
]


class HealthResponse(BaseModel):
    """Health check response schema."""

    status: str = Field(..., description="Service status indicator ('ok')")
    service: str = Field("CampusPulse AI Classifier", description="Microservice name")
    model_loaded: bool = Field(..., description="Whether a trained model is currently loaded in memory")
    can_predict: bool = Field(..., description="True only if the service is operational and model is ready")
    model_version: Optional[str] = Field(None, description="Active model artifact version if loaded")
    supported_categories: List[str] = Field(
        default_factory=lambda: list(ALLOWED_CATEGORIES),
        description="Allowed complaint categories supported by the model",
    )


class PredictRequest(BaseModel):
    """Prediction request schema."""

    text: str = Field(
        ...,
        min_length=5,
        max_length=3000,
        description="Complaint text (title or detailed description) to classify",
        examples=["Water cooler in hostel block B is leaking and water is dirty."],
    )

    @field_validator("text")
    @classmethod
    def validate_non_whitespace(cls, value: str) -> str:
        trimmed = value.strip()
        if len(trimmed) < 5:
            raise ValueError("Complaint text must contain at least 5 non-whitespace characters")
        return trimmed


class PredictResponse(BaseModel):
    """Prediction response schema."""

    predicted_category: str = Field(
        ...,
        description="Classified complaint category from the allowed categories list",
    )
    confidence: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Heuristic model confidence score based on maximum class probability",
    )
    model_version: str = Field(
        ...,
        description="Version identifier of the loaded classification model",
    )
    warning: str = Field(
        "Confidence is an uncalibrated heuristic estimate based on demo data, not a correctness guarantee.",
        description="Heuristic confidence disclaimer",
    )


class ErrorResponse(BaseModel):
    """Standard error response schema."""

    detail: str = Field(..., description="Explanatory error message")
