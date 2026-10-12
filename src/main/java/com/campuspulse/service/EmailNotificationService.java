package com.campuspulse.service;

import com.campuspulse.model.ComplaintStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service responsible for constructing responsive, branded MIME emails
 * (HTML + plain text alternative) and delivering them via Spring's JavaMailSender.
 * Strictly avoids exposing complaint descriptions, student details, or administrator notes.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String baseUrl;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${campuspulse.mail.from:noreply@campuspulse.edu}") String mailFrom,
            @Value("${campuspulse.app.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * Sends an email notification to the student regarding a complaint status transition.
     * Throws MailException on delivery failure so the caller can record the failure in the outbox.
     *
     * @param recipientEmail student email address
     * @param referenceId unique complaint reference ID
     * @param newStatus updated status
     * @throws MailException if SMTP delivery fails
     */
    public void sendNotification(String recipientEmail, String referenceId, ComplaintStatus newStatus) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email must not be blank");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID must not be blank");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must not be null");
        }

        String trackingUrl = baseUrl + "/complaints/track?referenceId=" + referenceId.trim();
        String subject = "[CampusPulse] Grievance Update: " + referenceId.trim() + " is now " + newStatus.getDisplayName();

        String htmlContent = buildHtmlEmail(referenceId.trim(), newStatus, trackingUrl);
        String plainTextContent = buildPlainTextEmail(referenceId.trim(), newStatus, trackingUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail.trim());
            helper.setSubject(subject);
            helper.setText(plainTextContent, htmlContent);

            log.info("Dispatching status update email for referenceId={} with status={} to {}",
                    referenceId, newStatus, maskEmail(recipientEmail));

            mailSender.send(message);

            log.info("Successfully delivered status update email for referenceId={} to {}",
                    referenceId, maskEmail(recipientEmail));
        } catch (MessagingException ex) {
            log.error("Failed to construct MIME email message for referenceId={}: {}", referenceId, ex.getMessage());
            throw new org.springframework.mail.MailSendException("Failed to construct email message", ex);
        }
    }

    /**
     * Builds responsive HTML email using inline styles and CampusPulse navy-blue and white design.
     * Strictly avoids exposing complaint descriptions or sensitive admin notes.
     */
    String buildHtmlEmail(String referenceId, ComplaintStatus status, String trackingUrl) {
        String statusBg;
        String statusColor;
        String statusBorder;

        switch (status) {
            case UNDER_REVIEW -> {
                statusBg = "#fef3c7";
                statusColor = "#92400e";
                statusBorder = "#fcd34d";
            }
            case IN_PROGRESS -> {
                statusBg = "#e0e7ff";
                statusColor = "#3730a3";
                statusBorder = "#c7d2fe";
            }
            case RESOLVED -> {
                statusBg = "#d1fae5";
                statusColor = "#065f46";
                statusBorder = "#a7f3d0";
            }
            default -> {
                statusBg = "#f1f5f9";
                statusColor = "#334155";
                statusBorder = "#cbd5e1";
            }
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>CampusPulse Grievance Status Update</title>\n"
                + "</head>\n"
                + "<body style=\"margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; line-height: 1.5;\">\n"
                + "  <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color: #f8fafc; padding: 32px 16px;\">\n"
                + "    <tr>\n"
                + "      <td align=\"center\">\n"
                + "        <table role=\"presentation\" width=\"100%\" style=\"max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n"
                + "          <!-- Header Banner -->\n"
                + "          <tr>\n"
                + "            <td style=\"background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); padding: 28px 32px; text-align: left;\">\n"
                + "              <div style=\"color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: -0.02em;\">\n"
                + "                Campus<span style=\"color: #38bdf8;\">Pulse</span>\n"
                + "              </div>\n"
                + "              <div style=\"color: #94a3b8; font-size: 13px; margin-top: 4px; font-weight: 500;\">\n"
                + "                Campus Grievance &amp; Feedback Management System\n"
                + "              </div>\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "          <!-- Card Content -->\n"
                + "          <tr>\n"
                + "            <td style=\"padding: 32px;\">\n"
                + "              <h2 style=\"font-size: 18px; font-weight: 700; color: #0f172a; margin-top: 0; margin-bottom: 12px;\">\n"
                + "                Grievance Status Updated\n"
                + "              </h2>\n"
                + "              <p style=\"font-size: 14px; color: #475569; margin: 0 0 24px 0;\">\n"
                + "                Your registered campus grievance has been updated by the campus administration. Please find the latest lifecycle details below:\n"
                + "              </p>\n"
                + "              <!-- Reference ID and Status Box -->\n"
                + "              <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; margin-bottom: 24px;\">\n"
                + "                <tr>\n"
                + "                  <td style=\"padding: 16px 20px; border-bottom: 1px solid #e2e8f0;\">\n"
                + "                    <div style=\"font-size: 11px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; color: #64748b;\">Reference ID</div>\n"
                + "                    <div style=\"font-family: monospace; font-size: 16px; font-weight: 700; color: #1e3a8a; margin-top: 2px;\">" + referenceId + "</div>\n"
                + "                  </td>\n"
                + "                </tr>\n"
                + "                <tr>\n"
                + "                  <td style=\"padding: 16px 20px;\">\n"
                + "                    <div style=\"font-size: 11px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; color: #64748b;\">New Status</div>\n"
                + "                    <div style=\"margin-top: 6px;\">\n"
                + "                      <span style=\"display: inline-block; background-color: " + statusBg + "; color: " + statusColor + "; border: 1px solid " + statusBorder + "; padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;\">" + status.getDisplayName() + "</span>\n"
                + "                    </div>\n"
                + "                  </td>\n"
                + "                </tr>\n"
                + "              </table>\n"
                + "              <!-- CTA Button -->\n"
                + "              <div style=\"text-align: center; margin: 32px 0 24px 0;\">\n"
                + "                <a href=\"" + trackingUrl + "\" target=\"_blank\" style=\"display: inline-block; background: #1d4ed8; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 6px; font-size: 14px; font-weight: 700; letter-spacing: -0.01em; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);\">\n"
                + "                  Track Grievance Status &rarr;\n"
                + "                </a>\n"
                + "              </div>\n"
                + "              <!-- Direct Link Fallback -->\n"
                + "              <p style=\"font-size: 12px; color: #64748b; line-height: 1.5; margin: 0 0 16px 0;\">\n"
                + "                If the button above does not work, copy and paste this link into your browser:<br>\n"
                + "                <a href=\"" + trackingUrl + "\" style=\"color: #1d4ed8; word-break: break-all;\">" + trackingUrl + "</a>\n"
                + "              </p>\n"
                + "              <!-- Privacy Notice -->\n"
                + "              <div style=\"background-color: #f1f5f9; border-radius: 6px; padding: 12px 16px; margin-top: 24px;\">\n"
                + "                <p style=\"font-size: 11px; color: #64748b; margin: 0;\">\n"
                + "                  <strong>Privacy &amp; Security Notice:</strong> To protect student confidentiality, this automated notification does not include grievance descriptions or administrative notes. Please visit the public tracking page to view the timeline.\n"
                + "                </p>\n"
                + "              </div>\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "          <!-- Footer -->\n"
                + "          <tr>\n"
                + "            <td style=\"background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 32px; text-align: center; font-size: 11px; color: #94a3b8;\">\n"
                + "              &copy; CampusPulse &bull; Campus Grievance and Feedback Management System<br>\n"
                + "              This is an automated notification. Please do not reply directly to this email.\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "        </table>\n"
                + "      </td>\n"
                + "    </tr>\n"
                + "  </table>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Builds clean, legible plain-text email alternative.
     */
    String buildPlainTextEmail(String referenceId, ComplaintStatus status, String trackingUrl) {
        return "CampusPulse Grievance & Feedback System\n"
                + "==================================================\n\n"
                + "Dear Student,\n\n"
                + "The status of your registered campus grievance has been updated by the campus administration.\n\n"
                + "Reference ID : " + referenceId + "\n"
                + "New Status   : " + status.getDisplayName() + "\n\n"
                + "Track your grievance status and timeline on the public portal:\n"
                + trackingUrl + "\n\n"
                + "--------------------------------------------------\n"
                + "Privacy Notice: To protect student confidentiality, this automated notification\n"
                + "does not include grievance descriptions or administrative notes.\n\n"
                + "CampusPulse - Campus Grievance Management System\n"
                + "This is an automated message. Please do not reply directly to this email.\n";
    }

    /**
     * Sends an email notification to the student confirming initial registration of their complaint.
     * Contains the reference ID, tracking link, and status checking instructions.
     * Strictly avoids exposing complaint descriptions or sensitive personal details.
     *
     * @param recipientEmail student email address
     * @param referenceId unique complaint reference ID
     * @throws MailException if SMTP delivery fails
     */
    public void sendSubmissionConfirmation(String recipientEmail, String referenceId) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email must not be blank");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID must not be blank");
        }

        String trackingUrl = baseUrl + "/complaints/track?referenceId=" + referenceId.trim();
        String subject = "[CampusPulse] Grievance Registered: " + referenceId.trim();

        String htmlContent = buildHtmlSubmissionConfirmationEmail(referenceId.trim(), trackingUrl);
        String plainTextContent = buildPlainTextSubmissionConfirmationEmail(referenceId.trim(), trackingUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail.trim());
            helper.setSubject(subject);
            helper.setText(plainTextContent, htmlContent);

            log.info("Dispatching submission confirmation email for referenceId={} to {}",
                    referenceId, maskEmail(recipientEmail));

            mailSender.send(message);

            log.info("Successfully delivered submission confirmation email for referenceId={} to {}",
                    referenceId, maskEmail(recipientEmail));
        } catch (MessagingException ex) {
            log.error("Failed to construct MIME email message for submission confirmation referenceId={}: {}",
                    referenceId, ex.getMessage());
            throw new org.springframework.mail.MailSendException("Failed to construct email message", ex);
        }
    }

    /**
     * Builds responsive HTML submission confirmation email with CampusPulse branding and tracking link.
     * Strictly avoids exposing complaint descriptions or sensitive admin notes.
     */
    String buildHtmlSubmissionConfirmationEmail(String referenceId, String trackingUrl) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>CampusPulse Grievance Registration Confirmation</title>\n"
                + "</head>\n"
                + "<body style=\"margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; line-height: 1.5;\">\n"
                + "  <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color: #f8fafc; padding: 32px 16px;\">\n"
                + "    <tr>\n"
                + "      <td align=\"center\">\n"
                + "        <table role=\"presentation\" width=\"100%\" style=\"max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n"
                + "          <!-- Header Banner -->\n"
                + "          <tr>\n"
                + "            <td style=\"background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); padding: 28px 32px; text-align: left;\">\n"
                + "              <div style=\"color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: -0.02em;\">\n"
                + "                Campus<span style=\"color: #38bdf8;\">Pulse</span>\n"
                + "              </div>\n"
                + "              <div style=\"color: #94a3b8; font-size: 13px; margin-top: 4px; font-weight: 500;\">\n"
                + "                Campus Grievance &amp; Feedback Management System\n"
                + "              </div>\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "          <!-- Card Content -->\n"
                + "          <tr>\n"
                + "            <td style=\"padding: 32px;\">\n"
                + "              <h2 style=\"font-size: 18px; font-weight: 700; color: #0f172a; margin-top: 0; margin-bottom: 12px;\">\n"
                + "                Grievance Successfully Registered\n"
                + "              </h2>\n"
                + "              <p style=\"font-size: 14px; color: #475569; margin: 0 0 24px 0;\">\n"
                + "                Thank you for reaching out. Your campus grievance has been recorded in the CampusPulse system and assigned for administrative review.\n"
                + "              </p>\n"
                + "              <!-- Reference ID and Status Box -->\n"
                + "              <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; margin-bottom: 24px;\">\n"
                + "                <tr>\n"
                + "                  <td style=\"padding: 16px 20px; border-bottom: 1px solid #e2e8f0;\">\n"
                + "                    <div style=\"font-size: 11px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; color: #64748b;\">Reference ID</div>\n"
                + "                    <div style=\"font-family: monospace; font-size: 16px; font-weight: 700; color: #1e3a8a; margin-top: 2px;\">" + referenceId + "</div>\n"
                + "                  </td>\n"
                + "                </tr>\n"
                + "                <tr>\n"
                + "                  <td style=\"padding: 16px 20px;\">\n"
                + "                    <div style=\"font-size: 11px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; color: #64748b;\">Initial Status</div>\n"
                + "                    <div style=\"margin-top: 6px;\">\n"
                + "                      <span style=\"display: inline-block; background-color: #f1f5f9; color: #334155; border: 1px solid #cbd5e1; padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;\">SUBMITTED</span>\n"
                + "                    </div>\n"
                + "                  </td>\n"
                + "                </tr>\n"
                + "              </table>\n"
                + "              <!-- Instructions -->\n"
                + "              <div style=\"background-color: #f8fafc; border-radius: 8px; border-left: 4px solid #3b82f6; padding: 14px 18px; margin-bottom: 24px;\">\n"
                + "                <div style=\"font-size: 13px; font-weight: 700; color: #1e3a8a; margin-bottom: 6px;\">How to Track Your Grievance:</div>\n"
                + "                <ol style=\"font-size: 13px; color: #475569; margin: 0; padding-left: 18px; line-height: 1.6;\">\n"
                + "                  <li>Keep your unique <strong>Reference ID</strong> handy for future inquiries.</li>\n"
                + "                  <li>Visit the public tracking portal anytime to check the real-time resolution timeline.</li>\n"
                + "                  <li>You will receive automated email updates as administrative actions are recorded.</li>\n"
                + "                </ol>\n"
                + "              </div>\n"
                + "              <!-- CTA Button -->\n"
                + "              <div style=\"text-align: center; margin: 32px 0 24px 0;\">\n"
                + "                <a href=\"" + trackingUrl + "\" target=\"_blank\" style=\"display: inline-block; background: #1d4ed8; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 6px; font-size: 14px; font-weight: 700; letter-spacing: -0.01em; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);\">\n"
                + "                  Track Grievance Status &rarr;\n"
                + "                </a>\n"
                + "              </div>\n"
                + "              <!-- Direct Link Fallback -->\n"
                + "              <p style=\"font-size: 12px; color: #64748b; line-height: 1.5; margin: 0 0 16px 0;\">\n"
                + "                If the button above does not work, copy and paste this link into your browser:<br>\n"
                + "                <a href=\"" + trackingUrl + "\" style=\"color: #1d4ed8; word-break: break-all;\">" + trackingUrl + "</a>\n"
                + "              </p>\n"
                + "              <!-- Privacy Notice -->\n"
                + "              <div style=\"background-color: #f1f5f9; border-radius: 6px; padding: 12px 16px; margin-top: 24px;\">\n"
                + "                <p style=\"font-size: 11px; color: #64748b; margin: 0;\">\n"
                + "                  <strong>Privacy &amp; Security Notice:</strong> To protect student confidentiality, this automated notification does not include grievance descriptions or administrative notes. Please visit the public tracking page to view the timeline.\n"
                + "                </p>\n"
                + "              </div>\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "          <!-- Footer -->\n"
                + "          <tr>\n"
                + "            <td style=\"background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 32px; text-align: center; font-size: 11px; color: #94a3b8;\">\n"
                + "              &copy; CampusPulse &bull; Campus Grievance and Feedback Management System<br>\n"
                + "              This is an automated notification. Please do not reply directly to this email.\n"
                + "            </td>\n"
                + "          </tr>\n"
                + "        </table>\n"
                + "      </td>\n"
                + "    </tr>\n"
                + "  </table>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Builds clean, legible plain-text submission confirmation email alternative.
     */
    String buildPlainTextSubmissionConfirmationEmail(String referenceId, String trackingUrl) {
        return "CampusPulse Grievance & Feedback System\n"
                + "==================================================\n\n"
                + "Dear Student,\n\n"
                + "Your campus grievance has been successfully submitted and registered in the CampusPulse system.\n\n"
                + "Reference ID : " + referenceId + "\n"
                + "Initial Status: SUBMITTED\n\n"
                + "Track your grievance status and timeline on the public portal:\n"
                + trackingUrl + "\n\n"
                + "Instructions for Checking Status:\n"
                + "1. Keep your Reference ID safe for your records.\n"
                + "2. Visit the tracking link above to monitor review, assignment, and resolution progress.\n"
                + "3. You will receive automated email updates as administrative actions are taken.\n\n"
                + "--------------------------------------------------\n"
                + "Privacy Notice: To protect student confidentiality, this automated notification\n"
                + "does not include grievance descriptions or administrative notes.\n\n"
                + "CampusPulse - Campus Grievance Management System\n"
                + "This is an automated message. Please do not reply directly to this email.\n";
    }

    /**
     * Sends an SLA breach escalation alert to the administrative coordinator.
     * Strictly avoids exposing complaint descriptions or student personal details.
     *
     * @param recipientEmail administrative recipient email address
     * @param referenceId unique complaint reference ID
     * @param stage escalation stage
     * @param reason objective breach explanation
     * @param category complaint category
     * @param department responsible department
     */
    public void sendEscalationNotification(
            String recipientEmail,
            String referenceId,
            com.campuspulse.model.SlaEscalationStage stage,
            String reason,
            String category,
            String department) {

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email must not be blank");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID must not be blank");
        }
        if (stage == null) {
            throw new IllegalArgumentException("Escalation stage must not be null");
        }

        String adminUrl = baseUrl + "/admin/complaints/" + referenceId.trim();
        String subject = "[CampusPulse SLA Alert] Escalation: Complaint " + referenceId.trim() + " is Overdue (" + stage.getDisplayName() + ")";

        String htmlContent = buildHtmlEscalationEmail(referenceId.trim(), stage, reason, category, department, adminUrl);
        String plainTextContent = buildPlainTextEscalationEmail(referenceId.trim(), stage, reason, category, department, adminUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail.trim());
            helper.setSubject(subject);
            helper.setText(plainTextContent, htmlContent);

            log.info("Dispatching SLA escalation email for referenceId={} stage={} to {}",
                    referenceId, stage, maskEmail(recipientEmail));

            mailSender.send(message);

            log.info("Successfully delivered SLA escalation email for referenceId={} to {}",
                    referenceId, maskEmail(recipientEmail));
        } catch (MessagingException ex) {
            log.error("Failed to construct MIME email message for escalation referenceId={}: {}", referenceId, ex.getMessage());
            throw new org.springframework.mail.MailSendException("Failed to construct escalation email message", ex);
        }
    }

    String buildHtmlEscalationEmail(
            String referenceId,
            com.campuspulse.model.SlaEscalationStage stage,
            String reason,
            String category,
            String department,
            String adminUrl) {

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head><meta charset=\"UTF-8\"><title>SLA Escalation Alert</title></head>\n"
                + "<body style=\"margin:0;padding:24px;background-color:#f8fafc;font-family:'Plus Jakarta Sans',sans-serif;color:#1e293b;\">\n"
                + "  <div style=\"max-width:600px;margin:0 auto;background:#fff;border-radius:12px;border:1px solid #e2e8f0;overflow:hidden;\">\n"
                + "    <div style=\"background:#0f172a;color:#fff;padding:20px 24px;\">\n"
                + "      <h1 style=\"margin:0;font-size:18px;\">CampusPulse &bull; SLA Escalation Alert</h1>\n"
                + "    </div>\n"
                + "    <div style=\"padding:24px;\">\n"
                + "      <div style=\"background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:12px 16px;color:#991b1b;margin-bottom:16px;\">\n"
                + "        <strong>" + stage.getDisplayName() + " Triggered</strong>\n"
                + "      </div>\n"
                + "      <p style=\"margin:0 0 12px;\">A campus grievance has exceeded defined service level agreement thresholds and requires administrative attention.</p>\n"
                + "      <table style=\"width:100%;border-collapse:collapse;margin-bottom:20px;\">\n"
                + "        <tr><td style=\"padding:6px 0;color:#64748b;\">Reference ID:</td><td style=\"padding:6px 0;font-weight:700;\">" + referenceId + "</td></tr>\n"
                + "        <tr><td style=\"padding:6px 0;color:#64748b;\">Department:</td><td style=\"padding:6px 0;font-weight:600;\">" + department + "</td></tr>\n"
                + "        <tr><td style=\"padding:6px 0;color:#64748b;\">Category:</td><td style=\"padding:6px 0;\">" + category + "</td></tr>\n"
                + "        <tr><td style=\"padding:6px 0;color:#64748b;\">Breach Details:</td><td style=\"padding:6px 0;color:#b91c1c;\">" + reason + "</td></tr>\n"
                + "      </table>\n"
                + "      <a href=\"" + adminUrl + "\" style=\"display:inline-block;background:#2563eb;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;font-weight:700;\">Open in Admin Portal &rarr;</a>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "</html>";
    }

    String buildPlainTextEscalationEmail(
            String referenceId,
            com.campuspulse.model.SlaEscalationStage stage,
            String reason,
            String category,
            String department,
            String adminUrl) {

        return "CampusPulse SLA Escalation Alert\n"
                + "==================================================\n\n"
                + "Alert: " + stage.getDisplayName() + "\n\n"
                + "Reference ID : " + referenceId + "\n"
                + "Department   : " + department + "\n"
                + "Category     : " + category + "\n"
                + "Reason       : " + reason + "\n\n"
                + "Access administrative management details:\n"
                + adminUrl + "\n\n"
                + "--------------------------------------------------\n"
                + "CampusPulse Administrative Service Level Monitoring\n";
    }

    /**
     * Masks email address for safe logging without exposing personal data.
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[empty]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}

