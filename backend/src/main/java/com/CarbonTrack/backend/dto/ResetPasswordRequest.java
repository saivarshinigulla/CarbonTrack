package com.CarbonTrack.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 128, message = "Reset token is invalid")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72,
                message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$",
                message = "Password must include uppercase and lowercase letters, a number, and a symbol"
        )
        String newPassword
) {
}
