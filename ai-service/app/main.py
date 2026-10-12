"""CampusPulse AI Microservice - FastAPI Application.

Provides local HTTP endpoints for complaint category classification and health checks.
Execution is completely local; no external AI APIs or cloud services are invoked.
"""

from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.predictor import predictor, ModelNotLoadedError
from app.schemas import (
    HealthResponse,
    PredictRequest,
    PredictResponse,
    ErrorResponse,
    ALLOWED_CATEGORIES,
)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan managing model loading on startup."""
    # Attempt to load local model artifact on startup if present
    predictor.load()
    yield
    # Cleanup on shutdown
    predictor.unload()


app = FastAPI(
    title="CampusPulse AI Complaint Classification Service",
    description=(
        "Local NLP microservice that classifies student grievance text into standardized "
        "CampusPulse categories using TF-IDF and Logistic Regression."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

# CORS restricted to local development origins
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://127.0.0.1:8080", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get(
    "/health",
    response_model=HealthResponse,
    summary="Microservice Health & Readiness Check",
    tags=["System"],
)
def get_health() -> HealthResponse:
    """Returns the operational status of the service and indicates whether the model is ready.

    A running API without a loaded model will report can_predict=False and model_loaded=False.
    """
    model_ready = predictor.is_ready()
    return HealthResponse(
        status="ok",
        service="CampusPulse AI Classifier",
        model_loaded=model_ready,
        can_predict=model_ready,
        model_version=predictor.model_version if model_ready else None,
        supported_categories=ALLOWED_CATEGORIES,
    )


@app.post(
    "/predict",
    response_model=PredictResponse,
    responses={
        503: {"model": ErrorResponse, "description": "Model artifact not loaded or not trained yet"},
        422: {"description": "Validation error (e.g., text empty or exceeding maximum length)"},
    },
    summary="Predict Complaint Category",
    tags=["Inference"],
)
def predict_category(request: PredictRequest) -> PredictResponse:
    """Classifies grievance text into one of the 6 CampusPulse categories.

    Uses Logistic Regression class probabilities for a heuristic confidence score.
    Returns HTTP 503 if the model has not been trained or loaded.
    """
    if not predictor.is_ready():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Classification model is not loaded. Please train the model using training/train.py.",
        )

    try:
        category, confidence, version = predictor.predict(request.text)
        return PredictResponse(
            predicted_category=category,
            confidence=confidence,
            model_version=version,
        )
    except ModelNotLoadedError as ex:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(ex),
        ) from ex
    except ValueError as ex:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(ex),
        ) from ex


@app.get("/", tags=["System"])
def root():
    """Root info endpoint."""
    return {
        "service": "CampusPulse AI Microservice",
        "status": "running",
        "documentation": "/docs",
        "health": "/health",
        "predict": "/predict",
    }
