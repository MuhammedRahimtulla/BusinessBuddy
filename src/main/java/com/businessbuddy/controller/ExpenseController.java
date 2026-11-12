package com.businessbuddy.controller;

import com.businessbuddy.dto.TransactionForms.ExpenseForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Expense;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.ExpenseRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.ExpenseService;
import com.businessbuddy.util.DateRange;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/** Operating expenses (Table 4.47). */
@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenses;
    private final ExpenseRepository expenseRepo;
    private final CatalogService catalog;
    private final BusinessContext context;

    public ExpenseController(ExpenseService expenses, ExpenseRepository expenseRepo, CatalogService catalog,
                             BusinessContext context) {
        this.expenses = expenses;
        this.expenseRepo = expenseRepo;
        this.catalog = catalog;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Business business = context.currentBusiness();
        DateRange range = from == null && to == null ? DateRange.lastDays(business.zoneId(), 90)
                : DateRange.of(from, to, business.zoneId());
        model.addAttribute("page", expenseRepo.search(business.getId(), categoryId, range.from(), range.to(),
                PageRequest.of(Math.max(page, 0), 25)));
        model.addAttribute("total", expenseRepo.total(business.getId(), range.from(), range.to()));
        model.addAttribute("period", range);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", catalog.expenseCategories());
        return "expenses/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new ExpenseForm());
        return formPage(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Expense e = expenses.expense(id);
        ExpenseForm form = new ExpenseForm();
        form.setCategoryId(e.getCategory().getId());
        form.setDescription(e.getDescription());
        form.setAmount(e.getAmount());
        form.setExpenseDate(e.getExpenseDate());
        form.setSupplierId(e.getSupplier() == null ? null : e.getSupplier().getId());
        form.setPaymentMethod(e.getPaymentMethod());
        form.setReferenceNo(e.getReferenceNo());
        form.setNotes(e.getNotes());
        model.addAttribute("form", form);
        return formPage(model, id);
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") ExpenseForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                expenses.save(id, form);
                redirect.addFlashAttribute("successMessage", "Expense saved.");
                return "redirect:/expenses";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return formPage(model, id);
    }

    private String formPage(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("categories", catalog.expenseCategories());
        model.addAttribute("suppliers", catalog.activeSuppliers());
        model.addAttribute("methods", PaymentMethod.values());
        return "expenses/form";
    }
}
