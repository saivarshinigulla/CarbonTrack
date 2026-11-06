package com.CarbonTrack.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetRequestValidationTest {

    private static Validator validator;
    private static AutoCloseable validationFactory;

    @BeforeAll
    static void setUpValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validationFactory = factory::close;
    }

    @AfterAll
    static void closeValidator() throws Exception {
        validationFactory.close();
    }

    @Test
    void confirmationRequiresExistingPasswordStrengthRules() {
        assertFalse(validator.validate(
                new ResetPasswordRequest("a-valid-token", "weakpassword")
        ).isEmpty());
        assertTrue(validator.validate(
                new ResetPasswordRequest("a-valid-token", "CarbonTrack1!")
        ).isEmpty());
    }

    @Test
    void requestRequiresAValidEmailAddress() {
        assertFalse(validator.validate(
                new ForgotPasswordRequest("not-an-email")
        ).isEmpty());
        assertTrue(validator.validate(
                new ForgotPasswordRequest("person@example.com")
        ).isEmpty());
    }
}
