package com.businessbuddy.security;

import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The logged-in user as held in the session (4.6.1: "store user id, role and business id").
 * Equality is by user id so the session registry can find every session of one user,
 * which is needed to end other sessions after a password change (Table 4.50).
 */
public class CurrentUser implements UserDetails {

    private final Long userId;
    private final Long businessId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;
    private final boolean locked;

    public CurrentUser(User user, Instant now) {
        this.userId = user.getId();
        this.businessId = user.owningBusinessId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
        this.locked = user.isLocked(now);
    }

    public Long getUserId() { return userId; }
    public Long getBusinessId() { return businessId; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    public boolean hasRole(Role... roles) {
        for (Role r : roles) {
            if (r == role) {
                return true;
            }
        }
        return false;
    }

    /** Owners and Managers see costs, profits and expenses; Employees do not (4.8.2). */
    public boolean canSeeMoney() {
        return hasRole(Role.OWNER, Role.MANAGER);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isEnabled() { return active; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CurrentUser other && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() { return Objects.hashCode(userId); }

    @Override
    public String toString() { return "CurrentUser[" + userId + "]"; }
}
