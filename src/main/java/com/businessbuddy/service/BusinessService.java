package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.AccountForms.BusinessSettingsForm;
import com.businessbuddy.dto.AccountForms.RegistrationForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.ExpenseCategory;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.ExpenseCategoryRepository;
import com.businessbuddy.repository.UserRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.util.Money;
import com.businessbuddy.validation.PasswordPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Business registration and settings (spec 8.1 and 8.2, decision "self-registration creates a
 * new business with its first Owner account").
 */
@Service
public class BusinessService {

    /** Default expense categories from spec 8.15, created for every new business. */
    public static final List<String> DEFAULT_EXPENSE_CATEGORIES = List.of("Rent", "Electricity", "Water",
            "Internet", "Transport", "Marketing", "Insurance", "Salaries", "Maintenance", "Other");

    private final BusinessRepository businesses;
    private final UserRepository users;
    private final ExpenseCategoryRepository expenseCategories;
    private final PasswordEncoder passwordEncoder;
    private final BusinessContext context;
    private final AuditService audit;

    public BusinessService(BusinessRepository businesses, UserRepository users,
                           ExpenseCategoryRepository expenseCategories, PasswordEncoder passwordEncoder,
                           BusinessContext context, AuditService audit) {
        this.businesses = businesses;
        this.users = users;
        this.expenseCategories = expenseCategories;
        this.passwordEncoder = passwordEncoder;
        this.context = context;
        this.audit = audit;
    }

    /** Registers a business and its Owner. Returns the new Owner. */
    @Transactional
    public User register(RegistrationForm form) {
        String email = form.getEmail().trim().toLowerCase();
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessRuleException("The passwords do not match");
        }
        PasswordPolicy.check(form.getPassword(), email).ifPresent(msg -> {
            throw new BusinessRuleException(msg);
        });
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        validateCurrencyAndZone(form.getCurrencyCode(), form.getTimeZone());

        Business business = new Business();
        business.setName(form.getBusinessName().trim());
        business.setCurrencyCode(form.getCurrencyCode());
        business.setTimeZone(form.getTimeZone());
        business.setEmail(email);
        businesses.save(business);
        createDefaultExpenseCategories(business);

        User owner = new User();
        owner.setBusiness(business);
        owner.setFullName(form.getFullName().trim());
        owner.setEmail(email);
        owner.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        owner.setRole(Role.OWNER);
        users.save(owner);
        audit.recordFor("BUSINESS_REGISTERED", owner.getId(), business.getId(), "BUSINESS", business.getId(), null);
        return owner;
    }

    public void createDefaultExpenseCategories(Business business) {
        for (String name : DEFAULT_EXPENSE_CATEGORIES) {
            ExpenseCategory category = new ExpenseCategory();
            category.setBusiness(business);
            category.setName(name);
            expenseCategories.save(category);
        }
    }

    @Transactional(readOnly = true)
    public BusinessSettingsForm settingsForm() {
        Business b = context.currentBusiness();
        BusinessSettingsForm form = new BusinessSettingsForm();
        form.setName(b.getName());
        form.setEmail(b.getEmail());
        form.setPhone(b.getPhone());
        form.setAddress(b.getAddress());
        form.setCurrencyCode(b.getCurrencyCode());
        form.setTimeZone(b.getTimeZone());
        form.setTaxRatePercent(Money.rateToPercent(b.getDefaultTaxRate()));
        form.setTargetMarginPercent(Money.rateToPercent(b.getDefaultTargetMargin()));
        form.setAllowNegativeStock(b.isAllowNegativeStock());
        return form;
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void updateSettings(BusinessSettingsForm form) {
        validateCurrencyAndZone(form.getCurrencyCode(), form.getTimeZone());
        Business b = context.currentBusiness();
        boolean negativeChanged = b.isAllowNegativeStock() != form.isAllowNegativeStock();
        b.setName(form.getName().trim());
        b.setEmail(blankToNull(form.getEmail()));
        b.setPhone(blankToNull(form.getPhone()));
        b.setAddress(blankToNull(form.getAddress()));
        b.setCurrencyCode(form.getCurrencyCode());
        b.setTimeZone(form.getTimeZone());
        b.setDefaultTaxRate(Money.percentToRate(form.getTaxRatePercent()));
        b.setDefaultTargetMargin(Money.percentToRate(form.getTargetMarginPercent()));
        b.setAllowNegativeStock(form.isAllowNegativeStock());
        audit.record("SETTINGS_CHANGED", "BUSINESS", b.getId(), Map.of(
                "taxRate", b.getDefaultTaxRate().toPlainString(),
                "targetMargin", b.getDefaultTargetMargin().toPlainString(),
                "allowNegativeStock", negativeChanged ? "changed to " + b.isAllowNegativeStock() : "unchanged"));
    }

    static void validateCurrencyAndZone(String currencyCode, String timeZone) {
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("Choose a valid currency");
        }
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException | NullPointerException e) {
            throw new BusinessRuleException("Choose a valid time zone");
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
