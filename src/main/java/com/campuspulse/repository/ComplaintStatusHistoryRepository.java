package com.campuspulse.repository;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the ComplaintStatusHistory entity.
 */
@Repository
public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {

    /**
     * Finds all status history entries for a complaint entity, ordered chronologically (oldest first).
     *
     * @param complaint the target complaint
     * @return list of chronological history entries
     */
    List<ComplaintStatusHistory> findByComplaintOrderByCreatedAtAsc(Complaint complaint);

    /**
     * Finds all status history entries for a complaint reference ID, ordered chronologically (oldest first).
     *
     * @param referenceId unique reference identifier
     * @return list of chronological history entries
     */
    List<ComplaintStatusHistory> findByComplaint_ReferenceIdOrderByCreatedAtAsc(String referenceId);
}
