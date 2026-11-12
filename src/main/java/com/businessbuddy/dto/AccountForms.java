package com.businessbuddy.dto;

import com.businessbuddy.entity.Enums.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Form objects for registration, settings and user accounts (Tables 4.38 and 4.39).
 * Forms bind to these DTOs, never to entities, so hidden fields such as role or
 * business_id cannot be injected into a request (mass-assignment protection, 4.8.3).
 */
public final class AccountForms {

    private AccountForms() {
    }

    public static class RegistrationForm {
        @NotBlank @Size(min = 2, max = 150) private String businessName;
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Choose a currency") private String currencyCode = "ZAR";
        @NotBlank @Size(max = 64) private String timeZone = "Africa/Johannesburg";
        @NotBlank @Size(min = 2, max = 120) private String fullName;
        @NotBlank @Email @Size(max = 255) private String email;
        @NotBlank private String password;
        @NotBlank private String confirmPassword;

        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class BusinessSettingsForm {
        @NotBlank @Size(min = 2, max = 150) private String name;
        @Email @Size(max = 255) private String email;
        @Size(max = 30) @Pattern(regexp = "^[0-9+ ()-]*$", message = "Use digits, spaces, + and - only")
        private String phone;
        @Size(max = 300) private String address;
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Choose a currency") private String currencyCode;
        @NotBlank @Size(max = 64) private String timeZone;
        @NotNull @DecimalMin("0") @DecimalMax("99.99") private BigDecimal taxRatePercent = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @DecimalMax("99.99") private BigDecimal targetMarginPercent = BigDecimal.ZERO;
        private boolean allowNegativeStock;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
        public BigDecimal getTaxRatePercent() { return taxRatePercent; }
        public void setTaxRatePercent(BigDecimal taxRatePercent) { this.taxRatePercent = taxRatePercent; }
        public BigDecimal getTargetMarginPercent() { return targetMarginPercent; }
        public void setTargetMarginPercent(BigDecimal targetMarginPercent) { this.targetMarginPercent = targetMarginPercent; }
        public boolean isAllowNegativeStock() { return allowNegativeStock; }
        public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
    }

    /** A staff account created by an Owner. The Owner can only give the Manager or Employee role. */
    public static class StaffForm {
        @NotBlank @Size(min = 2, max = 120) private String fullName;
        @NotBlank @Email @Size(max = 255) private String email;
        @NotNull private Role role = Role.EMPLOYEE;
        private String password;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Role getRole() { return role; }
        public void setRole(Role role) { this.role = role; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class PasswordChangeForm {
        @NotBlank private String currentPassword;
        @NotBlank private String newPassword;
        @NotBlank private String confirmPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}
