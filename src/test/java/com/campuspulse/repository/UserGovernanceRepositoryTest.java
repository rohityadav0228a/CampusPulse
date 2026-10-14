package com.campuspulse.repository;

import com.campuspulse.model.ComplaintDeletionAudit;
import com.campuspulse.model.Student;
import com.campuspulse.model.UserRoleAudit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserGovernanceRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRoleAuditRepository userRoleAuditRepository;

    @Autowired
    private ComplaintDeletionAuditRepository complaintDeletionAuditRepository;

    @Test
    @DisplayName("StudentRepository searches and counts users across roles correctly")
    void shouldSearchAndCountUsersByRole() {
        Student s1 = new Student("Alice Walker", "alice@campuspulse.edu", "CS-101", "pass");
        s1.setDepartment("Computer Science");
        s1.setRole("ROLE_STUDENT");
        studentRepository.save(s1);

        Student s2 = new Student("Bob Moderator", "bob@campuspulse.edu", "MOD-001", "pass");
        s2.setDepartment("Campus Facilities");
        s2.setRole("ROLE_MODERATOR");
        studentRepository.save(s2);

        Student s3 = new Student("Charlie Admin", "charlie@campuspulse.edu", "ADM-001", "pass");
        s3.setDepartment("Administration");
        s3.setRole("ROLE_ADMIN");
        studentRepository.save(s3);

        // Counts
        assertThat(studentRepository.countByRole("ROLE_STUDENT")).isEqualTo(1);
        assertThat(studentRepository.countByRole("ROLE_MODERATOR")).isEqualTo(1);
        assertThat(studentRepository.countByRoleIn(List.of("ROLE_ADMIN", "ADMIN"))).isEqualTo(1);

        // Search with query matching name
        Page<Student> searchName = studentRepository.searchUsers("Alice", null, PageRequest.of(0, 10));
        assertThat(searchName.getContent()).hasSize(1);
        assertThat(searchName.getContent().get(0).getEmail()).isEqualTo("alice@campuspulse.edu");

        // Search with role filter
        Page<Student> searchRole = studentRepository.searchUsers(null, "ROLE_MODERATOR", PageRequest.of(0, 10));
        assertThat(searchRole.getContent()).hasSize(1);
        assertThat(searchRole.getContent().get(0).getEmail()).isEqualTo("bob@campuspulse.edu");
    }

    @Test
    @DisplayName("UserRoleAuditRepository saves and queries immutable audit records")
    void shouldPersistAndRetrieveUserRoleAudits() {
        UserRoleAudit audit1 = new UserRoleAudit(1L, "alice@campuspulse.edu", "ROLE_STUDENT", "ROLE_MODERATOR", "admin", "Promotion");
        UserRoleAudit audit2 = new UserRoleAudit(2L, "bob@campuspulse.edu", "ROLE_MODERATOR", "ROLE_STUDENT", "admin", "Demotion");

        userRoleAuditRepository.save(audit1);
        userRoleAuditRepository.save(audit2);

        List<UserRoleAudit> recent = userRoleAuditRepository.findTop25ByOrderByChangedAtDesc();
        assertThat(recent).hasSize(2);

        List<UserRoleAudit> forAlice = userRoleAuditRepository.findByTargetEmailOrderByChangedAtDesc("alice@campuspulse.edu");
        assertThat(forAlice).hasSize(1);
        assertThat(forAlice.get(0).getNewRole()).isEqualTo("ROLE_MODERATOR");
    }

    @Test
    @DisplayName("ComplaintDeletionAuditRepository saves and queries surviving deletion audit records")
    void shouldPersistAndRetrieveComplaintDeletionAudits() {
        ComplaintDeletionAudit audit = new ComplaintDeletionAudit(
                100L,
                "CP-2026-DELTEST1",
                "Resolved AC repair",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student@campuspulse.edu",
                "admin",
                "Routine archival",
                LocalDateTime.now()
        );

        complaintDeletionAuditRepository.save(audit);

        assertThat(complaintDeletionAuditRepository.findByReferenceId("CP-2026-DELTEST1")).isPresent();
        assertThat(complaintDeletionAuditRepository.existsByReferenceId("CP-2026-DELTEST1")).isTrue();
    }
}
