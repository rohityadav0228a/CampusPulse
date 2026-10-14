package com.campuspulse.repository;

import com.campuspulse.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Student account persistence.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Looks up a registered student by case-insensitive email address.
     *
     * @param email student email
     * @return matching Student entity if found
     */
    Optional<Student> findByEmailIgnoreCase(String email);

    /**
     * Checks if an account already exists with the given email address.
     *
     * @param email student email
     * @return true if an account exists
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Counts active accounts matching any of the specified roles.
     *
     * @param roles collection of role names
     * @return count of matching accounts
     */
    long countByRoleIn(java.util.Collection<String> roles);

    /**
     * Counts active accounts matching a specific role.
     *
     * @param role role name
     * @return count of matching accounts
     */
    long countByRole(String role);

    /**
     * Searches registered accounts across full name, email, student ID, and department with optional role filtering.
     *
     * @param query case-insensitive search keyword
     * @param role optional role filter
     * @param pageable pagination parameters
     * @return page of matching users
     */
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s WHERE " +
            "(:query IS NULL OR :query = '' OR " +
            " LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " (s.studentId IS NOT NULL AND LOWER(s.studentId) LIKE LOWER(CONCAT('%', :query, '%'))) OR " +
            " (s.department IS NOT NULL AND LOWER(s.department) LIKE LOWER(CONCAT('%', :query, '%')))) AND " +
            "(:role IS NULL OR :role = '' OR s.role = :role)")
    org.springframework.data.domain.Page<Student> searchUsers(
            @org.springframework.data.repository.query.Param("query") String query,
            @org.springframework.data.repository.query.Param("role") String role,
            org.springframework.data.domain.Pageable pageable);
}
