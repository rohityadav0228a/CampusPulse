package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.model.UserRoleAudit;
import com.campuspulse.service.UserManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    @DisplayName("Dashboard loads with populated model attributes including users, metrics, and audits")
    void shouldRenderUserGovernancePageWithModelAttributes() throws Exception {
        Student s1 = new Student("Alice Walker", "alice@campuspulse.edu", "CS-101", "pass");
        s1.setRole("ROLE_STUDENT");
        s1.setId(1L);

        when(userManagementService.searchUsers(eq("alice"), eq("ROLE_STUDENT"), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(s1)));
        when(userManagementService.getRoleMetrics())
                .thenReturn(Map.of("TOTAL", 10L, "STUDENTS", 8L, "MODERATORS", 1L, "ADMINS", 1L));
        when(userManagementService.getRecentAuditHistory())
                .thenReturn(List.of(new UserRoleAudit(1L, "alice@campuspulse.edu", "ROLE_STUDENT", "ROLE_MODERATOR", "testadmin", "Promoted")));

        mockMvc.perform(get("/admin/users")
                        .with(user("testadmin").roles("ADMIN"))
                        .param("q", "alice")
                        .param("role", "ROLE_STUDENT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/index"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("metrics"))
                .andExpect(model().attributeExists("recentAudits"))
                .andExpect(model().attribute("searchQuery", "alice"))
                .andExpect(model().attribute("roleFilter", "ROLE_STUDENT"));
    }

    @Test
    @DisplayName("Admin promotion passes confirmAdminPromotion true to service")
    void shouldPassExplicitPromotionConfirmationToService() throws Exception {
        Student promoted = new Student("Bob Clark", "bob@campuspulse.edu", "CS-202", "pass");
        promoted.setRole("ROLE_ADMIN");
        promoted.setId(2L);

        when(userManagementService.updateUserRole(eq(2L), eq("ROLE_ADMIN"), anyString(), any(), eq(true)))
                .thenReturn(promoted);

        mockMvc.perform(post("/admin/users/2/role")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("role", "ROLE_ADMIN")
                        .param("reason", "Carefully vetted admin promotion")
                        .param("confirmAdminPromotion", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userManagementService).updateUserRole(2L, "ROLE_ADMIN", "testadmin", "Carefully vetted admin promotion", true);
    }

    @Test
    @DisplayName("Service exception sets errorMessage flash attribute")
    void shouldCaptureServiceExceptionAsFlashError() throws Exception {
        when(userManagementService.updateUserRole(anyLong(), anyString(), anyString(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("Cannot demote the last active administrator."));

        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("role", "ROLE_STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "Cannot demote the last active administrator."));
    }
}
