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
}
