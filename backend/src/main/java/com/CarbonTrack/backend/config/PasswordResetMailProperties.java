package com.CarbonTrack.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailProperties implements ApplicationRunner {

    private final String host;
    private final String username;
    private final String password;
    private final String from;
    private final String frontendUrl;
    private final Environment environment;

    public PasswordResetMailProperties(
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${carbontrack.mail.from:}") String from,
            @Value("${carbontrack.password-reset.frontend-url:http://localhost:5173}")
            String frontendUrl,
            Environment environment) {
        this.host = host.trim();
        this.username = username.trim();
        this.password = password;
        this.from = from.trim();
        this.frontendUrl = stripTrailingSlash(frontendUrl.trim());
        this.environment = environment;
    }

    public boolean isSmtpConfigured() {
        return !host.isEmpty()
                && !username.isEmpty()
                && !password.isBlank()
                && !from.isEmpty();
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public String from() {
        return from;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean anySmtpSettingPresent = !host.isEmpty()
                || !username.isEmpty()
                || !password.isBlank()
                || !from.isEmpty();

        if (anySmtpSettingPresent && !isSmtpConfigured()) {
            throw new IllegalStateException(
                    "Configure SMTP host, username, password, and SMTP_FROM together.");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !isSmtpConfigured()) {
            throw new IllegalStateException(
                    "SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, and SMTP_FROM are required in the prod profile.");
        }
    }

    private String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
