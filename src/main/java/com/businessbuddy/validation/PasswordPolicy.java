package com.businessbuddy.validation;

import java.util.Optional;
import java.util.Set;

/**
 * Password policy from Table 4.50: at least 10 characters, not equal to the e-mail address,
 * and not on a list of common passwords. Returns the problem as a user-facing message.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    /** A short list of passwords that appear at the top of public breach lists. */
    private static final Set<String> COMMON = Set.of(
            "1234567890", "12345678910", "123456789a", "password123", "password12", "password1!",
            "qwertyuiop", "qwerty12345", "iloveyou123", "admin12345", "welcome123", "letmein123",
            "abcdefghij", "abc1234567", "passw0rd123", "1q2w3e4r5t", "zaq12wsxcde", "football123",
            "monkey12345", "sunshine123", "princess123", "dragon12345", "baseball123", "1111111111",
            "0000000000", "businessbuddy", "businessbuddy1", "changeme123", "p@ssw0rd123", "password");

    private PasswordPolicy() {
    }

    public static Optional<String> check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > 72) {
            return Optional.of("Password must be at most 72 characters");   // BCrypt input limit
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            return Optional.of("Password must not be the same as the email address");
        }
        if (COMMON.contains(password.toLowerCase())) {
            return Optional.of("This password is too common. Choose another one");
        }
        return Optional.empty();
    }
}
