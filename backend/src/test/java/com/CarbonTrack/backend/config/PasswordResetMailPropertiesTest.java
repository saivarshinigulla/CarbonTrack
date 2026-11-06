package com.CarbonTrack.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetMailPropertiesTest {

    @Test
    void productionCannotStartWithoutSmtpConfiguration() {
        PasswordResetMailProperties properties = new PasswordResetMailProperties(
                "", "", "", "", "https://carbontrack.example/",
                environment("prod")
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> properties.run(new DefaultApplicationArguments())
        );

        assertTrue(error.getMessage().contains("required in the prod profile"));
    }

    @Test
    void developmentCanUseConsoleDeliveryWhenSmtpIsAbsent() {
        PasswordResetMailProperties properties = new PasswordResetMailProperties(
                "", "", "", "", "http://localhost:5173/",
                environment("dev")
        );

        assertDoesNotThrow(
                () -> properties.run(new DefaultApplicationArguments())
        );
        assertFalse(properties.isSmtpConfigured());
        assertEquals("http://localhost:5173", properties.frontendUrl());
    }

    @Test
    void partialSmtpConfigurationFailsStartup() {
        PasswordResetMailProperties properties = new PasswordResetMailProperties(
                "smtp.example.com", "mailer", "", "carbontrack@example.com",
                "https://carbontrack.example",
                environment("dev")
        );

        assertThrows(
                IllegalStateException.class,
                () -> properties.run(new DefaultApplicationArguments())
        );
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
