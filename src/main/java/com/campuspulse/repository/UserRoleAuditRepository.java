package com.campuspulse.repository;

import com.campuspulse.model.UserRoleAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for role change audit records.
 */
@Repository
public interface UserRoleAuditRepository extends JpaRepository<UserRoleAudit, Long> {

    List<UserRoleAudit> findByTargetEmailOrderByChangedAtDesc(String targetEmail);

    List<UserRoleAudit> findTop25ByOrderByChangedAtDesc();
}
