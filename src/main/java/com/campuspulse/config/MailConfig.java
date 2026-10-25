package com.campuspulse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Spring JavaMailSender configuration.
 * Automatically sanitizes credentials (stripping whitespace and quotes commonly introduced
 * when copying Google App Passwords) and configures resilient timeout and TLS settings.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean starttlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private boolean starttlsRequired;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host != null && !host.isBlank() ? host.trim() : "smtp.gmail.com");
        mailSender.setPort(port > 0 ? port : 587);

        String cleanUsername = username != null ? username.trim().replaceAll("^[\"']|[\"']$", "") : "";
        mailSender.setUsername(cleanUsername);

        String cleanPassword = "";
        if (password != null && !password.isBlank()) {
            // Strip any internal spaces (Google App Passwords are shown with spaces like 'abcd efgh ijkl mnop')
            // and remove surrounding quotes if entered in .env or Render dashboard.
            cleanPassword = password.trim().replaceAll("^[\"']|[\"']$", "").replaceAll("\\s+", "");
            mailSender.setPassword(cleanPassword);
            log.info("Configured SMTP mail sender for user: {} (sanitized password length: {})",
                    cleanUsername.isEmpty() ? "unspecified" : cleanUsername, cleanPassword.length());
        } else {
            log.warn("Notice: SMTP_PASSWORD is empty or not configured. Outgoing emails cannot be delivered until a valid Google App Password is supplied.");
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
        props.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        return mailSender;
    }
}
