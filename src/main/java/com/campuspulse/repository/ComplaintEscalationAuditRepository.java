package com.campuspulse.repository;

import com.campuspulse.model.ComplaintEscalationAudit;
import com.campuspulse.model.SlaEscalationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for immutable ComplaintEscalationAudit records.
 */
@Repository
public interface ComplaintEscalationAuditRepository extends JpaRepository<ComplaintEscalationAudit, Long> {

    /**
     * Finds all escalation audit entries for a complaint ordered chronologically.
     *
     * @param referenceId unique complaint reference ID
     * @return list of escalation audit entries
     */
    List<ComplaintEscalationAudit> findByComplaint_ReferenceIdOrderByTriggeredAtAsc(String referenceId);

    /**
     * Checks if an escalation event for the specified stage has already been recorded for this complaint.
     * Guarantees strict duplicate prevention.
     *
     * @param referenceId unique complaint reference ID
     * @param escalationStage escalation stage
     * @return true if an audit entry exists, false otherwise
     */
    boolean existsByComplaint_ReferenceIdAndEscalationStage(String referenceId, SlaEscalationStage escalationStage);
}
