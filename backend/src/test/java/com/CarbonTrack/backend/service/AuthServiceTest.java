package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.LoginRequest;
import com.CarbonTrack.backend.dto.RegisterRequest;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        service = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginRejectsUsernameAsPasswordEvenIfThatValueWasPreviouslySet() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        when(userRepository.findByUsername("CarbonUser"))
                .thenReturn(Optional.of(user));

        assertThrows(
                BadCredentialsException.class,
                () -> service.login(new LoginRequest("CarbonUser", "carbonuser"))
        );

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void registrationRejectsUsernameAsPassword() {
        RegisterRequest request = new RegisterRequest(
                "CarbonUser",
                "person@example.com",
                "carbonuser"
        );

        assertThrows(IllegalArgumentException.class, () -> service.register(request));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void loginStillAcceptsPasswordThatMatchesStoredHash() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        when(user.getRole()).thenReturn("USER");
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(userRepository.findByUsername("CarbonUser"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPassword1!", "stored-hash"))
                .thenReturn(true);
        when(jwtService.generateToken("CarbonUser", "USER"))
                .thenReturn("issued-token");

        service.login(new LoginRequest("CarbonUser", "StrongPassword1!"));

        verify(passwordEncoder).matches("StrongPassword1!", "stored-hash");
        verify(jwtService).generateToken("CarbonUser", "USER");
    }

    @Test
    void loginAcceptsUsernameWithoutCaseSensitivity() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        when(user.getRole()).thenReturn("USER");
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(userRepository.findByUsername("carbonuser"))
                .thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("carbonuser"))
                .thenReturn(List.of(user));
        when(passwordEncoder.matches("StrongPassword1!", "stored-hash"))
                .thenReturn(true);
        when(jwtService.generateToken("CarbonUser", "USER"))
                .thenReturn("issued-token");

        assertEquals(
                "issued-token",
                service.login(new LoginRequest(
                        "carbonuser",
                        "StrongPassword1!"
                ))
        );
        verify(passwordEncoder).matches("StrongPassword1!", "stored-hash");
    }

    @Test
    void loginRejectsAmbiguousCaseInsensitiveUsername() {
        User first = mock(User.class);
        User second = mock(User.class);
        when(userRepository.findByUsername("carbonuser"))
                .thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("carbonuser"))
                .thenReturn(List.of(first, second));
        when(userRepository.findByEmail("carbonuser"))
                .thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> service.login(new LoginRequest(
                        "carbonuser",
                        "StrongPassword1!"
                ))
        );
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void loginAcceptsRegisteredEmailAsIdentifier() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("CarbonUser");
        when(user.getRole()).thenReturn("USER");
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(userRepository.findByUsername("Person@Example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPassword1!", "stored-hash"))
                .thenReturn(true);
        when(jwtService.generateToken("CarbonUser", "USER"))
                .thenReturn("issued-token");

        String token = service.login(
                new LoginRequest("Person@Example.com", "StrongPassword1!"));

        assertEquals("issued-token", token);
        verify(passwordEncoder).matches("StrongPassword1!", "stored-hash");
        verify(jwtService).generateToken("CarbonUser", "USER");
    }
}
