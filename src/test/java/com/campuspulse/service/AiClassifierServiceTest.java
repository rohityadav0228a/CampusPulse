package com.campuspulse.service;

import com.campuspulse.dto.AiClassificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiClassifierServiceTest {

    private ComplaintService complaintService;
    private MockRestServiceServer mockServer;
    private AiClassifierService aiClassifierService;

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs",
            "Library & Study Spaces",
            "Canteen & Dining",
            "Transit & Campus Grounds"
    );

    @BeforeEach
    void setUp() {
        complaintService = mock(ComplaintService.class);
        when(complaintService.getAllowedCategories()).thenReturn(ALLOWED_CATEGORIES);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        aiClassifierService = new AiClassifierService(restClient, complaintService);
    }

    @Test
    @DisplayName("classify() returns success when Python microservice returns allowed category")
    void shouldReturnSuccessWhenMicroserviceReturnsValidCategory() {
        String input = "The computers in the college laboratory are not working.";

        mockServer.expect(requestTo("http://127.0.0.1:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.text").value(input))
                .andRespond(withSuccess(
                        """
                        {
                            "predicted_category": "Infrastructure & Labs",
                            "confidence": 0.8842,
                            "model_version": "1.0.0",
                            "warning": "Confidence is an uncalibrated heuristic estimate based on demo data, not a correctness guarantee."
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AiClassificationResult result = aiClassifierService.classify(input);

        mockServer.verify();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.category()).isEqualTo("Infrastructure & Labs");
        assertThat(result.confidence()).isEqualTo(0.8842);
        assertThat(result.modelVersion()).isEqualTo("1.0.0");
        assertThat(result.warning()).contains("heuristic estimate");
    }

    @Test
    @DisplayName("classify() returns INVALID_CATEGORY when microservice returns category not in Java whitelist")
    void shouldReturnInvalidCategoryWhenDisallowedCategoryReturned() {
        String input = "General administrative grievance text.";

        mockServer.expect(requestTo("http://127.0.0.1:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                            "predicted_category": "Unapproved External Category",
                            "confidence": 0.95,
                            "model_version": "1.0.0",
                            "warning": "Advisory only"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AiClassificationResult result = aiClassifierService.classify(input);

        mockServer.verify();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isCategoryInvalid()).isTrue();
        assertThat(result.category()).isEqualTo("Unapproved External Category");
        assertThat(result.errorMessage()).contains("whitelist");
    }

    @Test
    @DisplayName("classify() returns SERVICE_UNAVAILABLE when microservice returns 503")
    void shouldReturnServiceUnavailableWhenMicroserviceReturns503() {
        String input = "Hostel washroom cleaning request.";

        mockServer.expect(requestTo("http://127.0.0.1:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AiClassificationResult result = aiClassifierService.classify(input);

        mockServer.verify();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isUnavailable()).isTrue();
        assertThat(result.errorMessage()).contains("503");
    }

    @Test
    @DisplayName("classify() returns SERVICE_UNAVAILABLE when connection fails or times out")
    void shouldReturnServiceUnavailableWhenConnectionTimesOut() {
        RestClient.Builder failingBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        RestClient failingClient = failingBuilder.build();

        failingServer.expect(requestTo("http://127.0.0.1:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException(
                            "Connection timed out", new SocketTimeoutException("Read timed out"));
                });

        AiClassifierService timeoutService = new AiClassifierService(failingClient, complaintService);
        AiClassificationResult result = timeoutService.classify("Sample text that is long enough.");

        failingServer.verify();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isUnavailable()).isTrue();
        assertThat(result.errorMessage()).contains("timed out");
    }

    @Test
    @DisplayName("classify() returns INVALID_INPUT without HTTP call when input is under 5 characters")
    void shouldReturnInvalidInputWhenTextIsTooShort() {
        AiClassificationResult nullResult = aiClassifierService.classify(null);
        assertThat(nullResult.status()).isEqualTo(AiClassificationResult.Status.INVALID_INPUT);

        AiClassificationResult blankResult = aiClassifierService.classify("   ");
        assertThat(blankResult.status()).isEqualTo(AiClassificationResult.Status.INVALID_INPUT);

        AiClassificationResult shortResult = aiClassifierService.classify("Lab");
        assertThat(shortResult.status()).isEqualTo(AiClassificationResult.Status.INVALID_INPUT);
    }
}
