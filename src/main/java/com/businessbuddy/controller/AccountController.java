package com.businessbuddy.controller;

import com.businessbuddy.dto.AccountForms.BusinessSettingsForm;
import com.businessbuddy.dto.AccountForms.PasswordChangeForm;
import com.businessbuddy.dto.AccountForms.StaffForm;
import com.businessbuddy.dto.CatalogForms.CategoryForm;
import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.AuditLogRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.BusinessService;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.NotificationService;
import com.businessbuddy.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Settings (business profile, categories, users), the user's own profile, notifications,
 * the audit log and the Administrator's user list. Access follows Table 4.37 through the
 * URL rules in SecurityConfig and @PreAuthorize on the services.
 */
@Controller
public class AccountController {

    private final BusinessService businessService;
    private final UserService userService;
    private final CatalogService catalog;
    private final NotificationService notifications;
    private final AuditLogRepository auditLogs;
    private final BusinessContext context;

    public AccountController(BusinessService businessService, UserService userService, CatalogService catalog,
                             NotificationService notifications, AuditLogRepository auditLogs, BusinessContext context) {
        this.businessService = businessService;
        this.userService = userService;
        this.catalog = catalog;
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.context = context;
    }

    // --- Business settings (Owner) ------------------------------------------------------

    @GetMapping("/settings/business")
    public String business(Model model) {
        model.addAttribute("form", businessService.settingsForm());
        AuthController.addLists(model);
        return "settings/business";
    }

    @PostMapping("/settings/business")
    public String saveBusiness(@Valid @ModelAttribute("form") BusinessSettingsForm form, BindingResult result,
                               Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                businessService.updateSettings(form);
                redirect.addFlashAttribute("successMessage", "Business settings saved.");
                return "redirect:/settings/business";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        AuthController.addLists(model);
        return "settings/business";
    }

    // --- Categories (Owner, Manager) ----------------------------------------------------

    @GetMapping("/settings/categories")
    public String categories(Model model) {
        model.addAttribute("categories", catalog.categories(null));
        model.addAttribute("expenseCategories", catalog.expenseCategories());
        model.addAttribute("form", new CategoryForm());
        model.addAttribute("types", CategoryType.values());
        return "settings/categories";
    }

    @PostMapping("/settings/categories")
    public String addCategory(@Valid @ModelAttribute("form") CategoryForm form, BindingResult result,
                              RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Enter a category name of up to 80 characters.");
        } else {
            catalog.createCategory(form);
            redirect.addFlashAttribute("successMessage", "Category added.");
        }
        return "redirect:/settings/categories";
    }

    @PostMapping("/settings/expense-categories")
    public String addExpenseCategory(@RequestParam String name, RedirectAttributes redirect) {
        catalog.createExpenseCategory(name);
        redirect.addFlashAttribute("successMessage", "Expense category added.");
        return "redirect:/settings/categories";
    }

    // --- Staff accounts (Owner) ---------------------------------------------------------

    @GetMapping("/settings/users")
    public String users(Model model) {
        model.addAttribute("users", userService.staff());
        model.addAttribute("form", new StaffForm());
        model.addAttribute("roles", new Role[]{Role.MANAGER, Role.EMPLOYEE});
        return "settings/users";
    }

    @PostMapping("/settings/users")
    public String addUser(@Valid @ModelAttribute("form") StaffForm form, BindingResult result, Model model,
                          RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                userService.createStaff(form);
                redirect.addFlashAttribute("successMessage", "User account created.");
                return "redirect:/settings/users";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        form.setPassword(null);
        model.addAttribute("users", userService.staff());
        model.addAttribute("roles", new Role[]{Role.MANAGER, Role.EMPLOYEE});
        return "settings/users";
    }

    @PostMapping("/settings/users/{id}")
    public String updateUser(@PathVariable Long id, @RequestParam Role role,
                             @RequestParam(defaultValue = "false") boolean active, RedirectAttributes redirect) {
        userService.updateStaff(id, role, active);
        redirect.addFlashAttribute("successMessage", "User account updated.");
        return "redirect:/settings/users";
    }

    // --- Own profile (all users) --------------------------------------------------------

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult result,
                                 HttpSession session, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                userService.changePassword(form, session.getId());
                redirect.addFlashAttribute("successMessage", "Password changed. Your other sessions have been ended.");
                return "redirect:/profile";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return "profile";
    }

    // --- Notifications (all business users) ---------------------------------------------

    @GetMapping("/notifications")
    public String notifications(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", notifications.listForCurrentUser(PageRequest.of(Math.max(page, 0), 25)));
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable Long id) {
        notifications.markRead(id);
        return "redirect:/notifications";
    }

    // --- Audit log (Owner: own business; Administrator: all) ----------------------------

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page, Model model) {
        CurrentUser user = context.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), 50);
        model.addAttribute("page", user.getRole() == Role.ADMINISTRATOR ? auditLogs.findAllByOrderByIdDesc(pageable)
                : auditLogs.findByBusinessIdOrderByIdDesc(context.currentBusinessId(), pageable));
        return "audit";
    }

    // --- Administrator: all accounts ----------------------------------------------------

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("users", userService.allUsers());
        model.addAttribute("now", java.time.Instant.now());
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/lock")
    public String lock(@PathVariable Long id, @RequestParam boolean locked, RedirectAttributes redirect) {
        userService.adminSetLocked(id, locked);
        redirect.addFlashAttribute("successMessage", locked ? "Account locked." : "Account unlocked.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/active")
    public String active(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        userService.adminSetActive(id, active);
        redirect.addFlashAttribute("successMessage", active ? "Account activated." : "Account deactivated.");
        return "redirect:/admin/users";
    }
}
