package com.campuspulse.controller;

import com.campuspulse.dto.insights.AdminInsightsData;
import com.campuspulse.dto.insights.PeriodComparison;
import com.campuspulse.dto.insights.StatusDistribution;
import com.campuspulse.dto.insights.TrendDirection;
import com.campuspulse.service.InsightsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminInsightsController.class)
class AdminInsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightsService insightsService;

    @MockitoBean
    private com.campuspulse.service.SlaEscalationService slaEscalationService;

    private AdminInsightsData createMockInsightsData(int days) {
        return new AdminInsightsData(
                days,
                new PeriodComparison(
                        days,
                        0L,
                        0L,
                        0L,
                        0.0,
                        TrendDirection.STABLE,
                        "0 complaints in the past " + days + " days",
                        "Sep 28 – Oct 12",
                        "Sep 14 – Sep 28"
                ),
                StatusDistribution.of(0, 0, 0, 0, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                0L
        );
    }

    @Test
    @DisplayName("GET /admin/insights renders the insights dashboard with default 14 days and SLA metrics")
    void shouldRenderInsightsDashboardWithDefaults() throws Exception {
        AdminInsightsData mockData = createMockInsightsData(14);
        when(insightsService.getInsightsData(14)).thenReturn(mockData);
        when(slaEscalationService.getSlaSummary()).thenReturn(com.campuspulse.dto.insights.SlaSummary.of(0, 0, 0, 0, 0));

        mockMvc.perform(get("/admin/insights"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/insights/index"))
                .andExpect(model().attributeExists("insights"))
                .andExpect(model().attributeExists("comparison"))
                .andExpect(model().attributeExists("statusDistribution"))
                .andExpect(model().attributeExists("topCategories"))
                .andExpect(model().attributeExists("departmentBacklogs"))
                .andExpect(model().attributeExists("recommendations"))
                .andExpect(model().attributeExists("slaSummary"))
                .andExpect(model().attribute("selectedDays", 14))
                .andExpect(content().string(containsString("AI-Powered Grievance Insights &amp; Recommendations")))
                .andExpect(content().string(containsString("Period Volume Trend Analysis")))
                .andExpect(content().string(containsString("SLA Compliance Rate")));

        verify(insightsService).getInsightsData(14);
    }

    @Test
    @DisplayName("GET /admin/insights with valid timeframe parameter calls service with parsed days")
    void shouldAcceptCustomValidTimeframe() throws Exception {
        AdminInsightsData mockData = createMockInsightsData(30);
        when(insightsService.getInsightsData(30)).thenReturn(mockData);

        mockMvc.perform(get("/admin/insights").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/insights/index"))
                .andExpect(model().attribute("selectedDays", 30));

        verify(insightsService).getInsightsData(30);
    }

    @Test
    @DisplayName("GET /admin/insights with invalid or unusual timeframe falls back to default 14 days safely")
    void shouldNormalizeInvalidTimeframeToDefault() throws Exception {
        AdminInsightsData mockData = createMockInsightsData(14);
        when(insightsService.getInsightsData(14)).thenReturn(mockData);

        mockMvc.perform(get("/admin/insights").param("days", "-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/insights/index"))
                .andExpect(model().attribute("selectedDays", 14));

        verify(insightsService).getInsightsData(14);
    }
}
