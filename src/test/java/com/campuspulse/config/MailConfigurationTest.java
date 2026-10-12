package com.campuspulse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Spring Boot loads the Gmail SMTP configuration from application.properties:
 * Host: smtp.gmail.com, Port: 587, Username: rohityaduvanshi438@gmail.com,
 * Auth: true, STARTTLS required: true, and From address matching the authenticated account.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mailcfgdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MailConfigurationTest {

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private int mailPort;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.properties.mail.smtp.auth}")
    private boolean smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    private boolean starttlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required}")
    private boolean starttlsRequired;

    @Value("${campuspulse.mail.from}")
    private String mailFrom;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Test
    @DisplayName("application.properties defaults to Gmail SMTP host, port 587, and authenticated username")
    void applicationPropertiesConfiguresGmailDefaults() {
        assertThat(mailHost).isEqualTo("smtp.gmail.com");
        assertThat(mailPort).isEqualTo(587);
        assertThat(mailUsername).isEqualTo("rohityaduvanshi438@gmail.com");
        assertThat(smtpAuth).isTrue();
        assertThat(starttlsEnable).isTrue();
        assertThat(starttlsRequired).isTrue();
        assertThat(mailFrom).isEqualTo("rohityaduvanshi438@gmail.com");

        assertThat(mailSender).isNotNull();
        if (mailSender instanceof JavaMailSenderImpl impl) {
            assertThat(impl.getHost()).isEqualTo("smtp.gmail.com");
            assertThat(impl.getPort()).isEqualTo(587);
            assertThat(impl.getUsername()).isEqualTo("rohityaduvanshi438@gmail.com");
            assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        }
    }
}
