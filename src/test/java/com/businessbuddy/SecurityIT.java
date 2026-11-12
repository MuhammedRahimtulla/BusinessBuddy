package com.businessbuddy;

import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.AuditLogRepository;
import com.businessbuddy.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests through the full web stack with MockMvc (NFR-01, Tables 4.37 and 4.50,
 * spec 16 "Security tests"). Requests pass through the real Spring Security filter chain.
 */
class SecurityIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired AuditLogRepository audit;

    private CurrentUser principal(User u) {
        return new CurrentUser(users.findById(u.getId()).orElseThrow(), Instant.now());
    }

    @Test
    @DisplayName("TC-SEC-01: a correct email and password log in and open the dashboard")
    void loginSuccess() throws Exception {
        User owner = newBusinessOwner("Login");
        mvc.perform(formLogin("/login").user(owner.getEmail()).password(PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(users.findById(owner.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-SEC-02: a wrong password gives the generic error, the same as an unknown email")
    void loginFailureGeneric() throws Exception {
        User owner = newBusinessOwner("Login fail");
        mvc.perform(formLogin("/login").user(owner.getEmail()).password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").user("nobody@nowhere.test").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
        assertThat(users.findById(owner.getId()).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SEC-03: five failed logins lock the account; the correct password is then refused")
    void lockout() throws Exception {
        User owner = newBusinessOwner("Lockout");
        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin("/login").user(owner.getEmail()).password("wrong-" + i));
        }
        assertThat(users.findById(owner.getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
        mvc.perform(formLogin("/login").user(owner.getEmail()).password(PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
        assertThat(audit.findByActionOrderByIdDesc("ACCOUNT_LOCKED")).isNotEmpty();
    }

    @Test
    @DisplayName("TC-SEC-04: an anonymous request for a protected page is redirected to the login page")
    void anonymousRedirected() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @Test
    @DisplayName("TC-SEC-05: an Employee typing the URL of purchases, reports or settings gets 403")
    void employeeRestricted() throws Exception {
        newBusinessOwner("Roles");
        CurrentUser employee = principal(addStaff(Role.EMPLOYEE));
        for (String url : new String[]{"/purchases", "/reports", "/reports/export/profitability", "/expenses",
                "/settings/users", "/forecasts", "/pricing", "/inventory/adjust"}) {
            mvc.perform(get(url).with(user(employee))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/sales/new").with(user(employee))).andExpect(status().isOk());
        mvc.perform(get("/inventory/waste").with(user(employee))).andExpect(status().isOk());
        assertThat(audit.findByActionOrderByIdDesc("ACCESS_DENIED")).isNotEmpty();
    }

    @Test
    @DisplayName("TC-SEC-06: a Manager cannot manage users or business settings; an Owner can")
    void managerRestricted() throws Exception {
        User owner = newBusinessOwner("Manager limits");
        CurrentUser manager = principal(addStaff(Role.MANAGER));
        mvc.perform(get("/settings/users").with(user(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/settings/business").with(user(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/reports").with(user(manager))).andExpect(status().isOk());
        mvc.perform(get("/settings/users").with(user(principal(owner)))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-07: a POST without a CSRF token is rejected")
    void csrfRequired() throws Exception {
        User owner = newBusinessOwner("CSRF");
        mvc.perform(post("/suppliers").param("name", "Sneaky").with(user(principal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/suppliers").param("name", "Honest").param("active", "true")
                        .with(user(principal(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("TC-SEC-08: opening another business's product by URL gives 404, not its data")
    void crossBusinessByUrl() throws Exception {
        newBusinessOwner("Business A");
        Long secret = product("Secret recipe cake", ProductType.MANUFACTURED, "99.00");
        User ownerB = newBusinessOwner("Business B");
        mvc.perform(get("/products/" + secret).with(user(principal(ownerB))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret recipe cake"))));
    }

    @Test
    @DisplayName("TC-SEC-09: invalid form input is rejected on the server with field messages")
    void serverSideValidation() throws Exception {
        User owner = newBusinessOwner("Validation");
        mvc.perform(post("/ingredients").param("name", "").param("baseUnitCode", "g").param("minStockLevel", "-5")
                        .with(user(principal(owner))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "minStockLevel"));
    }

    @Test
    @DisplayName("TC-SEC-11: security headers (CSP, frame denial, no-sniff) are sent")
    void securityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("TC-SEC-12: passwords are stored as BCrypt hashes, never in plain text")
    void passwordsHashed() {
        User owner = newBusinessOwner("Hashing");
        String hash = users.findById(owner.getId()).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
    }
}
