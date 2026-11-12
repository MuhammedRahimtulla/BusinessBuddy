package com.businessbuddy.controller;

import com.businessbuddy.dto.TransactionForms.PurchaseForm;
import com.businessbuddy.dto.TransactionForms.PurchaseLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.PurchaseStatus;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.PurchaseItem;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.PurchaseRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.PurchaseService;
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

/** Purchases: list, draft form (Table 4.42), detail, receive and cancel. */
@Controller
@RequestMapping("/purchases")
public class PurchaseController {

    private static final int BLANK_LINES = 4;

    private final PurchaseService purchases;
    private final PurchaseRepository purchaseRepo;
    private final ProductRepository products;
    private final CatalogService catalog;
    private final BusinessContext context;

    public PurchaseController(PurchaseService purchases, PurchaseRepository purchaseRepo, ProductRepository products,
                              CatalogService catalog, BusinessContext context) {
        this.purchases = purchases;
        this.purchaseRepo = purchaseRepo;
        this.products = products;
        this.catalog = catalog;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(required = false) PurchaseStatus status,
                       @RequestParam(required = false) Long supplierId, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", purchaseRepo.search(context.currentBusinessId(), status, supplierId,
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("status", status);
        model.addAttribute("statuses", PurchaseStatus.values());
        return "purchases/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Business business = context.currentBusiness();
        model.addAttribute("purchase", purchases.purchase(id));
        model.addAttribute("today", LocalDate.now(business.zoneId()));
        return "purchases/detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        return formPage(new PurchaseForm(), null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Purchase p = purchases.purchase(id);
        if (p.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft purchase can be changed");
        }
        PurchaseForm form = new PurchaseForm();
        form.setSupplierId(p.getSupplier().getId());
        form.setReferenceNo(p.getReferenceNo());
        form.setPurchaseDate(p.getPurchaseDate());
        form.setDiscountAmount(p.getDiscountAmount());
        form.setAdditionalCosts(p.getAdditionalCosts());
        form.setNotes(p.getNotes());
        for (PurchaseItem item : p.getItems()) {
            PurchaseLineForm lf = new PurchaseLineForm();
            lf.setItemKey(item.getIngredient() != null ? "I:" + item.getIngredient().getId() : "P:" + item.getProduct().getId());
            lf.setQuantity(item.getQuantity());
            lf.setUnitCode(item.getUnit().getCode());
            lf.setUnitCost(item.getUnitCost());
            lf.setExpiryDate(item.getExpiryDate());
            form.getLines().add(lf);
        }
        return formPage(form, id, model);
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") PurchaseForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Purchase p = purchases.saveDraft(id, form);
                redirect.addFlashAttribute("successMessage", "Purchase saved as a draft. Receive it when the goods arrive.");
                return "redirect:/purchases/" + p.getId();
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return formPage(form, id, model);
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDate,
                          RedirectAttributes redirect) {
        purchases.receive(id, receivedDate);
        redirect.addFlashAttribute("successMessage", "Purchase received. Stock and average costs are updated.");
        return "redirect:/purchases/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirect) {
        purchases.cancel(id, reason);
        redirect.addFlashAttribute("successMessage", "Purchase cancelled.");
        return "redirect:/purchases/" + id;
    }

    private String formPage(PurchaseForm form, Long id, Model model) {
        long blanks = form.getLines().stream().filter(PurchaseLineForm::isBlank).count();
        for (long i = blanks; i < BLANK_LINES; i++) {
            form.getLines().add(new PurchaseLineForm());
        }
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        model.addAttribute("suppliers", catalog.activeSuppliers());
        model.addAttribute("ingredients", catalog.activeIngredients());
        model.addAttribute("resaleProducts", products.findByBusinessIdAndTypeAndActiveTrueOrderByName(
                context.currentBusinessId(), ProductType.RESALE));
        model.addAttribute("units", catalog.units());
        return "purchases/form";
    }
}
