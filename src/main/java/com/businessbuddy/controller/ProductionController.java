package com.businessbuddy.controller;

import com.businessbuddy.dto.TransactionForms.ConfirmBatchForm;
import com.businessbuddy.dto.TransactionForms.ProductionForm;
import com.businessbuddy.dto.TransactionForms.UsedLine;
import com.businessbuddy.entity.Enums.BatchStatus;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.ProductionBatch;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.ProductionBatchRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.ProductionService;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Production batches (Table 4.44): record a draft, then confirm it with the actual output. */
@Controller
@RequestMapping("/production")
public class ProductionController {

    private final ProductionService production;
    private final ProductionBatchRepository batches;
    private final ProductRepository products;
    private final BusinessContext context;

    public ProductionController(ProductionService production, ProductionBatchRepository batches,
                                ProductRepository products, BusinessContext context) {
        this.production = production;
        this.batches = batches;
        this.products = products;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(required = false) BatchStatus status, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", batches.search(context.currentBusinessId(), status,
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("status", status);
        model.addAttribute("statuses", BatchStatus.values());
        return "production/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new ProductionForm());
        return formPage(model);
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") ProductionForm form, BindingResult result, Model model,
                       RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ProductionBatch batch = production.createDraft(form);
                redirect.addFlashAttribute("successMessage", "Batch recorded. Confirm it when production is finished.");
                return "redirect:/production/" + batch.getId();
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return formPage(model);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductionBatch batch = production.batch(id);
        model.addAttribute("batch", batch);
        if (batch.getStatus() == BatchStatus.DRAFT) {
            var planned = production.plannedConsumption(batch);
            model.addAttribute("planned", planned);
            ConfirmBatchForm form = new ConfirmBatchForm();
            form.setActualOutput(batch.getPlannedOutput());
            planned.forEach(p -> form.getUsed().add(new UsedLine(p.ingredient().getId(), p.plannedQuantity())));
            model.addAttribute("confirmForm", form);
        }
        return "production/detail";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, @ModelAttribute("confirmForm") ConfirmBatchForm form,
                          RedirectAttributes redirect) {
        Map<Long, BigDecimal> used = new HashMap<>();
        for (UsedLine line : form.getUsed()) {
            if (line.getIngredientId() != null && line.getQuantity() != null) {
                used.put(line.getIngredientId(), line.getQuantity());
            }
        }
        production.confirm(id, form.getActualOutput(), used, form.getWasteNotes());
        redirect.addFlashAttribute("successMessage", "Batch confirmed. Ingredients consumed and finished stock added.");
        return "redirect:/production/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        production.cancel(id);
        redirect.addFlashAttribute("successMessage", "Batch cancelled.");
        return "redirect:/production/" + id;
    }

    private String formPage(Model model) {
        model.addAttribute("products", products.findByBusinessIdAndTypeAndActiveTrueOrderByName(
                context.currentBusinessId(), ProductType.MANUFACTURED));
        return "production/form";
    }
}
