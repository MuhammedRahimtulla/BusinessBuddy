package com.businessbuddy.controller;

import com.businessbuddy.dto.CatalogForms.ProductForm;
import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Product;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.RecipeService;
import com.businessbuddy.service.pricing.PricingService;
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

/** Products: list, detail with recipe versions and current cost, and form. */
@Controller
@RequestMapping("/products")
public class ProductController {

    private final CatalogService catalog;
    private final RecipeService recipes;
    private final PricingService pricing;
    private final ProductRepository products;
    private final BusinessContext context;

    public ProductController(CatalogService catalog, RecipeService recipes, PricingService pricing,
                             ProductRepository products, BusinessContext context) {
        this.catalog = catalog;
        this.recipes = recipes;
        this.pricing = pricing;
        this.products = products;
        this.context = context;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", products.search(context.currentBusinessId(), q.trim(),
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("q", q);
        return "products/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product product = catalog.product(id);
        model.addAttribute("product", product);
        if (context.currentUser().canSeeMoney()) {
            model.addAttribute("versions", recipes.versions(id));
            recipes.currentCost(product).ifPresent(cost -> {
                model.addAttribute("cost", cost);
                model.addAttribute("analysis", pricing.analyse(cost.unitCost(), product.getSellingPrice(),
                        context.currentBusiness().getDefaultTaxRate()));
            });
            if (!product.isManufactured()) {
                model.addAttribute("analysis", pricing.analyse(product.getAverageUnitCost(), product.getSellingPrice(),
                        context.currentBusiness().getDefaultTaxRate()));
            }
        }
        return "products/detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new ProductForm());
        return form(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Product p = catalog.product(id);
        ProductForm form = new ProductForm();
        form.setName(p.getName());
        form.setSku(p.getSku());
        form.setCategoryId(p.getCategory() == null ? null : p.getCategory().getId());
        form.setType(p.getType());
        form.setSaleUnitCode(p.getSaleUnit().getCode());
        form.setSellingPrice(p.getSellingPrice());
        form.setMinStockLevel(p.getMinStockLevel());
        form.setDescription(p.getDescription());
        form.setActive(p.isActive());
        model.addAttribute("form", form);
        return form(model, id);
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") ProductForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Product saved = catalog.saveProduct(id, form);
                redirect.addFlashAttribute("successMessage", "Product saved.");
                return "redirect:/products/" + saved.getId();
            } catch (BusinessRuleException e) {
                result.reject("rule", e.getMessage());
            }
        }
        return form(model, id);
    }

    private String form(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("categories", catalog.categories(CategoryType.PRODUCT));
        model.addAttribute("units", catalog.units());
        model.addAttribute("types", ProductType.values());
        return "products/form";
    }
}
