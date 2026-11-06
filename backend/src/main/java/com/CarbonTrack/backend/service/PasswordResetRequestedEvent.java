package com.CarbonTrack.backend.service;

public record PasswordResetRequestedEvent(
        Long userId,
        String email,
        String rawToken
) {
}
