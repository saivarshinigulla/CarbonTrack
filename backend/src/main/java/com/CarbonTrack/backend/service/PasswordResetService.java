package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.entity.PasswordResetToken;
import com.CarbonTrack.backend.repository.PasswordResetTokenRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import com.CarbonTrack.backend.util.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class PasswordResetService {

    public static final String GENERIC_REQUEST_MESSAGE =
            "If an account exists for that email, recovery instructions will be sent when delivery is configured.";
    public static final String INVALID_TOKEN_MESSAGE =
            "This password reset link is invalid or has expired. Request a new link and try again.";

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordResetRateLimiter rateLimiter;
    private final Clock clock;
    private final Duration tokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            PasswordResetRateLimiter rateLimiter,
            Clock clock,
            @Value("${carbontrack.password-reset.token-ttl:PT30M}")
            Duration tokenTtl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "Password-reset token TTL must be positive.");
        }
    }

    @Transactional
    public void requestReset(String email, String clientIp) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (!rateLimiter.allowRequest(normalizedEmail, clientIp)) {
            return;
        }

        var user = userRepository
                .findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .orElse(null);
        if (user == null) {
            return;
        }

        String rawToken = generateToken();
        Instant now = clock.instant();

        tokenRepository.deleteAllByUserId(user.getId());
        tokenRepository.save(new PasswordResetToken(
                user,
                hashToken(rawToken),
                now,
                now.plus(tokenTtl)
        ));
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                user.getId(),
                user.getEmail(),
                rawToken
        ));
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .filter(resetToken -> resetToken.getConsumedAt() == null)
                .filter(resetToken -> resetToken.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        INVALID_TOKEN_MESSAGE
                ));

        var user = token.getUser();
        if (user.getUsername().equalsIgnoreCase(newPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must not be the same as the username"
            );
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.consume(now);
        userRepository.save(user);
        tokenRepository.save(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        return Sha256.hex(rawToken);
    }
}
