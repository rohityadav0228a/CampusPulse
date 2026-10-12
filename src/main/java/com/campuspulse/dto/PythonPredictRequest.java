package com.campuspulse.dto;

/**
 * Payload sent to the Python FastAPI microservice at POST /predict.
 */
public record PythonPredictRequest(String text) {
}
