package com.campuspulse.repository;

import com.campuspulse.model.ComplaintDeletionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for surviving complaint deletion audits.
 */
@Repository
public interface ComplaintDeletionAuditRepository extends JpaRepository<ComplaintDeletionAudit, Long> {

    Optional<ComplaintDeletionAudit> findByReferenceId(String referenceId);

    List<ComplaintDeletionAudit> findTop50ByOrderByDeletedAtDesc();

    boolean existsByReferenceId(String referenceId);
}
