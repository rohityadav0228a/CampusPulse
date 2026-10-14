package com.campuspulse.service;

import com.campuspulse.model.Student;
import com.campuspulse.model.UserRoleAudit;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.repository.UserRoleAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing administrative user role governance, allowlist enforcement,
 * last-admin protection, audit trail persistence, and session invalidation.
 */
@Service
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    public static final String ROLE_STUDENT = "ROLE_STUDENT";
    public static final String ROLE_MODERATOR = "ROLE_MODERATOR";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static final Set<String> ALLOWED_ROLES = Set.of(
            ROLE_STUDENT,
            ROLE_MODERATOR,
            ROLE_ADMIN
    );

    private final StudentRepository studentRepository;
    private final UserRoleAuditRepository userRoleAuditRepository;
    private final SessionRegistry sessionRegistry;

    @Value("${campuspulse.admin.username:admin}")
    private String systemAdminUsername = "admin";

    public UserManagementService(StudentRepository studentRepository,
                                 UserRoleAuditRepository userRoleAuditRepository) {
        this(studentRepository, userRoleAuditRepository, null);
    }

    @Autowired
    public UserManagementService(StudentRepository studentRepository,
                                 UserRoleAuditRepository userRoleAuditRepository,
                                 @Autowired(required = false) SessionRegistry sessionRegistry) {
        this.studentRepository = studentRepository;
        this.userRoleAuditRepository = userRoleAuditRepository;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Searches registered accounts with optional role filtering and pagination.
     *
     * @param query case-insensitive search keyword across name, email, student ID, department
     * @param roleFilter optional role filter
     * @param page zero-based page number
     * @param size page size
     * @return page of matching student accounts
     */
    @Transactional(readOnly = true)
    public Page<Student> searchUsers(String query, String roleFilter, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0) ? 10 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(validPage, validSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        String effectiveQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String effectiveRole = (roleFilter != null && !roleFilter.isBlank() && !"ALL".equalsIgnoreCase(roleFilter.trim()))
                ? normalizeRole(roleFilter.trim())
                : null;

        return studentRepository.searchUsers(effectiveQuery, effectiveRole, pageable);
    }

    /**
     * Retrieves an account by its unique numeric ID.
     *
     * @param id user ID
     * @return Optional containing the student account if found
     */
    @Transactional(readOnly = true)
    public Optional<Student> getUserById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return studentRepository.findById(id);
    }

    /**
     * Updates an account's role in an atomic, audited transaction.
     * Validates allowlist, prevents redundant changes, protects the last active administrator,
     * persists an immutable UserRoleAudit record, and expires active sessions for the user.
     *
     * @param userId target user ID
     * @param newRole target role
     * @param actorUsername username of the administrator performing the action
     * @param reason administrative justification
     * @param confirmAdminPromotion explicit confirmation boolean required when promoting to ROLE_ADMIN
     * @return updated Student entity
     */
    @Transactional
    public Student updateUserRole(Long userId, String newRole, String actorUsername, String reason, boolean confirmAdminPromotion) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }
        if (newRole == null || newRole.isBlank()) {
            throw new IllegalArgumentException("New role must not be blank.");
        }

        String normalizedNewRole = normalizeRole(newRole);
        if (!ALLOWED_ROLES.contains(normalizedNewRole)) {
            throw new IllegalArgumentException("Invalid role: '" + newRole + "'. Allowed roles are " + ALLOWED_ROLES);
        }

        Student student = studentRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        String oldRole = normalizeRole(student.getRole());

        if (oldRole.equalsIgnoreCase(normalizedNewRole)) {
            throw new IllegalArgumentException("User already has role " + normalizedNewRole + ". No role change was performed.");
        }

        // Promoting to ROLE_ADMIN requires explicit administrative confirmation
        if (ROLE_ADMIN.equals(normalizedNewRole) && !confirmAdminPromotion) {
            throw new IllegalArgumentException("Promoting an account to Administrator requires explicit administrative confirmation.");
        }

        // Last Administrator Protection: prevent demoting or removing the last active administrator
        if (isAdminRole(oldRole) && !isAdminRole(normalizedNewRole)) {
            long activeAdminCount = studentRepository.countByRoleIn(List.of(ROLE_ADMIN, "ADMIN"));
            if (activeAdminCount <= 1) {
                log.warn("Rejected demotion of user id={} ({}) from {} to {}. Cannot demote the last active administrator.",
                        userId, student.getEmail(), oldRole, normalizedNewRole);
                throw new IllegalStateException("Cannot demote the last active administrator. At least one administrator account must remain active.");
            }
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : "ADMIN";

        String effectiveReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : "Administrative role adjustment by " + effectiveActor;

        student.setRole(normalizedNewRole);
        Student updated = studentRepository.save(student);

        // Record immutable audit history
        UserRoleAudit audit = new UserRoleAudit(
                updated.getId(),
                updated.getEmail(),
                oldRole,
                normalizedNewRole,
                effectiveActor,
                effectiveReason
        );
        userRoleAuditRepository.save(audit);

        // Revoke active sessions for the target user immediately
        invalidateUserSessions(updated.getEmail());

        log.info("Administrator '{}' successfully updated role for user id={} ({}) from {} to {} (reason: {})",
                effectiveActor, updated.getId(), updated.getEmail(), oldRole, normalizedNewRole, effectiveReason);

        return updated;
    }

    /**
     * Overload for updateUserRole defaulting confirmAdminPromotion to false.
     */
    @Transactional
    public Student updateUserRole(Long userId, String newRole, String actorUsername, String reason) {
        return updateUserRole(userId, newRole, actorUsername, reason, false);
    }

    /**
     * Calculates user role breakdown counts for administration overview.
     *
     * @return map of role names and totals
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getRoleMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("TOTAL", studentRepository.count());
        metrics.put("STUDENTS", studentRepository.countByRole(ROLE_STUDENT));
        metrics.put("MODERATORS", studentRepository.countByRole(ROLE_MODERATOR));
        metrics.put("ADMINS", studentRepository.countByRoleIn(List.of(ROLE_ADMIN, "ADMIN")));
        return metrics;
    }

    /**
     * Retrieves recent role audit trail records for system auditing.
     *
     * @return list of recent UserRoleAudit entries
     */
    @Transactional(readOnly = true)
    public List<UserRoleAudit> getRecentAuditHistory() {
        return userRoleAuditRepository.findTop25ByOrderByChangedAtDesc();
    }

    /**
     * Retrieves role change history for a specific user email.
     *
     * @param email user email
     * @return list of audit records
     */
    @Transactional(readOnly = true)
    public List<UserRoleAudit> getAuditHistoryForUser(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptyList();
        }
        return userRoleAuditRepository.findByTargetEmailOrderByChangedAtDesc(email.trim().toLowerCase());
    }

    /**
     * Invalidates all active HTTP sessions for the given user email using Spring Security's SessionRegistry.
     *
     * @param email target user email
     */
    public void invalidateUserSessions(String email) {
        if (sessionRegistry == null || email == null || email.isBlank()) {
            return;
        }

        String target = email.trim().toLowerCase();
        int expiredCount = 0;

        try {
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                String principalUsername = null;
                if (principal instanceof UserDetails ud) {
                    principalUsername = ud.getUsername();
                } else if (principal instanceof Principal p) {
                    principalUsername = p.getName();
                } else if (principal instanceof String s) {
                    principalUsername = s;
                }

                if (principalUsername != null && principalUsername.trim().equalsIgnoreCase(target)) {
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                    for (SessionInformation sessionInfo : sessions) {
                        sessionInfo.expireNow();
                        expiredCount++;
                    }
                }
            }
            if (expiredCount > 0) {
                log.info("Invalidated {} active session(s) for user {}", expiredCount, target);
            }
        } catch (Exception ex) {
            log.error("Error occurred while invalidating sessions for user {}: {}", target, ex.getMessage(), ex);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return ROLE_STUDENT;
        }
        String trimmed = role.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.startsWith("ROLE_")) {
            return "ROLE_" + trimmed;
        }
        return trimmed;
    }

    private boolean isAdminRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = normalizeRole(role);
        return ROLE_ADMIN.equals(normalized) || "ADMIN".equals(role.trim().toUpperCase(Locale.ROOT));
    }
}
