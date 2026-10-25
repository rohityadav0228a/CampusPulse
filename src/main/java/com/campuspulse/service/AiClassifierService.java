package com.campuspulse.service;

import com.campuspulse.dto.AiClassificationResult;
import com.campuspulse.dto.PythonPredictRequest;
import com.campuspulse.dto.PythonPredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Service invoking the local Python ML microservice for advisory complaint category suggestion.
 * Uses Spring RestClient with timeout protection, fallback error handling, and strict category
 * validation against the predefined whitelist in ComplaintService.
 *
 * Privacy requirement: Never logs raw complaint text or sensitive user information.
 */
@Service
public class AiClassifierService {

    private static final Logger log = LoggerFactory.getLogger(AiClassifierService.class);

    private final RestClient restClient;
    private final ComplaintService complaintService;

    @org.springframework.beans.factory.annotation.Autowired
    public AiClassifierService(
            @Value("${campuspulse.ai.service-url:http://127.0.0.1:8000}") String aiServiceUrl,
            @Value("${campuspulse.ai.connect-timeout-ms:60000}") int connectTimeoutMs,
            @Value("${campuspulse.ai.read-timeout-ms:30000}") int readTimeoutMs,
            ComplaintService complaintService) {
        this(createRestClient(aiServiceUrl, connectTimeoutMs, readTimeoutMs), complaintService);
    }

    public AiClassifierService(RestClient restClient, ComplaintService complaintService) {
        this.restClient = restClient;
        this.complaintService = complaintService;
    }

    private static RestClient createRestClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        String normalizedBaseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.trim().replaceAll("/+$", "")
                : "http://127.0.0.1:8000";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Classifies complaint text into an advisory campus category using the local ML microservice.
     *
     * @param combinedText combined title and description text
     * @return AiClassificationResult containing prediction details or a graceful error status
     */
    public AiClassificationResult classify(String combinedText) {
        if (combinedText == null || combinedText.trim().length() < 5) {
            return AiClassificationResult.invalidInput("Text must contain at least 5 characters for prediction.");
        }

        log.info("Requesting AI category suggestion (text length: {} chars)", combinedText.trim().length());

        try {
            PythonPredictResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new PythonPredictRequest(combinedText.trim()))
                    .retrieve()
                    .body(PythonPredictResponse.class);

            if (response == null || response.predictedCategory() == null || response.predictedCategory().isBlank()) {
                log.warn("AI microservice returned empty prediction response");
                return AiClassificationResult.unavailable("Empty prediction received from AI microservice.");
            }

            String predictedCategory = response.predictedCategory().trim();

            // Validate against existing Java allowed-category list
            if (!complaintService.getAllowedCategories().contains(predictedCategory)) {
                log.warn("AI microservice returned category '{}' which is not in Java allowed categories list", predictedCategory);
                return AiClassificationResult.invalidCategory(predictedCategory);
            }

            log.info("AI classification successful: suggestedCategory='{}', confidence={}, modelVersion='{}'",
                    predictedCategory, response.confidence(), response.modelVersion());

            return AiClassificationResult.success(
                    predictedCategory,
                    response.confidence(),
                    response.warning(),
                    response.modelVersion()
            );

        } catch (ResourceAccessException ex) {
            log.warn("AI microservice connection or timeout error: {}", ex.getMessage());
            return AiClassificationResult.unavailable("AI service connection failed or timed out.");
        } catch (HttpStatusCodeException ex) {
            log.warn("AI microservice responded with HTTP error status {}", ex.getStatusCode());
            return AiClassificationResult.unavailable("AI service returned HTTP error " + ex.getStatusCode());
        } catch (Exception ex) {
            log.warn("Unexpected failure during AI classification: {}", ex.getMessage());
            return AiClassificationResult.unavailable("AI classification encountered an unexpected error.");
        }
    }
}
