package com.campuspulse.controller;

import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.dto.analytics.AnalyticsSummary;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminAnalyticsController.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private ComplaintService complaintService;

    private static final List<String> MOCK_CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs"
    );

    @BeforeEach
    void setUp() {
        when(complaintService.getAllowedCategories()).thenReturn(MOCK_CATEGORIES);
    }

    @Test
    @DisplayName("GET /admin/analytics renders the dashboard with default 30 days and empty state when no data")
    void shouldRenderAnalyticsDashboardWithEmptyState() throws Exception {
        AnalyticsDashboardData emptyData = new AnalyticsDashboardData(
                new AnalyticsSummary(0, 0, 0, 0, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "",
                "",
                30
        );
        when(analyticsService.getAnalyticsData(null, null, 30)).thenReturn(emptyData);

        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics/index"))
                .andExpect(model().attributeExists("analytics"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attributeExists("statuses"))
                .andExpect(content().string(containsString("Campus Grievance Analytics")))
                .andExpect(content().string(containsString("Total Complaints")))
                .andExpect(content().string(containsString("Resolution Rate")))
                .andExpect(content().string(containsString("0.0%")));

        verify(analyticsService).getAnalyticsData(null, null, 30);
    }

    @Test
    @DisplayName("GET /admin/analytics with filters passes parsed parameters to service")
    void shouldPassFiltersToAnalyticsService() throws Exception {
        AnalyticsDashboardData filteredData = new AnalyticsDashboardData(
                new AnalyticsSummary(5, 0, 0, 0, 5),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "RESOLVED",
                "Hostel & Residence",
                7
        );
        when(analyticsService.getAnalyticsData(eq(ComplaintStatus.RESOLVED), eq("Hostel & Residence"), eq(7)))
                .thenReturn(filteredData);

        mockMvc.perform(get("/admin/analytics")
                        .param("status", "RESOLVED")
                        .param("category", "Hostel & Residence")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics/index"))
                .andExpect(model().attribute("selectedStatus", "RESOLVED"))
                .andExpect(model().attribute("selectedCategory", "Hostel & Residence"))
                .andExpect(model().attribute("selectedDays", 7));

        verify(analyticsService).getAnalyticsData(ComplaintStatus.RESOLVED, "Hostel & Residence", 7);
    }

    @Test
    @DisplayName("GET /admin/analytics handles invalid status filter string gracefully")
    void shouldHandleInvalidStatusFilterGracefully() throws Exception {
        AnalyticsDashboardData defaultData = new AnalyticsDashboardData(
                new AnalyticsSummary(0, 0, 0, 0, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "",
                "",
                30
        );
        when(analyticsService.getAnalyticsData(null, null, 30)).thenReturn(defaultData);

        mockMvc.perform(get("/admin/analytics").param("status", "INVALID_STATUS_NAME"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics/index"));

        verify(analyticsService).getAnalyticsData(null, null, 30);
    }
}
