package com.businessbuddy.controller;

import com.businessbuddy.dto.CatalogForms.SupplierForm;
import com.businessbuddy.entity.Supplier;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.SupplierRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Supplier list and form (Table 4.41). Suppliers are deactivated, never deleted. */
@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    private final CatalogService catalog;
    private final SupplierRepository suppliers;
    private final BusinessContext context;

    public SupplierController(CatalogService catalog, SupplierRepository suppliers, BusinessContext context) {
        this.catalog = catalog;
        this.suppliers = suppliers;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", suppliers.search(context.currentBusinessId(), q.trim(),
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("q", q);
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new SupplierForm());
        model.addAttribute("id", null);
        return "suppliers/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Supplier s = catalog.supplier(id);
        SupplierForm form = new SupplierForm();
        form.setName(s.getName());
        form.setContactPerson(s.getContactPerson());
        form.setEmail(s.getEmail());
        form.setPhone(s.getPhone());
        form.setAddress(s.getAddress());
        form.setNotes(s.getNotes());
        form.setActive(s.isActive());
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        return "suppliers/form";
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") SupplierForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                catalog.saveSupplier(id, form);
                redirect.addFlashAttribute("successMessage", "Supplier saved.");
                return "redirect:/suppliers";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        model.addAttribute("id", id);
        return "suppliers/form";
    }
}
