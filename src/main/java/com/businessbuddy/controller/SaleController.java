package com.businessbuddy.controller;

import com.businessbuddy.dto.TransactionForms.SaleForm;
import com.businessbuddy.dto.TransactionForms.SaleLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Enums.SaleStatus;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.entity.SaleItem;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.SaleRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.SalesService;
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

/** Sales (Figure 4.15, Table 4.45): new sale, history, receipt view, confirm, void and delete draft. */
@Controller
@RequestMapping("/sales")
public class SaleController {

    private static final int BLANK_LINES = 4;

    private final SalesService sales;
    private final SaleRepository saleRepo;
    private final CatalogService catalog;
    private final BusinessContext context;

    public SaleController(SalesService sales, SaleRepository saleRepo, CatalogService catalog, BusinessContext context) {
        this.sales = sales;
        this.saleRepo = saleRepo;
        this.catalog = catalog;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(required = false) SaleStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Business business = context.currentBusiness();
        CurrentUser user = context.currentUser();
        DateRange range = from == null && to == null ? DateRange.lastDays(business.zoneId(), 30)
                : DateRange.of(from, to, business.zoneId());
        Long onlyMine = user.getRole() == Role.EMPLOYEE ? user.getUserId() : null;
        model.addAttribute("page", saleRepo.search(business.getId(), status, onlyMine,
                range.startInstant(business.zoneId()), range.endInstantExclusive(business.zoneId()),
                PageRequest.of(Math.max(page, 0), 25)));
        model.addAttribute("period", range);
        model.addAttribute("status", status);
        model.addAttribute("statuses", SaleStatus.values());
        return "sales/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        return formPage(new SaleForm(), null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Sale sale = sales.sale(id);
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft sale can be changed");
        }
        SaleForm form = new SaleForm();
        form.setCustomerId(sale.getCustomer() == null ? null : sale.getCustomer().getId());
        form.setNotes(sale.getNotes());
        form.setPaymentMethod(sale.getPaymentMethod());
        for (SaleItem item : sale.getItems()) {
            form.getLines().add(new SaleLineForm(item.getProduct().getId(), item.getQuantity(), item.getUnitPrice(),
                    item.getDiscountAmount()));
        }
        return formPage(form, id, model);
    }

    /** Saves a draft; with action=confirm it is confirmed straight away. */
    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @RequestParam(defaultValue = "draft") String action,
                       @Valid @ModelAttribute("form") SaleForm form, BindingResult result, Model model,
                       RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            Sale sale = null;
            try {
                sale = sales.saveDraft(id, form);
                if ("confirm".equals(action)) {
                    sales.confirm(sale.getId(), form.getPaymentMethod());
                    redirect.addFlashAttribute("successMessage", "Sale " + sale.getSaleNumber() + " confirmed.");
                } else {
                    redirect.addFlashAttribute("successMessage", "Sale saved as a draft.");
                }
                return "redirect:/sales/" + sale.getId();
            } catch (BusinessRuleException e) {
                if (sale != null) {
                    redirect.addFlashAttribute("errorMessage", e.getMessage() + " The sale was kept as a draft.");
                    return "redirect:/sales/" + sale.getId();
                }
                result.reject("rule", e.getMessage());
            }
        }
        return formPage(form, id, model);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("sale", sales.sale(id));
        model.addAttribute("methods", PaymentMethod.values());
        return "sales/detail";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam(required = false) PaymentMethod paymentMethod,
                          RedirectAttributes redirect) {
        sales.confirm(id, paymentMethod);
        redirect.addFlashAttribute("successMessage", "Sale confirmed.");
        return "redirect:/sales/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidSale(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirect) {
        sales.voidSale(id, reason);
        redirect.addFlashAttribute("successMessage", "Sale voided and stock returned.");
        return "redirect:/sales/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        sales.deleteDraft(id);
        redirect.addFlashAttribute("successMessage", "Draft sale deleted.");
        return "redirect:/sales";
    }

    private String formPage(SaleForm form, Long id, Model model) {
        long blanks = form.getLines().stream().filter(SaleLineForm::isBlank).count();
        for (long i = blanks; i < BLANK_LINES; i++) {
            form.getLines().add(new SaleLineForm());
        }
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        model.addAttribute("products", catalog.activeProducts());
        model.addAttribute("customers", catalog.customers());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("taxRate", context.currentBusiness().getDefaultTaxRate());
        return "sales/form";
    }
}
