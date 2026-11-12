package com.businessbuddy.controller;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Product;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.RecipeService;
import com.businessbuddy.service.costing.CostBreakdown;
import com.businessbuddy.service.pricing.PriceAnalysis;
import com.businessbuddy.service.pricing.PricingService;
import com.businessbuddy.util.Money;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** The pricing calculator (Table 4.48): price by target margin, by markup, or analyse a price. */
@Controller
@RequestMapping("/pricing")
public class PricingController {

    private final PricingService pricing;
    private final CatalogService catalog;
    private final RecipeService recipes;
    private final BusinessContext context;

    public PricingController(PricingService pricing, CatalogService catalog, RecipeService recipes,
                             BusinessContext context) {
        this.pricing = pricing;
        this.catalog = catalog;
        this.recipes = recipes;
        this.context = context;
    }

    @GetMapping
    public String calculator(@RequestParam(required = false) Long productId,
                             @RequestParam(defaultValue = "MARGIN") PricingService.Method method,
                             @RequestParam(required = false) BigDecimal value,
                             @RequestParam(required = false) BigDecimal taxPercent, Model model) {
        Business business = context.currentBusiness();
        model.addAttribute("products", catalog.activeProducts());
        model.addAttribute("productId", productId);
        model.addAttribute("method", method);
        model.addAttribute("taxPercent", taxPercent == null ? Money.rateToPercent(business.getDefaultTaxRate()) : taxPercent);
        if (productId != null) {
            Product product = catalog.product(productId);
            BigDecimal unitCost = unitCost(product);
            model.addAttribute("product", product);
            model.addAttribute("unitCost", unitCost);
            BigDecimal shownValue = value != null ? value
                    : method == PricingService.Method.PRICE ? product.getSellingPrice()
                    : Money.rateToPercent(business.getDefaultTargetMargin());
            model.addAttribute("value", shownValue);
            try {
                BigDecimal rate = method == PricingService.Method.PRICE ? shownValue : Money.percentToRate(shownValue);
                PriceAnalysis analysis = pricing.suggest(unitCost, method, rate,
                        Money.percentToRate((BigDecimal) model.getAttribute("taxPercent")));
                model.addAttribute("analysis", analysis);
                model.addAttribute("current", pricing.analyse(unitCost, product.getSellingPrice(),
                        business.getDefaultTaxRate()));
            } catch (BusinessRuleException e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }
        return "pricing/calculator";
    }

    @PostMapping("/apply")
    public String apply(@RequestParam Long productId, @RequestParam BigDecimal price, RedirectAttributes redirect) {
        if (price.signum() < 0) {
            throw new BusinessRuleException("Price cannot be negative");
        }
        catalog.applySellingPrice(productId, price);
        redirect.addFlashAttribute("successMessage", "Selling price updated.");
        return "redirect:/products/" + productId;
    }

    private BigDecimal unitCost(Product product) {
        return recipes.currentCost(product).map(CostBreakdown::unitCost).orElse(product.getAverageUnitCost());
    }
}
