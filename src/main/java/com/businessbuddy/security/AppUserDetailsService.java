package com.businessbuddy.security;

import com.businessbuddy.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Loads a user for Spring Security by e-mail. Emails are compared in lower case.
 * Lockout and deactivation are reported through the UserDetails flags, and Spring
 * Security checks them before the password (4.6.1).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    public AppUserDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        return users.findByEmail(normalised)
                .map(user -> new CurrentUser(user, Instant.now(clock)))
                .orElseThrow(() -> new UsernameNotFoundException("unknown"));
    }
}
