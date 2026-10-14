package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.dto.analytics.AnalyticsSummary;
import com.campuspulse.dto.insights.AdminInsightsData;
import com.campuspulse.dto.insights.PeriodComparison;
import com.campuspulse.dto.insights.SlaSummary;
import com.campuspulse.dto.insights.StatusDistribution;
import com.campuspulse.dto.insights.TrendDirection;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.AiClassifierService;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.InsightsService;
import com.campuspulse.service.SlaEscalationService;
import com.campuspulse.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        HomeController.class,
        AuthController.class,
        StudentAuthController.class,
        ComplaintController.class,
        AdminComplaintController.class,
        AdminAnalyticsController.class,
        AdminInsightsController.class,
        ModeratorComplaintController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=admin123"
})
class BackNavigationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private AiClassifierService aiClassifierService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private InsightsService insightsService;

    @MockitoBean
    private SlaEscalationService slaEscalationService;

    @BeforeEach
    void setUp() {
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs", "Hostel & Residence"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Engineering", "Hostel Administration"));
    }

    @Test
    @DisplayName("Complaint submission form (/complaints/new) renders Back button with fallback to home")
    void complaintFormHasBackButton() throws Exception {
        mockMvc.perform(get("/complaints/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")))
                .andExpect(content().string(containsString("/js/back-navigation.js")));
    }

    @Test
    @DisplayName("Public complaint registry (/complaints) renders Back button with fallback to home")
    void complaintListHasBackButton() throws Exception {
        when(complaintService.getAllComplaints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")))
                .andExpect(content().string(containsString("/js/back-navigation.js")));
    }

    @Test
    @DisplayName("Complaint tracking page (/complaints/track) renders Back button with fallback to registry")
    void complaintTrackHasBackButton() throws Exception {
        mockMvc.perform(get("/complaints/track"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/complaints\"")))
                .andExpect(content().string(containsString("Back to Registry")))
                .andExpect(content().string(containsString("/js/back-navigation.js")));
    }

    @Test
    @DisplayName("Submission confirmation (/complaints/success/{id}) renders direct Back button to registry")
    void complaintSuccessHasDirectBackButton() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-TESTREF1",
                "Broken Light",
                "Needs bulb replacement in hostel corridor.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setId(1L);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setPriority(ComplaintPriority.MEDIUM);
        complaint.setCreatedAt(LocalDateTime.now());

        when(complaintService.getByReferenceId("CP-2026-TESTREF1")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/complaints/success/CP-2026-TESTREF1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-force-fallback=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/complaints\"")))
                .andExpect(content().string(containsString("Back to Registry")));
    }

    @Test
    @DisplayName("Admin login page (/login) renders Back button with fallback to home")
    void adminLoginHasBackButton() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")));
    }

    @Test
    @DisplayName("Student login page (/student/login) renders Back button with fallback to home")
    void studentLoginHasBackButton() throws Exception {
        mockMvc.perform(get("/student/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")));
    }

    @Test
    @DisplayName("Student register page (/student/register) renders Back button with fallback to sign-in")
    void studentRegisterHasBackButton() throws Exception {
        mockMvc.perform(get("/student/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/student/login\"")))
                .andExpect(content().string(containsString("Back to Sign In")));
    }

    @Test
    @DisplayName("Admin complaints queue (/admin/complaints) renders Back button with fallback to home")
    void adminComplaintsQueueHasBackButton() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", 0L);
        when(complaintService.getStatusCounts()).thenReturn(counts);
        when(complaintService.searchComplaints("", null, null, "", 0, 10))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/admin/complaints").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")));
    }

    @Test
    @DisplayName("Admin complaint detail (/admin/complaints/{id}) renders Back button with fallback to queue")
    void adminComplaintDetailHasBackButton() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-ADMINDTL",
                "Leaking Pipe",
                "Pipe is leaking in bathroom.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setId(42L);
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.setPriority(ComplaintPriority.HIGH);
        complaint.setCreatedAt(LocalDateTime.now());

        when(complaintService.getByReferenceId(eq("CP-2026-ADMINDTL"))).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory(eq("CP-2026-ADMINDTL"))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/complaints/CP-2026-ADMINDTL").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/admin/complaints\"")))
                .andExpect(content().string(containsString("Back to Complaints")));
    }

    @Test
    @DisplayName("Admin analytics (/admin/analytics) renders Back button with fallback to dashboard")
    void adminAnalyticsHasBackButton() throws Exception {
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

        mockMvc.perform(get("/admin/analytics").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/admin/complaints\"")))
                .andExpect(content().string(containsString("Back to Dashboard")));
    }

    @Test
    @DisplayName("Admin insights (/admin/insights) renders Back button with fallback to dashboard")
    void adminInsightsHasBackButton() throws Exception {
        AdminInsightsData insightsData = new AdminInsightsData(
                14,
                new PeriodComparison(14, 0L, 0L, 0L, 0.0, TrendDirection.STABLE, "0 complaints", "Oct 1", "Sep 15"),
                StatusDistribution.of(0, 0, 0, 0, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                0L
        );
        when(insightsService.getInsightsData(14)).thenReturn(insightsData);
        when(slaEscalationService.getSlaSummary()).thenReturn(SlaSummary.of(0, 0, 0, 0, 0));

        mockMvc.perform(get("/admin/insights").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/admin/complaints\"")))
                .andExpect(content().string(containsString("Back to Dashboard")));
    }

    @Test
    @DisplayName("Moderator dashboard (/moderator/complaints) renders Back button with fallback to home")
    void moderatorDashboardHasBackButton() throws Exception {
        when(complaintService.searchComplaints(eq(""), eq(null), eq(null), eq(""), eq(0), anyInt()))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));
        when(complaintService.getStatusCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getSlaCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getAllowedCategories()).thenReturn(Collections.emptyList());
        when(complaintService.getAllowedDepartments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/moderator/complaints").with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/\"")))
                .andExpect(content().string(containsString("Back to Home")));
    }

    @Test
    @DisplayName("Moderator detail (/moderator/complaints/{id}) renders Back button with fallback to /moderator/complaints")
    void moderatorDetailHasBackButton() throws Exception {
        Complaint c = new Complaint("CP-2026-MODBACK01", "Lab AC", "Desc", "Infrastructure & Labs", "Campus Facilities & Works");
        c.setId(10L);
        when(complaintService.getByReferenceId("CP-2026-MODBACK01")).thenReturn(Optional.of(c));
        when(complaintService.getStatusHistory("CP-2026-MODBACK01")).thenReturn(Collections.emptyList());
        when(slaEscalationService.getEscalationAudits("CP-2026-MODBACK01")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/moderator/complaints/CP-2026-MODBACK01").with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-back")))
                .andExpect(content().string(containsString("data-back-button=\"true\"")))
                .andExpect(content().string(containsString("data-fallback=\"/moderator/complaints\"")))
                .andExpect(content().string(containsString("Back to Complaints Queue")));
    }
}
