package com.businessbuddy.controller;

import com.businessbuddy.dto.CatalogForms.RecipeForm;
import com.businessbuddy.dto.CatalogForms.RecipeLineForm;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.RecipeVersion;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.RecipeService;
import com.businessbuddy.service.pricing.PricingService;
import jakarta.validation.Valid;
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

/**
 * Recipe versions and the recipe costing screen (Figure 4.14, Table 4.43).
 * "Calculate cost" re-renders the form with the breakdown from CostCalculationService, so
 * the cost shown is always the server calculation and never a browser-side estimate.
 */
@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private static final int BLANK_LINES = 3;

    private final RecipeService recipes;
    private final CatalogService catalog;
    private final PricingService pricing;
    private final BusinessContext context;

    public RecipeController(RecipeService recipes, CatalogService catalog, PricingService pricing,
                            BusinessContext context) {
        this.recipes = recipes;
        this.catalog = catalog;
        this.pricing = pricing;
        this.context = context;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        RecipeVersion v = recipes.version(id);
        model.addAttribute("version", v);
        model.addAttribute("cost", recipes.cost(id));
        return "recipes/view";
    }

    /** New version for a product, optionally starting from an existing version. */
    @GetMapping("/new")
    public String create(@RequestParam Long productId, @RequestParam(required = false) Long from, Model model) {
        RecipeForm form = from == null ? new RecipeForm() : recipes.formFrom(recipes.version(from));
        if (from == null) {
            form.setName(catalog.product(productId).getName());
        }
        return formPage(productId, form, model);
    }

    @PostMapping("/new")
    public String save(@RequestParam Long productId, @RequestParam(defaultValue = "save") String action,
                       @Valid @ModelAttribute("form") RecipeForm form, BindingResult result, Model model,
                       RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                if ("preview".equals(action)) {
                    model.addAttribute("preview", recipes.previewCost(productId, form));
                } else {
                    RecipeVersion v = recipes.createVersion(productId, form);
                    redirect.addFlashAttribute("successMessage", "Recipe version " + v.getVersionNo() + " saved.");
                    return "redirect:/recipes/" + v.getId();
                }
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return formPage(productId, form, model);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        recipes.activate(id);
        redirect.addFlashAttribute("successMessage", "Recipe version activated.");
        return "redirect:/recipes/" + id;
    }

    private String formPage(Long productId, RecipeForm form, Model model) {
        Product product = catalog.product(productId);
        long blanks = form.getLines().stream().filter(RecipeLineForm::isBlank).count();
        for (long i = blanks; i < BLANK_LINES; i++) {
            form.getLines().add(new RecipeLineForm());
        }
        model.addAttribute("form", form);
        model.addAttribute("product", product);
        model.addAttribute("ingredients", catalog.activeIngredients());
        model.addAttribute("units", catalog.units());
        Object preview = model.getAttribute("preview");
        if (preview instanceof com.businessbuddy.service.costing.CostBreakdown cost) {
            model.addAttribute("analysis", pricing.analyse(cost.unitCost(), product.getSellingPrice(),
                    context.currentBusiness().getDefaultTaxRate()));
        }
        return "recipes/form";
    }
}
