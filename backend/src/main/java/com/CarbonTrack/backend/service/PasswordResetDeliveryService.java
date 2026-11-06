package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.config.PasswordResetMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetDeliveryService {

    private static final Logger logger =
            LoggerFactory.getLogger(PasswordResetDeliveryService.class);

    private final PasswordResetMailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    public PasswordResetDeliveryService(
            PasswordResetMailProperties mailProperties,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment) {
        this.mailProperties = mailProperties;
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendResetLink(PasswordResetRequestedEvent event) {
        String resetUrl = mailProperties.frontendUrl()
                + "/#/reset-password?token=" + event.rawToken();

        if (mailProperties.isSmtpConfigured()) {
            try {
                sendEmail(event.email(), resetUrl);
            } catch (MailAuthenticationException ex) {
                logger.error(
                        "SMTP authentication failed for password recovery; verify SMTP_USERNAME and SMTP_PASSWORD. Some providers require an application password."
                );
            } catch (MailException | IllegalStateException ex) {
                logger.error(
                        "Could not send password recovery email ({})",
                        ex.getClass().getSimpleName()
                );
            }
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            logger.info("Development password reset link: {}", resetUrl);
        } else {
            logger.warn(
                    "Password-reset delivery is disabled: SMTP is not configured and the dev profile is not active.");
        }
    }

    private void sendEmail(String email, String resetUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                    "SMTP is configured but no JavaMailSender is available.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailProperties.from());
            helper.setTo(email);
            helper.setSubject("Reset your CarbonTrack password");
            helper.setText(
                    "We received a request to reset your CarbonTrack password.\n\n"
                            + "Use this link soon to choose a new password:\n"
                            + resetUrl
                            + "\n\nIf you did not request a reset, you can ignore this email.",
                    false
            );
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException(
                    "Could not prepare the password-reset email.", ex);
        }
    }
}
