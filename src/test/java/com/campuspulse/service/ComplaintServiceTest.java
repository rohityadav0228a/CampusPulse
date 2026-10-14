package com.campuspulse.service;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private com.campuspulse.repository.ComplaintDeletionAuditRepository complaintDeletionAuditRepository;

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
        assertEquals(ComplaintPriority.MEDIUM, created.getPriority(), "Default priority for newly submitted complaint must be MEDIUM");

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
    @DisplayName("createComplaint enqueues submission confirmation notification")
    void createComplaintEnqueuesSubmissionConfirmation() {
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
        verify(notificationService, times(1)).queueAndDispatchSubmissionConfirmation(created);
    }

    @Test
    @DisplayName("createComplaint safely preserves persisted complaint if confirmation notification fails")
    void createComplaintPreservesComplaintWhenNotificationFails() {
        ComplaintForm form = new ComplaintForm();
        form.setTitle("Hostel Room Leakage");
        form.setDescription("Roof is leaking in room 204.");
        form.setCategory("Hostel & Residence");
        form.setDepartment("Hostel Administration");
        form.setStudentEmail("resident204@college.edu");

        when(complaintRepository.existsByReferenceId(any())).thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Simulated notification outbox failure"))
                .when(notificationService).queueAndDispatchSubmissionConfirmation(any(Complaint.class));

        Complaint created = complaintService.createComplaint(form);

        assertNotNull(created);
        verify(complaintRepository, times(1)).save(any(Complaint.class));
        verify(historyRepository, times(1)).save(any(ComplaintStatusHistory.class));
        verify(notificationService, times(1)).queueAndDispatchSubmissionConfirmation(any(Complaint.class));
    }

    @Test
    @DisplayName("queueSubmissionConfirmation delegates to notification service")
    void queueSubmissionConfirmationDelegatesToNotificationService() {
        Complaint complaint = new Complaint(
                "CP-2026-CONFIRM1",
                "Hostel Leakage",
                "Water leak in hallway.",
                "Hostel & Residence",
                "Hostel Administration",
                "student@college.edu"
        );

        complaintService.queueSubmissionConfirmation(complaint);
        verify(notificationService, times(1)).queueAndDispatchSubmissionConfirmation(complaint);
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

    @Test
    @DisplayName("updateComplaintStatus to RESOLVED with resolution note enqueues status notification and records note")
    void updateComplaintStatusToResolvedWithNoteEnqueuesNotification() {
        Complaint complaint = new Complaint(
                "CP-2026-RESOLV01",
                "Projector Malfunction",
                "Display lamp is dead in Lab 402.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "instructor@college.edu"
        );
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintRepository.findByReferenceId("CP-2026-RESOLV01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint resolved = complaintService.updateComplaintStatus(
                "CP-2026-RESOLV01",
                ComplaintStatus.RESOLVED,
                "av_admin",
                "Replaced projector lamp and verified HDMI input."
        );

        assertEquals(ComplaintStatus.RESOLVED, resolved.getStatus());
        assertEquals("Replaced projector lamp and verified HDMI input.", resolved.getResolutionNote());
        assertNotNull(resolved.getResolvedAt());
        verify(notificationService, times(1)).queueAndDispatchStatusUpdate(complaint, ComplaintStatus.RESOLVED);
    }

    @Test
    @DisplayName("updateComplaintStatus to IN_PROGRESS enqueues status notification")
    void updateComplaintStatusToInProgressEnqueuesNotification() {
        Complaint complaint = new Complaint(
                "CP-2026-PROG001",
                "Wi-Fi Router Issue",
                "No connectivity in library.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student@college.edu"
        );
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);

        when(complaintRepository.findByReferenceId("CP-2026-PROG001")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateComplaintStatus(
                "CP-2026-PROG001",
                ComplaintStatus.IN_PROGRESS,
                "network_admin",
                "Dispatched technician to inspect AP 3."
        );

        assertEquals(ComplaintStatus.IN_PROGRESS, updated.getStatus());
        verify(notificationService, times(1)).queueAndDispatchStatusUpdate(complaint, ComplaintStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateResolutionNote updates note and preserves audit trail without queueing duplicate status update")
    void updateResolutionNotePreservesResolvedStateWithoutDuplicateNotification() {
        Complaint complaint = new Complaint(
                "CP-2026-RES007",
                "AC Issue",
                "AC leaking water.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student@college.edu"
        );
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolutionNote("Initial resolution note");

        when(complaintRepository.findByReferenceId("CP-2026-RES007")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateResolutionNote(
                "CP-2026-RES007",
                "Drainage pipe cleared and sealed properly.",
                "facilities_lead"
        );

        assertEquals("Drainage pipe cleared and sealed properly.", updated.getResolutionNote());
        assertEquals(ComplaintStatus.RESOLVED, updated.getStatus());
        // updateResolutionNote preserves student privacy and avoids spamming student with duplicate "Grievance Resolved" emails
        verify(notificationService, never()).queueAndDispatchStatusUpdate(any(), any());
    }

    @Test
    @DisplayName("searchComplaints normalizes negative page and clamps excessive page size")
    void searchComplaintsNormalizesNegativePageAndClampsSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(complaintRepository.searchWithFilters(any(), any(), any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(Page.empty());

        Page<Complaint> result = complaintService.searchComplaints("projector", ComplaintStatus.SUBMITTED, "Infrastructure & Labs", "OVERDUE", -3, 500);

        assertNotNull(result);
        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber(), "Negative page must be normalized to 0");
        assertEquals(ComplaintService.MAX_PAGE_SIZE, captured.getPageSize(), "Size exceeding MAX_PAGE_SIZE must be clamped to 100");
        assertNotNull(captured.getSort().getOrderFor("createdAt"));
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("createdAt").getDirection());
        assertNotNull(captured.getSort().getOrderFor("id"));
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("id").getDirection());
    }

    @Test
    @DisplayName("searchComplaints sets default page size when size is zero or negative")
    void searchComplaintsSetsDefaultPageSizeWhenSizeNonPositive() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(complaintRepository.searchWithFilters(any(), any(), any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(Page.empty());

        complaintService.searchComplaints("fan", null, null, null, 2, 0);

        Pageable captured = pageableCaptor.getValue();
        assertEquals(2, captured.getPageNumber());
        assertEquals(ComplaintService.DEFAULT_PAGE_SIZE, captured.getPageSize(), "Non-positive size must default to 10");
    }

    @Test
    @DisplayName("searchComplaints normalizes empty query and SLA status filter correctly")
    void searchComplaintsNormalizesSearchAndSlaParameters() {
        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> slaCaptor = ArgumentCaptor.forClass(String.class);

        when(complaintRepository.searchWithFilters(searchCaptor.capture(), any(), categoryCaptor.capture(), slaCaptor.capture(), any(), any(), any()))
                .thenReturn(Page.empty());

        // Blank search, blank category, "ALL" slaStatus -> should pass nulls to repository
        complaintService.searchComplaints("   ", null, "   ", "ALL", 0, 10);
        assertNull(searchCaptor.getValue(), "Blank search term should be passed as null");
        assertNull(categoryCaptor.getValue(), "Blank category should be passed as null");
        assertNull(slaCaptor.getValue(), "'ALL' SLA status should be passed as null");

        // Valid lowercase SLA status
        complaintService.searchComplaints("test", null, null, "response_overdue", 0, 10);
        assertEquals("RESPONSE_OVERDUE", slaCaptor.getValue());

        // Unknown SLA status
        complaintService.searchComplaints("test", null, null, "UNKNOWN_STATUS", 0, 10);
        assertNull(slaCaptor.getValue(), "Unknown SLA status should normalize to null");
    }

    @Test
    @DisplayName("searchComplaints with custom Pageable preserves primary sort and appends secondary tie-breaker")
    void searchComplaintsWithCustomPageableAddsTieBreaker() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(complaintRepository.searchWithFilters(any(), any(), any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(Page.empty());

        Pageable custom = PageRequest.of(1, 15, Sort.by(Sort.Direction.ASC, "title"));
        complaintService.searchComplaints("wifi", null, null, null, custom);

        Pageable captured = pageableCaptor.getValue();
        assertEquals(1, captured.getPageNumber());
        assertEquals(15, captured.getPageSize());
        assertEquals(Sort.Direction.ASC, captured.getSort().getOrderFor("title").getDirection());
        assertNotNull(captured.getSort().getOrderFor("id"), "Tie-breaker id sort must be added if missing");
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("id").getDirection());
    }

    @Test
    @DisplayName("updateComplaintStatus sets resolution note and resolvedAt timestamp when resolving")
    void updateComplaintStatusSetsResolutionNoteWhenResolving() {
        Complaint complaint = new Complaint("CP-2026-RES001", "Projector bulb", "Bulb burnt out", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintRepository.findByReferenceId("CP-2026-RES001")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateComplaintStatus("CP-2026-RES001", ComplaintStatus.RESOLVED, "admin", "Bulb replaced with OEM unit and tested.");

        assertEquals(ComplaintStatus.RESOLVED, updated.getStatus());
        assertNotNull(updated.getResolvedAt());
        assertEquals("Bulb replaced with OEM unit and tested.", updated.getResolutionNote());
    }

    @Test
    @DisplayName("updateComplaintStatus clears resolvedAt and resolutionNote when re-opening a resolved complaint")
    void updateComplaintStatusClearsResolutionNoteWhenReopening() {
        Complaint complaint = new Complaint("CP-2026-RES002", "Projector bulb", "Bulb burnt out", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now().minusDays(1));
        complaint.setResolutionNote("Replaced bulb.");

        when(complaintRepository.findByReferenceId("CP-2026-RES002")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateComplaintStatus("CP-2026-RES002", ComplaintStatus.IN_PROGRESS, "admin", "Issue recurred; re-investigating.");

        assertEquals(ComplaintStatus.IN_PROGRESS, updated.getStatus());
        assertNull(updated.getResolvedAt());
        assertNull(updated.getResolutionNote());
    }

    @Test
    @DisplayName("updateComplaintStatus rejects note exceeding maximum length of 1000 characters")
    void updateComplaintStatusRejectsNoteExceedingMaxLength() {
        Complaint complaint = new Complaint("CP-2026-RES003", "Projector", "Broken", "Infrastructure & Labs", "Campus Facilities & Works");
        when(complaintRepository.findByReferenceId("CP-2026-RES003")).thenReturn(Optional.of(complaint));

        String overlyLongNote = "A".repeat(1001);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateComplaintStatus("CP-2026-RES003", ComplaintStatus.UNDER_REVIEW, "admin", overlyLongNote)
        );
        assertTrue(ex.getMessage().contains("cannot exceed 1000 characters"));
    }

    @Test
    @DisplayName("updateResolutionNote updates note and records audit trail for resolved complaint")
    void updateResolutionNoteSuccessfullyUpdatesAndAudits() {
        Complaint complaint = new Complaint("CP-2026-RES004", "AC repair", "Not cooling", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolutionNote("Initial note");

        when(complaintRepository.findByReferenceId("CP-2026-RES004")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateResolutionNote("CP-2026-RES004", "Compressor replaced and refrigerant refilled.", "hvac_admin");

        assertEquals("Compressor replaced and refrigerant refilled.", updated.getResolutionNote());

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals(ComplaintStatus.RESOLVED, audit.getPreviousStatus());
        assertEquals(ComplaintStatus.RESOLVED, audit.getNewStatus());
        assertEquals("hvac_admin", audit.getActorUsername());
        assertTrue(audit.getNote().contains("Resolution note corrected: Compressor replaced and refrigerant refilled."));
    }

    @Test
    @DisplayName("updateResolutionNote rejects blank or overly long notes")
    void updateResolutionNoteRejectsInvalidNotes() {
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateResolutionNote("CP-2026-RES005", "   ", "admin")
        );

        String tooLong = "B".repeat(1001);
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateResolutionNote("CP-2026-RES005", tooLong, "admin")
        );
    }

    @Test
    @DisplayName("updateResolutionNote rejects updating non-resolved complaints")
    void updateResolutionNoteRejectsNonResolvedComplaint() {
        Complaint complaint = new Complaint("CP-2026-RES006", "AC repair", "Not cooling", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintRepository.findByReferenceId("CP-2026-RES006")).thenReturn(Optional.of(complaint));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateResolutionNote("CP-2026-RES006", "Valid note", "admin")
        );
        assertTrue(ex.getMessage().contains("Cannot update resolution note on a non-resolved complaint"));
    }

    @Test
    @DisplayName("updateDepartment reassigns department and records immutable audit history")
    void updateDepartmentSuccessfullyUpdatesAndAudits() {
        Complaint complaint = new Complaint("CP-2026-DEPT01", "Lab AC", "AC stopped", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintRepository.findByReferenceId("CP-2026-DEPT01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateDepartment("CP-2026-DEPT01", "Hostel Administration", "dept_admin");

        assertEquals("Hostel Administration", updated.getDepartment());

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals(ComplaintStatus.IN_PROGRESS, audit.getPreviousStatus());
        assertEquals(ComplaintStatus.IN_PROGRESS, audit.getNewStatus());
        assertEquals("dept_admin", audit.getActorUsername());
        assertTrue(audit.getNote().contains("Department reassigned from 'Campus Facilities & Works' to 'Hostel Administration'"));
    }

    @Test
    @DisplayName("updateDepartment rejects invalid department, blank input, or unchanged assignment")
    void updateDepartmentRejectsInvalidOrUnchangedValues() {
        Complaint complaint = new Complaint("CP-2026-DEPT02", "Lab AC", "AC stopped", "Infrastructure & Labs", "Campus Facilities & Works");

        // Blank referenceId or department
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateDepartment("", "Hostel Administration", "admin")
        );
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateDepartment("CP-2026-DEPT02", "   ", "admin")
        );

        // Department not in ALLOWED_DEPARTMENTS
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateDepartment("CP-2026-DEPT02", "External Arbitrary Vendor", "admin")
        );

        // Unchanged department
        when(complaintRepository.findByReferenceId("CP-2026-DEPT02")).thenReturn(Optional.of(complaint));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateDepartment("CP-2026-DEPT02", "Campus Facilities & Works", "admin")
        );
        assertTrue(ex.getMessage().contains("already assigned to Campus Facilities & Works"));
    }

    @Test
    @DisplayName("updateCategory corrects category and records immutable audit history")
    void updateCategorySuccessfullyUpdatesAndAudits() {
        Complaint complaint = new Complaint("CP-2026-CAT01", "Wi-Fi in library", "Disconnects", "Academic Affairs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);

        when(complaintRepository.findByReferenceId("CP-2026-CAT01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateCategory("CP-2026-CAT01", "Library & Study Spaces", "cat_admin");

        assertEquals("Library & Study Spaces", updated.getCategory());

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals(ComplaintStatus.UNDER_REVIEW, audit.getPreviousStatus());
        assertEquals(ComplaintStatus.UNDER_REVIEW, audit.getNewStatus());
        assertEquals("cat_admin", audit.getActorUsername());
        assertTrue(audit.getNote().contains("Category corrected from 'Academic Affairs' to 'Library & Study Spaces'"));
    }

    @Test
    @DisplayName("updateCategory rejects invalid category, blank input, or unchanged category")
    void updateCategoryRejectsInvalidOrUnchangedValues() {
        Complaint complaint = new Complaint("CP-2026-CAT02", "Mess food", "Cold food", "Canteen & Dining", "Hostel Administration");

        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateCategory("CP-2026-CAT02", "   ", "admin")
        );
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateCategory("CP-2026-CAT02", "Unknown External Category", "admin")
        );

        when(complaintRepository.findByReferenceId("CP-2026-CAT02")).thenReturn(Optional.of(complaint));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateCategory("CP-2026-CAT02", "Canteen & Dining", "admin")
        );
        assertTrue(ex.getMessage().contains("already categorized as Canteen & Dining"));
    }

    @Test
    @DisplayName("updatePriority updates priority, audits change, and preserves existing SLA deadlines")
    void updatePrioritySuccessfullyUpdatesAndAuditsWithoutAffectingSla() {
        Complaint complaint = new Complaint("CP-2026-PRIO01", "Power outage", "Whole block", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setPriority(ComplaintPriority.MEDIUM);

        LocalDateTime originalRespDeadline = complaint.getResponseDeadline();
        LocalDateTime originalResDeadline = complaint.getResolutionDeadline();

        when(complaintRepository.findByReferenceId("CP-2026-PRIO01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updatePriority("CP-2026-PRIO01", ComplaintPriority.CRITICAL, "prio_admin");

        assertEquals(ComplaintPriority.CRITICAL, updated.getPriority());
        assertEquals(originalRespDeadline, updated.getResponseDeadline(), "Response deadline must not change with priority update");
        assertEquals(originalResDeadline, updated.getResolutionDeadline(), "Resolution deadline must not change with priority update");

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals(ComplaintStatus.SUBMITTED, audit.getPreviousStatus());
        assertEquals(ComplaintStatus.SUBMITTED, audit.getNewStatus());
        assertEquals("prio_admin", audit.getActorUsername());
        assertTrue(audit.getNote().contains("Priority updated from MEDIUM to CRITICAL"));
    }

    @Test
    @DisplayName("updatePriority rejects null or unchanged priority")
    void updatePriorityRejectsNullOrUnchangedPriority() {
        Complaint complaint = new Complaint("CP-2026-PRIO02", "Water leak", "Floor 2", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setPriority(ComplaintPriority.HIGH);

        assertThrows(IllegalArgumentException.class, () ->
                complaintService.updatePriority("CP-2026-PRIO02", null, "admin")
        );

        when(complaintRepository.findByReferenceId("CP-2026-PRIO02")).thenReturn(Optional.of(complaint));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updatePriority("CP-2026-PRIO02", ComplaintPriority.HIGH, "admin")
        );
        assertTrue(ex.getMessage().contains("already set to High"));
    }

    @Test
    @DisplayName("updateAssignment updates department and category atomically with audit trail")
    void updateAssignmentSuccessfullyUpdatesBothAndAudits() {
        Complaint complaint = new Complaint("CP-2026-ASGN01", "Gym equipment", "Broken cable", "Infrastructure & Labs", "Campus Facilities & Works");

        when(complaintRepository.findByReferenceId("CP-2026-ASGN01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint updated = complaintService.updateAssignment(
                "CP-2026-ASGN01",
                "Hostel Administration",
                "Hostel & Residence",
                "assign_admin"
        );

        assertEquals("Hostel Administration", updated.getDepartment());
        assertEquals("Hostel & Residence", updated.getCategory());

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory audit = historyCaptor.getValue();
        assertEquals("assign_admin", audit.getActorUsername());
        assertTrue(audit.getNote().contains("Department reassigned from 'Campus Facilities & Works' to 'Hostel Administration'"));
        assertTrue(audit.getNote().contains("Category corrected from 'Infrastructure & Labs' to 'Hostel & Residence'"));
    }

    @Test
    @DisplayName("updateAssignment rejects when both department and category are unchanged")
    void updateAssignmentRejectsWhenBothUnchanged() {
        Complaint complaint = new Complaint("CP-2026-ASGN02", "Gym equipment", "Broken cable", "Infrastructure & Labs", "Campus Facilities & Works");

        when(complaintRepository.findByReferenceId("CP-2026-ASGN02")).thenReturn(Optional.of(complaint));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                complaintService.updateAssignment(
                        "CP-2026-ASGN02",
                        "Campus Facilities & Works",
                        "Infrastructure & Labs",
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("Both department and category are unchanged"));
    }

    @Test
    @DisplayName("searchComplaints passes department and priority filters to repository query")
    void searchComplaintsPassesDepartmentAndPriorityFilters() {
        Page<Complaint> mockPage = new PageImpl<>(List.of());
        when(complaintRepository.searchWithFilters(
                eq("search keyword"),
                eq(ComplaintStatus.IN_PROGRESS),
                eq("Infrastructure & Labs"),
                eq("Campus Facilities & Works"),
                eq(ComplaintPriority.HIGH),
                eq("OVERDUE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(mockPage);

        Page<Complaint> result = complaintService.searchComplaints(
                "search keyword",
                ComplaintStatus.IN_PROGRESS,
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                ComplaintPriority.HIGH,
                "OVERDUE",
                0,
                10
        );

        assertNotNull(result);
        verify(complaintRepository, times(1)).searchWithFilters(
                eq("search keyword"),
                eq(ComplaintStatus.IN_PROGRESS),
                eq("Infrastructure & Labs"),
                eq("Campus Facilities & Works"),
                eq(ComplaintPriority.HIGH),
                eq("OVERDUE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("Existing complaints without explicit priority default safely to MEDIUM")
    void existingComplaintCompatibilityDefaultsToMedium() {
        Complaint c = new Complaint();
        assertEquals(ComplaintPriority.MEDIUM, c.getPriority(), "No-arg constructor should initialize priority to MEDIUM");

        c.setPriority(null);
        assertEquals(ComplaintPriority.MEDIUM, c.getPriority(), "Null-safe getter must fallback to MEDIUM");
    }

    @Test
    @DisplayName("deleteResolvedComplaint soft-deletes resolved complaint and creates surviving deletion audit")
    void deleteResolvedComplaintSuccessfullySoftDeletesAndAudits() {
        Complaint complaint = new Complaint("CP-2026-DEL01", "Fixed lights", "All bulbs replaced", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setStudentEmail("student@campuspulse.edu");
        complaint.setId(99L);

        when(complaintRepository.findByReferenceId("CP-2026-DEL01")).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        Complaint deleted = complaintService.deleteResolvedComplaint("CP-2026-DEL01", "admin_super", "Resolved archival");

        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals("admin_super", deleted.getDeletedBy());
        assertEquals("Resolved archival", deleted.getDeletionReason());

        verify(complaintRepository).save(complaint);

        ArgumentCaptor<com.campuspulse.model.ComplaintDeletionAudit> auditCaptor =
                ArgumentCaptor.forClass(com.campuspulse.model.ComplaintDeletionAudit.class);
        verify(complaintDeletionAuditRepository).save(auditCaptor.capture());

        com.campuspulse.model.ComplaintDeletionAudit audit = auditCaptor.getValue();
        assertEquals(99L, audit.getComplaintId());
        assertEquals("CP-2026-DEL01", audit.getReferenceId());
        assertEquals("admin_super", audit.getDeletedBy());
        assertEquals("Resolved archival", audit.getDeletionReason());

        verify(historyRepository).save(any(ComplaintStatusHistory.class));
    }

    @Test
    @DisplayName("deleteResolvedComplaint rejects deletion when complaint status is not RESOLVED")
    void deleteResolvedComplaintRejectsUnresolved() {
        Complaint complaint = new Complaint("CP-2026-DEL02", "Pending issue", "Waiting", "Academic Affairs", "Academic Affairs Division");
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintRepository.findByReferenceId("CP-2026-DEL02")).thenReturn(Optional.of(complaint));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                complaintService.deleteResolvedComplaint("CP-2026-DEL02", "admin", "reason")
        );

        assertTrue(ex.getMessage().contains("Only resolved complaints can be deleted"));
        verify(complaintRepository, never()).save(any());
        verify(complaintDeletionAuditRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteResolvedComplaint rejects deletion when complaint is already deleted")
    void deleteResolvedComplaintRejectsAlreadyDeleted() {
        Complaint complaint = new Complaint("CP-2026-DEL03", "Old issue", "Done", "Academic Affairs", "Academic Affairs Division");
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setDeleted(true);

        when(complaintRepository.findByReferenceId("CP-2026-DEL03")).thenReturn(Optional.of(complaint));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                complaintService.deleteResolvedComplaint("CP-2026-DEL03", "admin", "reason")
        );

        assertTrue(ex.getMessage().contains("already been deleted"));
        verify(complaintRepository, never()).save(any());
        verify(complaintDeletionAuditRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteResolvedComplaint rejects non-existent complaint reference ID")
    void deleteResolvedComplaintRejectsNonExistent() {
        when(complaintRepository.findByReferenceId("CP-NON-EXISTENT")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                complaintService.deleteResolvedComplaint("CP-NON-EXISTENT", "admin", "reason")
        );

        verify(complaintRepository, never()).save(any());
        verify(complaintDeletionAuditRepository, never()).save(any());
    }
}

