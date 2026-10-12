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
}
