package com.campuspulse.service;

import com.campuspulse.model.ComplaintStatus;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite verifying EmailNotificationService email generation,
 * HTML branding, plain-text fallback, tracking link formatting, and privacy protection.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        emailNotificationService = new EmailNotificationService(
                mailSender,
                "noreply@campuspulse.edu",
                "https://campuspulse.edu"
        );
    }

    @Test
    @DisplayName("sendNotification constructs and dispatches valid MIME message with recipient, subject, and sender")
    void sendNotificationDispatchesMimeMessage() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailNotificationService.sendNotification(
                "student123@college.edu",
                "CP-2026-ABC12345",
                ComplaintStatus.UNDER_REVIEW
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);

        Address[] recipients = sentMessage.getAllRecipients();
        assertNotNull(recipients);
        assertEquals(1, recipients.length);
        assertEquals("student123@college.edu", recipients[0].toString());

        assertTrue(sentMessage.getSubject().contains("CP-2026-ABC12345"));
        assertTrue(sentMessage.getSubject().contains("Under Review"));
    }

    @Test
    @DisplayName("buildHtmlEmail creates responsive HTML with CampusPulse branding and tracking link")
    void buildHtmlEmailContainsBrandingAndTrackingUrl() {
        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-XYZ98765";
        String html = emailNotificationService.buildHtmlEmail(
                "CP-2026-XYZ98765",
                ComplaintStatus.IN_PROGRESS,
                trackingUrl
        );

        assertNotNull(html);
        assertTrue(html.contains("Campus<span style=\"color: #38bdf8;\">Pulse</span>"), "Must contain CampusPulse brand mark");
        assertTrue(html.contains("CP-2026-XYZ98765"), "Must include reference ID");
        assertTrue(html.contains("In Progress"), "Must include status display name");
        assertTrue(html.contains(trackingUrl), "Must include public tracking link");
        assertTrue(html.contains("Track Grievance Status"), "Must include CTA button");
        assertTrue(html.contains("Privacy &amp; Security Notice"), "Must include privacy disclaimer");
    }

    @Test
    @DisplayName("buildPlainTextEmail provides clean, informative text fallback without HTML tags")
    void buildPlainTextEmailContainsPlainContent() {
        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-XYZ98765";
        String text = emailNotificationService.buildPlainTextEmail(
                "CP-2026-XYZ98765",
                ComplaintStatus.RESOLVED,
                trackingUrl
        );

        assertNotNull(text);
        assertFalse(text.contains("<"), "Plain text alternative must not contain HTML tags");
        assertTrue(text.contains("CP-2026-XYZ98765"));
        assertTrue(text.contains("Resolved"));
        assertTrue(text.contains(trackingUrl));
        assertTrue(text.contains("Privacy Notice"));
    }

    @Test
    @DisplayName("Email templates never leak complaint description or sensitive administrator notes")
    void emailTemplatesNeverExposeSensitiveData() {
        String sensitiveDescription = "Student had confidential dispute with faculty advisor Dr. Smith";
        String adminNote = "Internal security investigation concluded student was not at fault";

        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-SECRET01";
        String html = emailNotificationService.buildHtmlEmail("CP-2026-SECRET01", ComplaintStatus.UNDER_REVIEW, trackingUrl);
        String text = emailNotificationService.buildPlainTextEmail("CP-2026-SECRET01", ComplaintStatus.UNDER_REVIEW, trackingUrl);

        assertFalse(html.contains(sensitiveDescription));
        assertFalse(html.contains(adminNote));
        assertFalse(text.contains(sensitiveDescription));
        assertFalse(text.contains(adminNote));
    }

    @Test
    @DisplayName("sendNotification propagates MailException so caller can update outbox failure state")
    void sendNotificationPropagatesMailException() {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP connection timed out")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () ->
                emailNotificationService.sendNotification(
                        "student@college.edu",
                        "CP-2026-FAIL0001",
                        ComplaintStatus.IN_PROGRESS
                )
        );
    }

    @Test
    @DisplayName("sendNotification rejects blank recipient email, blank reference ID, or null status")
    void sendNotificationValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendNotification(null, "CP-2026-TEST", ComplaintStatus.UNDER_REVIEW));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendNotification("   ", "CP-2026-TEST", ComplaintStatus.UNDER_REVIEW));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendNotification("student@test.edu", null, ComplaintStatus.UNDER_REVIEW));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendNotification("student@test.edu", "CP-2026-TEST", null));
    }

    @Test
    @DisplayName("sendSubmissionConfirmation constructs and dispatches valid MIME message with recipient and subject")
    void sendSubmissionConfirmationDispatchesMimeMessage() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailNotificationService.sendSubmissionConfirmation(
                "freshman@college.edu",
                "CP-2026-SUBMIT01"
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);

        Address[] recipients = sentMessage.getAllRecipients();
        assertNotNull(recipients);
        assertEquals(1, recipients.length);
        assertEquals("freshman@college.edu", recipients[0].toString());

        assertTrue(sentMessage.getSubject().contains("CP-2026-SUBMIT01"));
        assertTrue(sentMessage.getSubject().contains("Grievance Registered"));
    }

    @Test
    @DisplayName("buildHtmlSubmissionConfirmationEmail contains branding, tracking link, SUBMITTED status, and instructions")
    void buildHtmlSubmissionConfirmationEmailContainsRequiredElements() {
        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-SUBMIT01";
        String html = emailNotificationService.buildHtmlSubmissionConfirmationEmail(
                "CP-2026-SUBMIT01",
                trackingUrl
        );

        assertNotNull(html);
        assertTrue(html.contains("Campus<span style=\"color: #38bdf8;\">Pulse</span>"), "Must contain brand mark");
        assertTrue(html.contains("CP-2026-SUBMIT01"), "Must include reference ID");
        assertTrue(html.contains("SUBMITTED"), "Must include SUBMITTED status");
        assertTrue(html.contains(trackingUrl), "Must include tracking URL");
        assertTrue(html.contains("Track Grievance Status"), "Must include CTA button");
        assertTrue(html.contains("How to Track Your Grievance"), "Must include status instructions");
        assertTrue(html.contains("Privacy &amp; Security Notice"), "Must include privacy disclaimer");
    }

    @Test
    @DisplayName("buildPlainTextSubmissionConfirmationEmail contains clean plain text and instructions without HTML")
    void buildPlainTextSubmissionConfirmationEmailContainsPlainContent() {
        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-SUBMIT01";
        String text = emailNotificationService.buildPlainTextSubmissionConfirmationEmail(
                "CP-2026-SUBMIT01",
                trackingUrl
        );

        assertNotNull(text);
        assertFalse(text.contains("<"), "Plain text alternative must not contain HTML tags");
        assertTrue(text.contains("CP-2026-SUBMIT01"));
        assertTrue(text.contains("SUBMITTED"));
        assertTrue(text.contains(trackingUrl));
        assertTrue(text.contains("Instructions for Checking Status"));
        assertTrue(text.contains("Privacy Notice"));
    }

    @Test
    @DisplayName("Submission confirmation templates never expose complaint description or sensitive administrator notes")
    void submissionConfirmationTemplatesNeverExposeSensitiveData() {
        String sensitiveDescription = "Confidential student disciplinary conflict";
        String adminNote = "Dean recommended immediate mediation";
        String trackingUrl = "https://campuspulse.edu/complaints/track?referenceId=CP-2026-PRIVACY1";

        String html = emailNotificationService.buildHtmlSubmissionConfirmationEmail("CP-2026-PRIVACY1", trackingUrl);
        String text = emailNotificationService.buildPlainTextSubmissionConfirmationEmail("CP-2026-PRIVACY1", trackingUrl);

        assertFalse(html.contains(sensitiveDescription));
        assertFalse(html.contains(adminNote));
        assertFalse(text.contains(sensitiveDescription));
        assertFalse(text.contains(adminNote));
    }

    @Test
    @DisplayName("sendSubmissionConfirmation propagates MailException when SMTP fails")
    void sendSubmissionConfirmationPropagatesMailException() {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP relay refused connection")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () ->
                emailNotificationService.sendSubmissionConfirmation(
                        "student@college.edu",
                        "CP-2026-FAIL0002"
                )
        );
    }

    @Test
    @DisplayName("sendSubmissionConfirmation validates recipient email and reference ID")
    void sendSubmissionConfirmationValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendSubmissionConfirmation(null, "CP-2026-TEST"));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendSubmissionConfirmation("   ", "CP-2026-TEST"));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendSubmissionConfirmation("student@test.edu", null));

        assertThrows(IllegalArgumentException.class, () ->
                emailNotificationService.sendSubmissionConfirmation("student@test.edu", "   "));
    }

    @Test
    @DisplayName("sendPasswordResetEmail constructs and dispatches valid MIME message with reset URL and subject")
    void sendPasswordResetEmailDispatchesMimeMessage() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String resetUrl = "https://campuspulse.edu/reset-password?token=0123456789abcdef0123456789abcdef";
        emailNotificationService.sendPasswordResetEmail(
                "user@campuspulse.edu",
                resetUrl,
                15
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);

        Address[] recipients = sentMessage.getAllRecipients();
        assertNotNull(recipients);
        assertEquals(1, recipients.length);
        assertEquals("user@campuspulse.edu", recipients[0].toString());

        assertTrue(sentMessage.getSubject().contains("Password Reset Request"));
    }

    @Test
    @DisplayName("buildHtmlPasswordResetEmail contains reset link, branding, CTA button, and 15-minute expiry")
    void buildHtmlPasswordResetEmailContainsRequiredContent() {
        String resetUrl = "https://campuspulse.edu/reset-password?token=abcdef1234567890";
        String html = emailNotificationService.buildHtmlPasswordResetEmail(resetUrl, 15);

        assertNotNull(html);
        assertTrue(html.contains("Campus<span style=\"color: #38bdf8;\">Pulse</span>"));
        assertTrue(html.contains("Password Reset Request"));
        assertTrue(html.contains(resetUrl));
        assertTrue(html.contains("Reset My Password"));
        assertTrue(html.contains("15 minutes"));
        assertTrue(html.contains("single-use"));
    }

    @Test
    @DisplayName("buildPlainTextPasswordResetEmail contains plain text reset link and security notice")
    void buildPlainTextPasswordResetEmailContainsRequiredContent() {
        String resetUrl = "https://campuspulse.edu/reset-password?token=abcdef1234567890";
        String text = emailNotificationService.buildPlainTextPasswordResetEmail(resetUrl, 15);

        assertNotNull(text);
        assertTrue(text.contains("Password Reset Request"));
        assertTrue(text.contains(resetUrl));
        assertTrue(text.contains("15 minutes"));
        assertTrue(text.contains("single-use"));
    }

    @Test
    @DisplayName("sendPasswordResetEmail propagates MailException when SMTP delivery fails")
    void sendPasswordResetEmailPropagatesMailException() {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP server connection timeout")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () ->
                emailNotificationService.sendPasswordResetEmail(
                        "user@campuspulse.edu",
                        "https://campuspulse.edu/reset-password?token=test1234",
                        15
                )
        );
    }
}
