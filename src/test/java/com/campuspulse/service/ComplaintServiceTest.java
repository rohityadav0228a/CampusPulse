package com.campuspulse.service;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.repository.ComplaintRepository;
import com.campuspulse.repository.ComplaintStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite verifying ComplaintService business rules, atomic status transitions,
 * initial audit record generation, actor attribution, and rejection of unchanged statuses.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintStatusHistoryRepository historyRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ComplaintService complaintService;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createComplaint creates complaint and initial audit history attributed honestly to STUDENT")
    void createComplaintRecordsInitialStudentAuditHistory() {
        ComplaintForm form = new ComplaintForm();
        form.setTitle("Broken Laboratory Equipment");
        form.setDescription("Oscilloscope in Lab 3 is completely non-responsive.");
        form.setCategory("Infrastructure & Labs");
        form.setDepartment("Campus Facilities & Works");

        when(complaintRepository.existsByReferenceId(any())).thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });

        Complaint created = complaintService.createComplaint(form);

        assertNotNull(created);
        assertEquals(ComplaintStatus.SUBMITTED, created.getStatus());

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory initialAudit = historyCaptor.getValue();
        assertEquals(created, initialAudit.getComplaint());
        assertNull(initialAudit.getPreviousStatus(), "Previous status on initial creation must be null");
        assertEquals(ComplaintStatus.SUBMITTED, initialAudit.getNewStatus());
        assertEquals("STUDENT", initialAudit.getActorUsername(), "Initial submission actor must honestly be STUDENT");
        assertTrue(initialAudit.getNote().contains("Initial grievance submission"));
    }

    @Test
    @DisplayName("updateComplaintStatus transitions status and records audit history with actor and note")
    void updateComplaintStatusRecordsAuditHistoryWithActorAndNote() {
        Complaint complaint = new Complaint(
                "CP-2026-AUDIT001",
                "Library AC Not Working",
                "Temperature in 2nd floor reading hall is high.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.SUBMITTED);

        when(complaintRepository.findByReferenceId("CP-2026-AUDIT001")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateComplaintStatus(
                "CP-2026-AUDIT001",
                ComplaintStatus.UNDER_REVIEW,
                "admin_officer",
                "Assigned to HVAC maintenance team."
        );

        assertEquals(ComplaintStatus.UNDER_REVIEW, updated.getStatus());
        verify(complaintRepository, times(1)).save(complaint);

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals(complaint, audit.getComplaint());
        assertEquals(ComplaintStatus.SUBMITTED, audit.getPreviousStatus());
        assertEquals(ComplaintStatus.UNDER_REVIEW, audit.getNewStatus());
        assertEquals("admin_officer", audit.getActorUsername());
        assertEquals("Assigned to HVAC maintenance team.", audit.getNote());
    }

    @Test
    @DisplayName("updateComplaintStatus obtains actor from authenticated SecurityContext when actor not explicitly passed")
    void updateComplaintStatusResolvesActorFromSecurityContext() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "campus_director",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        Complaint complaint = new Complaint(
                "CP-2026-AUDIT002",
                "Hostel Mess Quality",
                "Food is cold.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setStatus(ComplaintStatus.SUBMITTED);

        when(complaintRepository.findByReferenceId("CP-2026-AUDIT002")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        complaintService.updateComplaintStatus("CP-2026-AUDIT002", ComplaintStatus.IN_PROGRESS);

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        assertEquals("campus_director", historyCaptor.getValue().getActorUsername());
    }

    @Test
    @DisplayName("updateComplaintStatus rejects unchanged status and creates no duplicate audit entry")
    void updateComplaintStatusRejectsUnchangedStatusWithoutAudit() {
        Complaint complaint = new Complaint(
                "CP-2026-AUDIT003",
                "Wi-Fi Connectivity",
                "Wi-Fi unstable in block A.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);

        when(complaintRepository.findByReferenceId("CP-2026-AUDIT003")).thenReturn(Optional.of(complaint));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateComplaintStatus(
                        "CP-2026-AUDIT003",
                        ComplaintStatus.UNDER_REVIEW,
                        "admin",
                        "Trying to set same status"
                )
        );

        assertTrue(ex.getMessage().contains("already in Under Review status"));
        verify(complaintRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateComplaintStatus rejects null status")
    void updateComplaintStatusRejectsNullStatus() {
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateComplaintStatus("CP-2026-AUDIT004", null, "admin", null)
        );
        verify(complaintRepository, never()).findByReferenceId(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateComplaintStatus fails when complaint does not exist")
    void updateComplaintStatusFailsWhenComplaintNotFound() {
        when(complaintRepository.findByReferenceId("CP-NON-EXISTENT")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateComplaintStatus("CP-NON-EXISTENT", ComplaintStatus.RESOLVED, "admin", null)
        );
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getStatusHistory retrieves chronological audit records ordered by creation timestamp")
    void getStatusHistoryRetrievesChronologicalEntries() {
        Complaint complaint = new Complaint("CP-2026-AUDIT005", "Title", "Desc", "Academic Affairs", "Academic Affairs Division");

        ComplaintStatusHistory h1 = new ComplaintStatusHistory(complaint, null, ComplaintStatus.SUBMITTED, "STUDENT", "Created");
        h1.setCreatedAt(LocalDateTime.now().minusHours(2));

        ComplaintStatusHistory h2 = new ComplaintStatusHistory(complaint, ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW, "admin", "Under review");
        h2.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(historyRepository.findByComplaint_ReferenceIdOrderByCreatedAtAsc("CP-2026-AUDIT005"))
                .thenReturn(List.of(h1, h2));

        List<ComplaintStatusHistory> history = complaintService.getStatusHistory("CP-2026-AUDIT005");

        assertEquals(2, history.size());
        assertEquals(ComplaintStatus.SUBMITTED, history.get(0).getNewStatus());
        assertEquals(ComplaintStatus.UNDER_REVIEW, history.get(1).getNewStatus());
        verify(historyRepository, times(1)).findByComplaint_ReferenceIdOrderByCreatedAtAsc("CP-2026-AUDIT005");
    }

    @Test
    @DisplayName("Failure during history save propagates exception causing transactional rollback")
    void failureDuringHistorySaveThrowsException() {
        Complaint complaint = new Complaint("CP-2026-ROLLBACK", "Title", "Desc", "Academic Affairs", "Academic Affairs Division");
        complaint.setStatus(ComplaintStatus.SUBMITTED);

        when(complaintRepository.findByReferenceId("CP-2026-ROLLBACK")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ComplaintStatusHistory.class)))
                .thenThrow(new RuntimeException("Simulated database failure during audit write"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                complaintService.updateComplaintStatus("CP-2026-ROLLBACK", ComplaintStatus.RESOLVED, "admin", "Done")
        );

        assertEquals("Simulated database failure during audit write", ex.getMessage());
        verify(notificationService, never()).queueAndDispatchStatusUpdate(any(), any());
    }

    @Test
    @DisplayName("createComplaint stores student email in complaint entity when provided in form")
    void createComplaintStoresStudentEmail() {
        ComplaintForm form = new ComplaintForm();
        form.setTitle("Hostel Room Leakage");
        form.setDescription("Roof is leaking in room 204.");
        form.setCategory("Hostel & Residence");
        form.setDepartment("Hostel Administration");
        form.setStudentEmail("resident204@college.edu");

        when(complaintRepository.existsByReferenceId(any())).thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint created = complaintService.createComplaint(form);

        assertNotNull(created);
        assertEquals("resident204@college.edu", created.getStudentEmail());
    }

    @Test
    @DisplayName("updateComplaintStatus invokes NotificationService when status changes")
    void updateComplaintStatusInvokesNotificationService() {
        Complaint complaint = new Complaint(
                "CP-2026-NOTIF099",
                "Wi-Fi Issue",
                "Wi-Fi not working.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student@college.edu"
        );
        complaint.setStatus(ComplaintStatus.SUBMITTED);

        when(complaintRepository.findByReferenceId("CP-2026-NOTIF099")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateComplaintStatus("CP-2026-NOTIF099", ComplaintStatus.UNDER_REVIEW, "admin", "Reviewing");

        assertEquals(ComplaintStatus.UNDER_REVIEW, updated.getStatus());
        verify(notificationService, times(1)).queueAndDispatchStatusUpdate(complaint, ComplaintStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("updateComplaintStatus does not trigger NotificationService when status is unchanged")
    void updateComplaintStatusDoesNotNotifyOnUnchangedStatus() {
        Complaint complaint = new Complaint(
                "CP-2026-NOTIF100",
                "Wi-Fi Issue",
                "Wi-Fi not working.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student@college.edu"
        );
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);

        when(complaintRepository.findByReferenceId("CP-2026-NOTIF100")).thenReturn(Optional.of(complaint));

        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateComplaintStatus("CP-2026-NOTIF100", ComplaintStatus.UNDER_REVIEW, "admin", "Same")
        );

        verify(notificationService, never()).queueAndDispatchStatusUpdate(any(), any());
    }
}
