package com.CarbonTrack.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.CarbonTrack.backend.config.PasswordResetMailProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetDeliveryServiceTest {

    @Test
    void logsConsoleResetLinkOnlyForExplicitDevProfileWithoutSmtp() {
        String testToken = "test-token-must-not-leak-outside-dev";
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(
                1L,
                "person@example.com",
                testToken
        );

        Logger logger = (Logger) LoggerFactory.getLogger(
                PasswordResetDeliveryService.class);
        boolean previousAdditivity = logger.isAdditive();
        logger.setAdditive(false);
        try {
            ListAppender<ILoggingEvent> devLogs = captureDeliveryLogs();
            createDeliveryService("dev").sendResetLink(event);
            assertTrue(messages(devLogs).stream().anyMatch(
                    message -> message.contains(testToken)));
            detach(devLogs);

            ListAppender<ILoggingEvent> defaultProfileLogs = captureDeliveryLogs();
            createDeliveryService(null).sendResetLink(event);
            assertFalse(messages(defaultProfileLogs).stream().anyMatch(
                    message -> message.contains(testToken)));
            detach(defaultProfileLogs);

            ListAppender<ILoggingEvent> prodLogs = captureDeliveryLogs();
            createDeliveryService("prod").sendResetLink(event);
            assertFalse(messages(prodLogs).stream().anyMatch(
                    message -> message.contains(testToken)));
            detach(prodLogs);
        } finally {
            logger.setAdditive(previousAdditivity);
        }
    }

    @Test
    void configuredSmtpUsesMailSender() throws Exception {
        PasswordResetMailProperties properties =
                new PasswordResetMailProperties(
                        "smtp.example.test",
                        "test-user",
                        "test-secret",
                        "carbontrack@example.test",
                        "https://carbontrack.example",
                        new MockEnvironment()
                );
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(
                Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);

        PasswordResetDeliveryService service =
                new PasswordResetDeliveryService(
                        properties,
                        provider,
                        new MockEnvironment()
                );
        service.sendResetLink(new PasswordResetRequestedEvent(
                1L,
                "person@example.test",
                "smtp-test-token"
        ));

        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void smtpAuthenticationFailureLogsSafeConfigurationGuidance() throws Exception {
        PasswordResetMailProperties properties =
                new PasswordResetMailProperties(
                        "smtp.example.test",
                        "smtp-user",
                        "smtp-password-test-secret",
                        "carbontrack@example.test",
                        "https://carbontrack.example",
                        new MockEnvironment()
                );
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(
                Session.getInstance(new Properties())));
        doThrow(new MailAuthenticationException("authentication rejected"))
                .when(sender).send(any(MimeMessage.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);

        Logger logger = (Logger) LoggerFactory.getLogger(
                PasswordResetDeliveryService.class);
        ListAppender<ILoggingEvent> appender = captureDeliveryLogs();
        try {
            PasswordResetDeliveryService service =
                    new PasswordResetDeliveryService(
                            properties, provider, new MockEnvironment());
            service.sendResetLink(new PasswordResetRequestedEvent(
                    1L, "person@example.test", "reset-token-test-secret"));

            String logged = String.join("\n", messages(appender));
            assertTrue(logged.contains("SMTP authentication failed"));
            assertTrue(logged.contains("application password"));
            assertFalse(logged.contains("smtp-password-test-secret"));
            assertFalse(logged.contains("reset-token-test-secret"));
        } finally {
            detach(appender);
        }
    }

    private PasswordResetDeliveryService createDeliveryService(String profile) {
        MockEnvironment environment = new MockEnvironment();
        if (profile != null) {
            environment.setActiveProfiles(profile);
        }

        PasswordResetMailProperties properties =
                new PasswordResetMailProperties(
                        "", "", "", "",
                        "http://localhost:5173",
                        environment
                );
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider =
                mock(ObjectProvider.class);

        return new PasswordResetDeliveryService(
                properties,
                mailSenderProvider,
                environment
        );
    }

    private ListAppender<ILoggingEvent> captureDeliveryLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                PasswordResetDeliveryService.class);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private java.util.List<String> messages(
            ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(
                PasswordResetDeliveryService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
