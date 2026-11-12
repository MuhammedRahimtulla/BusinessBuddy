package com.businessbuddy.security;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Login attempt protection (4.6.1, Table 4.50): after {@code maxFailures} consecutive
 * failures the account is locked for {@code lockoutMinutes}. Every outcome is audited.
 */
@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lockout;

    public LoginAttemptService(UserRepository users, AuditService audit, Clock clock,
                               @Value("${businessbuddy.security.max-failed-logins}") int maxFailures,
                               @Value("${businessbuddy.security.lockout-minutes}") int lockoutMinutes) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    @Transactional
    public void loginSucceeded(Long userId) {
        User user = users.findById(userId).orElseThrow();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now(clock));
        audit.recordFor("LOGIN_SUCCESS", user.getId(), user.owningBusinessId(), "USER", user.getId(), null);
    }

    /**
     * Counts a failed attempt. An unknown email is audited without a user so that the log
     * does not store guessed addresses.
     */
    @Transactional
    public void loginFailed(String email) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        User user = users.findByEmail(normalised).orElse(null);
        if (user == null) {
            audit.recordFor("LOGIN_FAILED", null, null, null, null, null);
            return;
        }
        Instant now = Instant.now(clock);
        if (user.isLocked(now)) {
            audit.recordFor("LOGIN_BLOCKED", user.getId(), user.owningBusinessId(), "USER", user.getId(), null);
            return;
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxFailures) {
            user.setLockedUntil(now.plus(lockout));
            user.setFailedLoginAttempts(0);
            audit.recordFor("ACCOUNT_LOCKED", user.getId(), user.owningBusinessId(), "USER", user.getId(),
                    Map.of("lockedMinutes", lockout.toMinutes()));
        }
        audit.recordFor("LOGIN_FAILED", user.getId(), user.owningBusinessId(), "USER", user.getId(), null);
    }
}
