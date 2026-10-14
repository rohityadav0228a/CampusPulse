package com.campuspulse.service;

import com.campuspulse.model.Student;
import com.campuspulse.model.UserRoleAudit;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.repository.UserRoleAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRoleAuditRepository userRoleAuditRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    private UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        userManagementService = new UserManagementService(studentRepository, userRoleAuditRepository, sessionRegistry);
    }

    @Test
    @DisplayName("Successfully changes role between ROLE_STUDENT and ROLE_MODERATOR and audits the change")
    void shouldChangeRoleBetweenStudentAndModerator() {
        Student student = new Student("Alex Rivera", "alex@campuspulse.edu", "CS-101", "encodedPass");
        student.setRole("ROLE_STUDENT");
        student.setId(10L);

        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student result = userManagementService.updateUserRole(10L, "ROLE_MODERATOR", "admin_user", "Promoted to moderator");

        assertThat(result.getRole()).isEqualTo("ROLE_MODERATOR");
        verify(studentRepository).save(student);

        ArgumentCaptor<UserRoleAudit> auditCaptor = ArgumentCaptor.forClass(UserRoleAudit.class);
        verify(userRoleAuditRepository).save(auditCaptor.capture());

        UserRoleAudit savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getTargetUserId()).isEqualTo(10L);
        assertThat(savedAudit.getTargetEmail()).isEqualTo("alex@campuspulse.edu");
        assertThat(savedAudit.getOldRole()).isEqualTo("ROLE_STUDENT");
        assertThat(savedAudit.getNewRole()).isEqualTo("ROLE_MODERATOR");
        assertThat(savedAudit.getChangedBy()).isEqualTo("admin_user");
        assertThat(savedAudit.getReason()).isEqualTo("Promoted to moderator");
    }

    @Test
    @DisplayName("Promotes user to ROLE_ADMIN when explicit confirmation is provided")
    void shouldPromoteToAdminWithExplicitConfirmation() {
        Student moderator = new Student("Morgan Lee", "morgan@campuspulse.edu", "MOD-202", "encodedPass");
        moderator.setRole("ROLE_MODERATOR");
        moderator.setId(20L);

        when(studentRepository.findById(20L)).thenReturn(Optional.of(moderator));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student result = userManagementService.updateUserRole(20L, "ROLE_ADMIN", "super_admin", "Elevated to admin", true);

        assertThat(result.getRole()).isEqualTo("ROLE_ADMIN");
        verify(studentRepository).save(moderator);
        verify(userRoleAuditRepository).save(any(UserRoleAudit.class));
    }

    @Test
    @DisplayName("Rejects promotion to ROLE_ADMIN when explicit confirmation is false")
    void shouldRejectPromotionToAdminWithoutExplicitConfirmation() {
        Student student = new Student("Chris Green", "chris@campuspulse.edu", "CS-303", "encodedPass");
        student.setRole("ROLE_STUDENT");
        student.setId(30L);

        when(studentRepository.findById(30L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> userManagementService.updateUserRole(30L, "ROLE_ADMIN", "admin_user", "Elevation attempt", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit administrative confirmation");

        verify(studentRepository, never()).save(any());
        verify(userRoleAuditRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects role values not in the strict allowlist")
    void shouldRejectInvalidRoleValue() {
        assertThatThrownBy(() -> userManagementService.updateUserRole(1L, "ROLE_SUPERUSER", "admin", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");

        assertThatThrownBy(() -> userManagementService.updateUserRole(1L, "HACKER", "admin", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects redundant role update when user already possesses the target role")
    void shouldRejectRedundantRoleChange() {
        Student student = new Student("Taylor Smith", "taylor@campuspulse.edu", "CS-404", "pass");
        student.setRole("ROLE_STUDENT");
        student.setId(40L);

        when(studentRepository.findById(40L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> userManagementService.updateUserRole(40L, "ROLE_STUDENT", "admin", "No-op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has role ROLE_STUDENT");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Prevents demoting the last active administrator")
    void shouldPreventDemotingLastActiveAdministrator() {
        Student admin = new Student("Admin One", "admin1@campuspulse.edu", "ADM-001", "pass");
        admin.setRole("ROLE_ADMIN");
        admin.setId(50L);

        when(studentRepository.findById(50L)).thenReturn(Optional.of(admin));
        // Only 1 admin in the system
        when(studentRepository.countByRoleIn(anyList())).thenReturn(1L);

        assertThatThrownBy(() -> userManagementService.updateUserRole(50L, "ROLE_STUDENT", "admin1@campuspulse.edu", "Demoting self"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot demote the last active administrator");

        verify(studentRepository, never()).save(any());
        verify(userRoleAuditRepository, never()).save(any());
    }

    @Test
    @DisplayName("Allows demoting an administrator when multiple active administrators exist")
    void shouldAllowDemotingAdminWhenOtherAdminsExist() {
        Student admin = new Student("Admin Two", "admin2@campuspulse.edu", "ADM-002", "pass");
        admin.setRole("ROLE_ADMIN");
        admin.setId(60L);

        when(studentRepository.findById(60L)).thenReturn(Optional.of(admin));
        // 2 admins exist
        when(studentRepository.countByRoleIn(anyList())).thenReturn(2L);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student result = userManagementService.updateUserRole(60L, "ROLE_MODERATOR", "super_admin", "Stepping down to moderator");

        assertThat(result.getRole()).isEqualTo("ROLE_MODERATOR");
        verify(studentRepository).save(admin);
        verify(userRoleAuditRepository).save(any(UserRoleAudit.class));
    }

    @Test
    @DisplayName("Safely invalidates active authenticated user sessions upon role change")
    void shouldInvalidateActiveSessionsOnRoleChange() {
        Student student = new Student("Jordan Bell", "jordan@campuspulse.edu", "CS-505", "pass");
        student.setRole("ROLE_STUDENT");
        student.setId(70L);

        when(studentRepository.findById(70L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDetails principal = User.builder()
                .username("jordan@campuspulse.edu")
                .password("pass")
                .roles("STUDENT")
                .build();

        SessionInformation sessionInfo = new SessionInformation(principal, "sess-xyz-123", new Date());
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(sessionInfo));

        userManagementService.updateUserRole(70L, "ROLE_MODERATOR", "admin", "Promoted");

        assertThat(sessionInfo.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Calculates role metrics correctly")
    void shouldReturnRoleMetrics() {
        when(studentRepository.count()).thenReturn(100L);
        when(studentRepository.countByRole("ROLE_STUDENT")).thenReturn(85L);
        when(studentRepository.countByRole("ROLE_MODERATOR")).thenReturn(12L);
        when(studentRepository.countByRoleIn(List.of("ROLE_ADMIN", "ADMIN"))).thenReturn(3L);

        Map<String, Long> metrics = userManagementService.getRoleMetrics();

        assertThat(metrics.get("TOTAL")).isEqualTo(100L);
        assertThat(metrics.get("STUDENTS")).isEqualTo(85L);
        assertThat(metrics.get("MODERATORS")).isEqualTo(12L);
        assertThat(metrics.get("ADMINS")).isEqualTo(3L);
    }

    @Test
    @DisplayName("Searches users and applies pagination and role filter")
    void shouldSearchUsersWithFilters() {
        Student student = new Student("Alex Rivera", "alex@campuspulse.edu", "CS-101", "pass");
        Page<Student> page = new PageImpl<>(List.of(student));

        when(studentRepository.searchUsers(eq("alex"), eq("ROLE_STUDENT"), any(Pageable.class))).thenReturn(page);

        Page<Student> result = userManagementService.searchUsers("alex", "ROLE_STUDENT", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("alex@campuspulse.edu");
    }
}
