package com.businessbuddy.security;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.Enums.Role;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Spring Security callbacks for login success, login failure and access denial.
 * They connect the security filter chain to {@link LoginAttemptService} and the audit log.
 */
@Component
public class LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler,
        AccessDeniedHandler {

    private final LoginAttemptService attempts;
    private final AuditService audit;

    public LoginHandlers(LoginAttemptService attempts, AuditService audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CurrentUser user = (CurrentUser) authentication.getPrincipal();
        attempts.loginSucceeded(user.getUserId());
        String target = user.getRole() == Role.ADMINISTRATOR ? "/admin/users" : "/dashboard";
        response.sendRedirect(request.getContextPath() + target);
    }

    /**
     * Every failure shows the same message so attackers cannot tell which emails exist.
     * A running lockout is reported separately, as specified in 4.6.1.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        attempts.loginFailed(request.getParameter("username"));
        String target = exception instanceof LockedException ? "/login?locked" : "/login?error";
        response.sendRedirect(request.getContextPath() + target);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        audit.record("ACCESS_DENIED", "PATH", null, Map.of("path", request.getRequestURI(),
                "method", request.getMethod()));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.getRequestDispatcher("/error/403").forward(request, response);
    }
}
