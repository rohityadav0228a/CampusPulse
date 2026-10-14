package com.campuspulse.config;

import com.campuspulse.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Controlled startup runner that optionally provisions an initial moderator account
 * if valid credentials are provided through environment variables.
 * Does nothing if no moderator credentials are provided.
 */
@Component
public class ModeratorProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeratorProvisioningRunner.class);

    @Value("${campuspulse.moderator.email:}")
    private String moderatorEmail;

    @Value("${campuspulse.moderator.password:}")
    private String moderatorPassword;

    @Value("${campuspulse.moderator.name:Campus Moderator}")
    private String moderatorName;

    @Value("${campuspulse.moderator.department:Grievance Redressal Cell}")
    private String moderatorDepartment;

    private final StudentService studentService;

    public ModeratorProvisioningRunner(StudentService studentService) {
        this.studentService = studentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (moderatorEmail != null && !moderatorEmail.isBlank()
                && moderatorPassword != null && !moderatorPassword.isBlank()) {
            try {
                studentService.provisionModerator(moderatorName, moderatorEmail, moderatorDepartment, moderatorPassword);
                log.info("Controlled startup provisioning completed for moderator account: {}", moderatorEmail);
            } catch (Exception ex) {
                log.error("Failed to provision moderator account during startup: {}", ex.getMessage());
            }
        }
    }
}
