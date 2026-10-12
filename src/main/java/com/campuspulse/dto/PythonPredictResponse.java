package com.campuspulse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Direct response payload received from the Python FastAPI microservice at POST /predict.
 */
public record PythonPredictResponse(
        @JsonProperty("predicted_category") String predictedCategory,
        Double confidence,
        @JsonProperty("model_version") String modelVersion,
        String warning
) {
}
