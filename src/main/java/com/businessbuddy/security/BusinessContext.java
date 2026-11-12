package com.businessbuddy.security;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.BusinessOwnedEntity;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.Optional;

/**
 * Answers "who is the current user and which business are they working in?" for every
 * service (Figure 4.6, 4.6.2).
 *
 * <p>The business id always comes from the authenticated session, never from a form or
 * URL, so a user cannot act for another business by changing a request.</p>
 */
@Component
public class BusinessContext {

    private final BusinessRepository businesses;
    private final UserRepository users;

    public BusinessContext(BusinessRepository businesses, UserRepository users) {
        this.businesses = businesses;
        this.users = users;
    }

    public Optional<CurrentUser> currentUserIfAny() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public CurrentUser currentUser() {
        return currentUserIfAny().orElseThrow(() -> new AccessDeniedException("Not logged in"));
    }

    /**
     * The business of the current user. An Administrator has no business and is refused
     * here, because Administrators manage accounts and do not touch business records (4.8.2).
     */
    public Long currentBusinessId() {
        Long id = currentUser().getBusinessId();
        if (id == null) {
            throw new AccessDeniedException("This account does not belong to a business");
        }
        return id;
    }

    public Business currentBusiness() {
        return businesses.findById(currentBusinessId()).orElseThrow(() -> new NotFoundException("Business"));
    }

    /** A reference to the current user row, used for created_by style columns. */
    public User currentUserEntity() {
        return users.getReferenceById(currentUser().getUserId());
    }

    /** Throws 404 unless the record belongs to the current business (LoadOwnedRecord, 4.6.2). */
    public <T extends BusinessOwnedEntity> T requireSameBusiness(T entity, String what) {
        if (entity == null || !Objects.equals(entity.owningBusinessId(), currentBusinessId())) {
            throw new NotFoundException(what);
        }
        return entity;
    }

    /** Client IP address for the audit log, or null outside a web request. */
    public static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
