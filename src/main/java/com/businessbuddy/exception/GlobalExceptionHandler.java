package com.businessbuddy.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Central error handling (spec section 11). Users see a friendly page; stack traces and
 * SQL errors are logged on the server only and never shown (A05, Table 4.51).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView notFound(HttpServletResponse response) {
        response.setStatus(404);
        return new ModelAndView("error/404");
    }

    /**
     * A broken business rule on a POST returns the user to the page they came from with the
     * message shown as an alert. The user's typed values are kept where the controller
     * handles the rule itself (form pages catch BusinessRuleException directly).
     */
    @ExceptionHandler(BusinessRuleException.class)
    public Object businessRule(BusinessRuleException ex, HttpServletRequest request, RedirectAttributes redirect,
                               HttpServletResponse response) {
        String referer = request.getHeader("Referer");
        if ("POST".equals(request.getMethod()) && referer != null && sameOrigin(referer, request)) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return new RedirectView(referer);
        }
        response.setStatus(422);
        ModelAndView mav = new ModelAndView("error/error");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException ex, HttpServletResponse response) {
        log.warn("Database constraint rejected a change: {}", ex.getMostSpecificCause().getMessage());
        response.setStatus(409);
        ModelAndView mav = new ModelAndView("error/error");
        mav.addObject("message", "The change could not be saved because it conflicts with existing data.");
        return mav;
    }

    /** Method-level @PreAuthorize failures are re-thrown so Spring Security's 403 handling applies. */
    @ExceptionHandler(AccessDeniedException.class)
    public void accessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex, HttpServletResponse response) {
        log.error("Unexpected error", ex);
        response.setStatus(500);
        ModelAndView mav = new ModelAndView("error/error");
        mav.addObject("message", "Something went wrong. The problem has been logged.");
        return mav;
    }

    private static boolean sameOrigin(String referer, HttpServletRequest request) {
        String origin = request.getScheme() + "://" + request.getServerName();
        return referer.startsWith(origin);
    }
}
