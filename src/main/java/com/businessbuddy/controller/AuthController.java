package com.businessbuddy.controller;

import com.businessbuddy.dto.AccountForms.RegistrationForm;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Login page and self-registration of a new business with its Owner (Table 4.38). */
@Controller
public class AuthController {

    /** Currencies offered in the registration and settings forms (ISO 4217). */
    public static final List<String> CURRENCIES = List.of("ZAR", "BWP", "NAD", "USD", "EUR", "GBP", "KES", "NGN", "ZMW");

    private final BusinessService businessService;

    public AuthController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegistrationForm());
        addLists(model);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result, Model model,
                           RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                businessService.register(form);
                redirect.addFlashAttribute("successMessage", "Your business is registered. Please log in.");
                return "redirect:/login";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        form.setPassword(null);
        form.setConfirmPassword(null);
        addLists(model);
        return "auth/register";
    }

    static void addLists(Model model) {
        model.addAttribute("currencies", CURRENCIES);
        Set<String> zones = new TreeSet<>();
        for (String z : ZoneId.getAvailableZoneIds()) {
            if (z.startsWith("Africa/") || z.startsWith("Europe/") || z.startsWith("America/")
                    || z.startsWith("Asia/") || z.startsWith("Australia/") || z.equals("UTC")) {
                zones.add(z);
            }
        }
        model.addAttribute("timeZones", zones);
    }
}
