package com.businessbuddy.controller;

import com.businessbuddy.dto.TransactionForms.StockChangeForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.service.reporting.ReportService;
import com.businessbuddy.util.DateRange;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock levels, the movement ledger and manual stock changes (Table 4.46).
 * Employees see quantities and may record waste; adjustments and returns are for Owners and
 * Managers, enforced both by the URL rules (/inventory/adjust) and in InventoryService.
 */
@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final InventoryMovementRepository movements;
    private final ReportService reports;
    private final CatalogService catalog;
    private final BusinessContext context;

    public InventoryController(InventoryService inventory, InventoryMovementRepository movements,
                               ReportService reports, CatalogService catalog, BusinessContext context) {
        this.inventory = inventory;
        this.movements = movements;
        this.reports = reports;
        this.catalog = catalog;
        this.context = context;
    }

    @GetMapping
    public String stock(Model model) {
        model.addAttribute("rows", reports.inventoryBalance(context.currentBusinessId()));
        return "inventory/stock";
    }

    @GetMapping("/movements")
    public String movements(@RequestParam(required = false) MovementType type,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        Business business = context.currentBusiness();
        DateRange range = from == null && to == null ? DateRange.lastDays(business.zoneId(), 30)
                : DateRange.of(from, to, business.zoneId());
        model.addAttribute("page", movements.search(business.getId(), null, null, type,
                range.startInstant(business.zoneId()), range.endInstantExclusive(business.zoneId()),
                PageRequest.of(Math.max(page, 0), 30)));
        model.addAttribute("period", range);
        model.addAttribute("type", type);
        model.addAttribute("types", MovementType.values());
        return "inventory/movements";
    }

    @GetMapping({"/waste", "/adjust"})
    public String changeForm(@RequestParam(required = false) String item, Model model) {
        StockChangeForm form = new StockChangeForm();
        form.setItemKey(item);
        model.addAttribute("form", form);
        return changePage(model);
    }

    @PostMapping({"/waste", "/adjust"})
    public String change(@Valid @ModelAttribute("form") StockChangeForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                String key = form.getItemKey();
                String type = key.startsWith("P:") ? "PRODUCT" : "INGREDIENT";
                Long id = Long.parseLong(key.substring(2));
                inventory.recordManualChange(new InventoryService.ManualChange(type, id, form.getType(),
                        form.getQuantity(), form.getUnitCost(), form.getReason()));
                redirect.addFlashAttribute("successMessage", "Stock change recorded.");
                return "redirect:/inventory";
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                result.rejectValue("itemKey", "item", "Choose an item");
            }
        }
        return changePage(model);
    }

    private String changePage(Model model) {
        model.addAttribute("ingredients", catalog.activeIngredients());
        model.addAttribute("products", catalog.activeProducts());
        model.addAttribute("types", context.currentUser().canSeeMoney()
                ? List.of(MovementType.WASTE, MovementType.ADJUSTMENT, MovementType.RETURN)
                : List.of(MovementType.WASTE));
        return "inventory/change";
    }
}
