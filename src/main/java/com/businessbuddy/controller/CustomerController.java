package com.businessbuddy.controller;

import com.businessbuddy.dto.CatalogForms.CustomerForm;
import com.businessbuddy.entity.Customer;
import com.businessbuddy.repository.CustomerRepository;
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

/** Customers: all business roles may add and view (Table 4.37). */
@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CatalogService catalog;
    private final CustomerRepository customers;
    private final BusinessContext context;

    public CustomerController(CatalogService catalog, CustomerRepository customers, BusinessContext context) {
        this.catalog = catalog;
        this.customers = customers;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", customers.search(context.currentBusinessId(), q.trim(),
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("q", q);
        return "customers/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new CustomerForm());
        model.addAttribute("id", null);
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Customer c = catalog.customer(id);
        CustomerForm form = new CustomerForm();
        form.setName(c.getName());
        form.setEmail(c.getEmail());
        form.setPhone(c.getPhone());
        form.setNotes(c.getNotes());
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        return "customers/form";
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") CustomerForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("id", id);
            return "customers/form";
        }
        catalog.saveCustomer(id, form);
        redirect.addFlashAttribute("successMessage", "Customer saved.");
        return "redirect:/customers";
    }
}
