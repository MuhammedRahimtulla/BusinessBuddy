package com.businessbuddy.controller;

import com.businessbuddy.dto.CatalogForms.IngredientForm;
import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.PurchaseItemRepository;
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

import java.time.Instant;

/** Ingredient list, detail (movements and price history) and form (Table 4.40). */
@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final CatalogService catalog;
    private final IngredientRepository ingredients;
    private final InventoryMovementRepository movements;
    private final PurchaseItemRepository purchaseItems;
    private final BusinessContext context;

    public IngredientController(CatalogService catalog, IngredientRepository ingredients,
                                InventoryMovementRepository movements, PurchaseItemRepository purchaseItems,
                                BusinessContext context) {
        this.catalog = catalog;
        this.ingredients = ingredients;
        this.movements = movements;
        this.purchaseItems = purchaseItems;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "false") boolean low,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", ingredients.search(context.currentBusinessId(), q.trim(), low,
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("q", q);
        model.addAttribute("low", low);
        return "ingredients/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Ingredient ingredient = catalog.ingredient(id);
        model.addAttribute("ingredient", ingredient);
        model.addAttribute("movements", movements.search(context.currentBusinessId(), id, null, null,
                Instant.EPOCH, Instant.now().plusSeconds(86400), PageRequest.of(0, 25)).getContent());
        if (context.currentUser().canSeeMoney()) {
            model.addAttribute("priceHistory", purchaseItems.priceHistory(id));
        }
        return "ingredients/detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new IngredientForm());
        return form(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Ingredient i = catalog.ingredient(id);
        IngredientForm form = new IngredientForm();
        form.setName(i.getName());
        form.setCategoryId(i.getCategory() == null ? null : i.getCategory().getId());
        form.setBaseUnitCode(i.getBaseUnit().getCode());
        form.setMinStockLevel(i.getMinStockLevel());
        form.setReorderQuantity(i.getReorderQuantity());
        form.setPreferredSupplierId(i.getPreferredSupplier() == null ? null : i.getPreferredSupplier().getId());
        form.setExpiryTracked(i.isExpiryTracked());
        form.setDescription(i.getDescription());
        form.setActive(i.isActive());
        model.addAttribute("form", form);
        return form(model, id);
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") IngredientForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Ingredient saved = catalog.saveIngredient(id, form);
                redirect.addFlashAttribute("successMessage", "Ingredient saved.");
                return "redirect:/ingredients/" + saved.getId();
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return form(model, id);
    }

    private String form(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("categories", catalog.categories(CategoryType.INGREDIENT));
        model.addAttribute("suppliers", catalog.activeSuppliers());
        model.addAttribute("units", catalog.units());
        return "ingredients/form";
    }
}
