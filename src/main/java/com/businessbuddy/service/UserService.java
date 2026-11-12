package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.AccountForms.PasswordChangeForm;
import com.businessbuddy.dto.AccountForms.StaffForm;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.UserRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.validation.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User accounts: staff management by Owners, password changes, and the Administrator's
 * lock, unlock and deactivate functions (spec 8.1, Table 4.37, Table 4.50).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BusinessContext context;
    private final AuditService audit;
    private final SessionRegistry sessionRegistry;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, BusinessContext context,
                       AuditService audit, SessionRegistry sessionRegistry) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.context = context;
        this.audit = audit;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<User> staff() {
        return users.findByBusinessIdOrderByFullName(context.currentBusinessId());
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public User createStaff(StaffForm form) {
        requireStaffRole(form.getRole());
        String email = form.getEmail().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        PasswordPolicy.check(form.getPassword(), email).ifPresent(msg -> {
            throw new BusinessRuleException(msg);
        });
        User user = new User();
        user.setBusiness(context.currentBusiness());
        user.setFullName(form.getFullName().trim());
        user.setEmail(email);
        user.setRole(form.getRole());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        users.save(user);
        audit.record("USER_CREATED", "USER", user.getId(), Map.of("role", user.getRole().name()));
        return user;
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void updateStaff(Long userId, Role role, boolean active) {
        User user = users.findByIdAndBusinessId(userId, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("User"));
        if (Objects.equals(user.getId(), context.currentUser().getUserId())) {
            throw new BusinessRuleException("You cannot change your own role or deactivate yourself");
        }
        if (user.getRole() == Role.OWNER) {
            throw new BusinessRuleException("Another Owner's account cannot be changed here");
        }
        requireStaffRole(role);
        Role oldRole = user.getRole();
        boolean wasActive = user.isActive();
        user.setRole(role);
        user.setActive(active);
        if (!active && wasActive) {
            expireSessions(user.getId(), null);
        }
        audit.record("USER_UPDATED", "USER", user.getId(), Map.of(
                "role", oldRole + " -> " + role, "active", wasActive + " -> " + active));
    }

    /**
     * Changes the current user's password. The current password is required, and every
     * other session of the user is ended (Table 4.50).
     */
    @Transactional
    public void changePassword(PasswordChangeForm form, String currentSessionId) {
        CurrentUser current = context.currentUser();
        User user = users.findById(current.getUserId()).orElseThrow(() -> new NotFoundException("User"));
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is not correct");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessRuleException("The new passwords do not match");
        }
        PasswordPolicy.check(form.getNewPassword(), user.getEmail()).ifPresent(msg -> {
            throw new BusinessRuleException(msg);
        });
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        expireSessions(user.getId(), currentSessionId);
        audit.record("PASSWORD_CHANGED", "USER", user.getId());
    }

    // --- Administrator functions -------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<User> allUsers() {
        return users.findAllWithBusiness();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void adminSetLocked(Long userId, boolean locked) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
        if (user.getRole() == Role.ADMINISTRATOR) {
            throw new BusinessRuleException("Administrator accounts cannot be locked here");
        }
        user.setLockedUntil(locked ? Instant.now().plusSeconds(100L * 365 * 24 * 3600) : null);
        user.setFailedLoginAttempts(0);
        if (locked) {
            expireSessions(user.getId(), null);
        }
        audit.record(locked ? "ACCOUNT_LOCKED_BY_ADMIN" : "ACCOUNT_UNLOCKED", "USER", user.getId());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void adminSetActive(Long userId, boolean active) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
        if (user.getRole() == Role.ADMINISTRATOR) {
            throw new BusinessRuleException("Administrator accounts cannot be deactivated here");
        }
        user.setActive(active);
        if (!active) {
            expireSessions(user.getId(), null);
        }
        audit.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", user.getId());
    }

    /**
     * Creates the first Administrator from configuration if none exists. Credentials come from
     * environment variables and are never stored in source code (4.8.4).
     */
    @Transactional
    public void ensureAdministrator(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()
                || users.existsByRole(Role.ADMINISTRATOR)) {
            return;
        }
        PasswordPolicy.check(password, email).ifPresent(msg -> {
            throw new IllegalStateException("Administrator password rejected: " + msg);
        });
        User admin = new User();
        admin.setFullName("System Administrator");
        admin.setEmail(email);
        admin.setRole(Role.ADMINISTRATOR);
        admin.setPasswordHash(passwordEncoder.encode(password));
        users.save(admin);
        audit.recordFor("ADMIN_CREATED", admin.getId(), null, "USER", admin.getId(), null);
        log.info("Administrator account created for {}", email);
    }

    private static void requireStaffRole(Role role) {
        if (role != Role.MANAGER && role != Role.EMPLOYEE) {
            throw new BusinessRuleException("Staff can be given the Manager or Employee role only");
        }
    }

    private void expireSessions(Long userId, String keepSessionId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof CurrentUser cu && Objects.equals(cu.getUserId(), userId)) {
                for (SessionInformation info : sessionRegistry.getAllSessions(principal, false)) {
                    if (!info.getSessionId().equals(keepSessionId)) {
                        info.expireNow();
                    }
                }
            }
        }
    }
}
