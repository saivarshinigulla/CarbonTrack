package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.entity.PasswordResetToken;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.PasswordResetTokenRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import com.CarbonTrack.backend.util.Sha256;
import com.CarbonTrack.backend.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private PasswordResetRateLimiter rateLimiter;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        rateLimiter = mock(PasswordResetRateLimiter.class);
        when(rateLimiter.allowRequest(any(), any())).thenReturn(true);

        service = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                eventPublisher,
                rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30)
        );
    }

    @Test
    void requestForUnknownEmailReturnsWithoutCreatingTokenOrEvent() {
        when(userRepository.findByEmailIgnoreCaseForUpdate("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.requestReset(" Unknown@Example.com ", "192.0.2.10"));

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void requestStoresOnlySha256HashAndPublishesDeliveryEvent() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("person@example.com");
        when(userRepository.findByEmailIgnoreCaseForUpdate("person@example.com"))
                .thenReturn(Optional.of(user));

        service.requestReset("Person@Example.com", "192.0.2.10");

        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).deleteAllByUserId(42L);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken storedToken = tokenCaptor.getValue();
        assertEquals(NOW, storedToken.getCreatedAt());
        assertEquals(NOW.plus(Duration.ofMinutes(30)), storedToken.getExpiresAt());
        assertEquals(64, storedToken.getTokenHash().length());

        var eventCaptor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        String rawToken = eventCaptor.getValue().rawToken();
        assertNotEquals(rawToken, storedToken.getTokenHash());
        assertEquals(Sha256.hex(rawToken), storedToken.getTokenHash());
        verify(rateLimiter).allowRequest(
                "person@example.com",
                "192.0.2.10");
    }

    @Test
    void successfulConfirmationChangesPasswordAndConsumesToken() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        PasswordResetToken token = new PasswordResetToken(
                user,
                Sha256.hex("raw-token"),
                NOW.minusSeconds(10),
                NOW.plusSeconds(60)
        );
        when(tokenRepository.findByTokenHashForUpdate(Sha256.hex("raw-token")))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword1!"))
                .thenReturn("encoded-password");

        service.confirmReset("raw-token", "NewPassword1!");

        verify(user).setPasswordHash("encoded-password");
        verify(userRepository).save(user);
        verify(passwordEncoder).encode("NewPassword1!");
        assertEquals(NOW, token.getConsumedAt());
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPasswordUsesBcryptAndCanAuthenticateThroughLogin() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        User user = new User(
                "CarbonUser",
                "person@example.com",
                bcrypt.encode("OldPassword1!"),
                "USER"
        );
        String resetToken = "valid-reset-token";
        PasswordResetToken token = new PasswordResetToken(
                user,
                Sha256.hex(resetToken),
                NOW.minusSeconds(30),
                NOW.plusSeconds(60)
        );
        when(tokenRepository.findByTokenHashForUpdate(Sha256.hex(resetToken)))
                .thenReturn(Optional.of(token));

        PasswordResetService bcryptResetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                bcrypt,
                eventPublisher,
                rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30)
        );
        bcryptResetService.confirmReset(resetToken, "NewPassword1!");

        assertTrue(bcrypt.matches("NewPassword1!", user.getPasswordHash()));
        assertFalse(bcrypt.matches("OldPassword1!", user.getPasswordHash()));
        assertEquals(NOW, token.getConsumedAt());

        when(userRepository.findByUsername("person@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("person@example.com"))
                .thenReturn(java.util.List.of());
        when(userRepository.findByEmail("person@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.findByUsername("carbonuser"))
                .thenReturn(Optional.of(user));
        when(userRepository.findByUsername("CarbonUser"))
                .thenReturn(Optional.of(user));
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken("CarbonUser", "USER"))
                .thenReturn("login-token");
        AuthService authService = new AuthService(userRepository, bcrypt, jwtService);

        assertEquals(
                "login-token",
                authService.login(new LoginRequest(
                        "person@example.com",
                        "NewPassword1!"
                ))
        );
        assertEquals(
                "login-token",
                authService.login(new LoginRequest(
                        "CarbonUser",
                        "NewPassword1!"
                ))
        );
        verify(jwtService, times(2)).generateToken("CarbonUser", "USER");
    }

    @Test
    void confirmationRejectsUsernameAsNewPassword() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        PasswordResetToken token = new PasswordResetToken(
                user,
                Sha256.hex("raw-token"),
                NOW.minusSeconds(10),
                NOW.plusSeconds(60)
        );
        when(tokenRepository.findByTokenHashForUpdate(Sha256.hex("raw-token")))
                .thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.confirmReset("raw-token", "carbonuser")
        );

        assertEquals(400, error.getStatusCode().value());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejectedWithoutChangingPassword() {
        User user = mock(User.class);
        PasswordResetToken token = new PasswordResetToken(
                user,
                Sha256.hex("expired-token"),
                NOW.minusSeconds(60),
                NOW
        );
        when(tokenRepository.findByTokenHashForUpdate(Sha256.hex("expired-token")))
                .thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.confirmReset("expired-token", "NewPassword1!")
        );

        assertEquals(400, error.getStatusCode().value());
        assertEquals(PasswordResetService.INVALID_TOKEN_MESSAGE, error.getReason());
        verify(user, never()).setPasswordHash(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void consumedTokenCannotBeReused() {
        User user = mock(User.class);
        PasswordResetToken token = new PasswordResetToken(
                user,
                Sha256.hex("one-use-token"),
                NOW.minusSeconds(60),
                NOW.plusSeconds(60)
        );
        token.consume(NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHashForUpdate(Sha256.hex("one-use-token")))
                .thenReturn(Optional.of(token));

        assertThrows(
                ResponseStatusException.class,
                () -> service.confirmReset("one-use-token", "NewPassword1!")
        );

        verify(user, never()).setPasswordHash(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rateLimitedRequestsDoNotLookUpAccounts() {
        when(rateLimiter.allowRequest("person@example.com", "192.0.2.10"))
                .thenReturn(false);

        service.requestReset("Person@Example.com", "192.0.2.10");

        verify(userRepository, never()).findByEmailIgnoreCaseForUpdate(any());
        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
