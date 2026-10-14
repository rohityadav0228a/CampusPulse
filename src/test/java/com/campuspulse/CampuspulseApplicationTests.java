package com.campuspulse;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost"
})
class CampuspulseApplicationTests {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private com.campuspulse.service.PasswordResetService passwordResetService;

    @Autowired
    private com.campuspulse.service.StudentService studentService;

    @Autowired
    private com.campuspulse.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        assertThat(complaintService).isNotNull();
    }

    @Test
    @DisplayName("End-to-End Critical Workflow Verification: Submission, Privacy, Tracking, Admin Mutations, SLA Deadlines, Audit Trail")
    void verifyEndToEndCriticalWorkflows() {
        // 1. Student submits a complaint
        ComplaintForm form = new ComplaintForm(
                "Hostel Water Disruption in Block B",
                "Severe plumbing leak detected on the 2nd floor corridor.",
                "Hostel & Residence",
                "Hostel Administration",
                "student.doe@campus.edu"
        );

        Complaint submitted = complaintService.createComplaint(form);
        assertThat(submitted).isNotNull();
        assertThat(submitted.getReferenceId()).matches("^CP-\\d{4}-[A-Z0-9]{8,10}$");
        assertThat(submitted.getStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
        assertThat(submitted.getPriority()).isEqualTo(ComplaintPriority.MEDIUM);

        String refId = submitted.getReferenceId();
        LocalDateTime originalResponseDeadline = submitted.getResponseDeadline();
        LocalDateTime originalResolutionDeadline = submitted.getResolutionDeadline();
        assertThat(originalResponseDeadline).isNotNull();
        assertThat(originalResolutionDeadline).isNotNull();

        // 2. Privacy: Verify masked email hides raw email address
        assertThat(submitted.getMaskedStudentEmail()).isEqualTo("s***@campus.edu");
        assertThat(submitted.getMaskedStudentEmail()).doesNotContain("student.doe");

        // 3. Student tracks complaint and inspects initial history
        List<ComplaintStatusHistory> initialHistory = complaintService.getStatusHistory(refId);
        assertThat(initialHistory).hasSize(1);
        assertThat(initialHistory.get(0).getNewStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
        assertThat(initialHistory.get(0).getActorUsername()).isEqualTo("STUDENT");

        // 4. Admin updates status to UNDER_REVIEW
        Complaint underReview = complaintService.updateComplaintStatus(refId, ComplaintStatus.UNDER_REVIEW, "admin_user", "Assigned to plumbing inspection team.");
        assertThat(underReview.getStatus()).isEqualTo(ComplaintStatus.UNDER_REVIEW);
        assertThat(underReview.getAcknowledgedAt()).isNotNull();

        // 5. Admin updates department, category, and priority
        Complaint deptUpdated = complaintService.updateDepartment(refId, "Campus Facilities & Works", "admin_user");
        assertThat(deptUpdated.getDepartment()).isEqualTo("Campus Facilities & Works");

        Complaint catUpdated = complaintService.updateCategory(refId, "Infrastructure & Labs", "admin_user");
        assertThat(catUpdated.getCategory()).isEqualTo("Infrastructure & Labs");

        Complaint priorityUpdated = complaintService.updatePriority(refId, ComplaintPriority.CRITICAL, "admin_user");
        assertThat(priorityUpdated.getPriority()).isEqualTo(ComplaintPriority.CRITICAL);

        // 6. SLA deadline preservation: Priority changes must NOT alter existing deadlines
        assertThat(priorityUpdated.getResponseDeadline()).isEqualTo(originalResponseDeadline);
        assertThat(priorityUpdated.getResolutionDeadline()).isEqualTo(originalResolutionDeadline);

        // 7. Admin resolves complaint with resolution note
        Complaint resolved = complaintService.updateComplaintStatus(refId, ComplaintStatus.RESOLVED, "admin_user", "Replaced main corridor valve and verified flow.");
        assertThat(resolved.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getResolutionNote()).isEqualTo("Replaced main corridor valve and verified flow.");

        // 8. Admin corrects resolution note
        Complaint noteCorrected = complaintService.updateResolutionNote(refId, "Valve replaced and pressure tested by civil team.", "admin_user");
        assertThat(noteCorrected.getResolutionNote()).isEqualTo("Valve replaced and pressure tested by civil team.");

        // 9. Verify full audit trail fidelity
        List<ComplaintStatusHistory> fullHistory = complaintService.getStatusHistory(refId);
        assertThat(fullHistory).hasSize(7);
        assertThat(fullHistory.get(0).getNewStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
        assertThat(fullHistory.get(1).getNewStatus()).isEqualTo(ComplaintStatus.UNDER_REVIEW);
        assertThat(fullHistory.get(2).getNote()).contains("Department reassigned");
        assertThat(fullHistory.get(3).getNote()).contains("Category corrected");
        assertThat(fullHistory.get(4).getNote()).contains("Priority updated");
        assertThat(fullHistory.get(5).getNewStatus()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(fullHistory.get(6).getNote()).contains("Resolution note corrected");
    }

    @Test
    @DisplayName("End-to-End Password Recovery Lifecycle: Request, Hash Storage, Atomic Consumption, BCrypt Update, Single-Use Invalidation")
    void verifyPasswordRecoveryEndToEndWorkflow() {
        // 1. Provision a student account
        com.campuspulse.dto.StudentRegisterForm registerForm = new com.campuspulse.dto.StudentRegisterForm(
                "Jane Doe",
                "jane.recovery@campuspulse.edu",
                "STU-REC-01",
                "InitialPass123!",
                "InitialPass123!"
        );
        com.campuspulse.model.Student registeredStudent = studentService.registerStudent(registerForm);
        assertThat(registeredStudent).isNotNull();
        assertThat(passwordEncoder.matches("InitialPass123!", registeredStudent.getPassword())).isTrue();

        // 2. Request password reset
        passwordResetService.requestPasswordReset("jane.recovery@campuspulse.edu", "127.0.0.1");

        // 3. Inspect token in database
        java.util.List<com.campuspulse.model.PasswordResetToken> activeTokens =
                passwordResetTokenRepository.findByEmailIgnoreCaseAndUsedFalse("jane.recovery@campuspulse.edu");
        assertThat(activeTokens).hasSize(1);
        com.campuspulse.model.PasswordResetToken tokenRecord = activeTokens.get(0);
        assertThat(tokenRecord.getTokenHash()).hasSize(64);
        assertThat(tokenRecord.isUsed()).isFalse();
        assertThat(tokenRecord.isValid()).isTrue();

        // 4. Validate bad token rejects
        assertThat(passwordResetService.isTokenValid("non-existent-raw-token")).isFalse();

        // 5. Simulate reset execution using the saved token record hash
        // (For testing reset execution directly via atomic consumption, we pass the raw token if known,
        // or execute resetPassword with the corresponding raw token generated)
        byte[] dummyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(dummyBytes);
        String testRawToken = java.util.HexFormat.of().formatHex(dummyBytes);
        String testHash = passwordResetService.hashToken(testRawToken);

        com.campuspulse.model.PasswordResetToken directToken = new com.campuspulse.model.PasswordResetToken(
                testHash,
                "jane.recovery@campuspulse.edu",
                registeredStudent.getId(),
                LocalDateTime.now().plusMinutes(15),
                "127.0.0.1"
        );
        passwordResetTokenRepository.save(directToken);

        assertThat(passwordResetService.isTokenValid(testRawToken)).isTrue();

        // 6. Reset password with new password
        com.campuspulse.model.Student updatedStudent = passwordResetService.resetPassword(testRawToken, "BrandNewSecurePass789!");
        assertThat(updatedStudent).isNotNull();
        assertThat(passwordEncoder.matches("BrandNewSecurePass789!", updatedStudent.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("InitialPass123!", updatedStudent.getPassword())).isFalse();
        assertThat(updatedStudent.getRole()).isEqualTo("ROLE_STUDENT");

        // 7. Verify token was atomically consumed
        com.campuspulse.model.PasswordResetToken consumedToken = passwordResetTokenRepository.findByTokenHash(testHash).orElseThrow();
        assertThat(consumedToken.isUsed()).isTrue();
        assertThat(consumedToken.getUsedAt()).isNotNull();
        assertThat(passwordResetService.isTokenValid(testRawToken)).isFalse();

        // 8. Verify token reuse is strictly rejected
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.resetPassword(testRawToken, "AnotherPassword123!"));
    }
}
