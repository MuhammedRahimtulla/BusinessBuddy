package com.businessbuddy;

import com.businessbuddy.dto.AccountForms.RegistrationForm;
import com.businessbuddy.dto.AccountForms.StaffForm;
import com.businessbuddy.dto.CatalogForms.IngredientForm;
import com.businessbuddy.dto.CatalogForms.ProductForm;
import com.businessbuddy.dto.CatalogForms.RecipeForm;
import com.businessbuddy.dto.CatalogForms.RecipeLineForm;
import com.businessbuddy.dto.CatalogForms.SupplierForm;
import com.businessbuddy.dto.TransactionForms.PurchaseForm;
import com.businessbuddy.dto.TransactionForms.PurchaseLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.UserRepository;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.BusinessService;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.PurchaseService;
import com.businessbuddy.service.RecipeService;
import com.businessbuddy.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for integration tests. Each test registers its own business in the embedded
 * PostgreSQL database, so tests never share records and cross-business isolation can be
 * tested with two real businesses. Services run with real transactions, row locks and
 * database constraints; nothing is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Test-Oven-2026!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected BusinessService businessService;
    @Autowired protected UserService userService;
    @Autowired protected CatalogService catalog;
    @Autowired protected RecipeService recipes;
    @Autowired protected PurchaseService purchases;
    @Autowired protected UserRepository users;
    @Autowired protected BusinessRepository businesses;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    /** Registers a new business with a unique Owner and logs that Owner in. */
    protected User newBusinessOwner(String name) {
        int n = SEQ.incrementAndGet();
        RegistrationForm f = new RegistrationForm();
        f.setBusinessName(name + " " + n);
        f.setCurrencyCode("ZAR");
        f.setTimeZone("Africa/Johannesburg");
        f.setFullName("Owner " + n);
        f.setEmail("owner" + n + "-" + System.nanoTime() + "@it.test");
        f.setPassword(PASSWORD);
        f.setConfirmPassword(PASSWORD);
        User owner = businessService.register(f);
        loginAs(owner);
        return owner;
    }

    protected User addStaff(Role role) {
        StaffForm f = new StaffForm();
        int n = SEQ.incrementAndGet();
        f.setFullName(role + " " + n);
        f.setEmail(role.name().toLowerCase() + n + "-" + System.nanoTime() + "@it.test");
        f.setRole(role);
        f.setPassword(PASSWORD);
        return userService.createStaff(f);
    }

    protected void loginAs(User user) {
        User fresh = users.findById(user.getId()).orElseThrow();
        CurrentUser principal = new CurrentUser(fresh, Instant.now());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    protected Business business(User owner) {
        return businesses.findById(owner.owningBusinessId()).orElseThrow();
    }

    protected void setTaxRate(User owner, String rate) {
        Business b = business(owner);
        b.setDefaultTaxRate(new BigDecimal(rate));
        businesses.save(b);
    }

    protected Long supplier(String name) {
        SupplierForm f = new SupplierForm();
        f.setName(name);
        return catalog.saveSupplier(null, f).getId();
    }

    protected Long ingredient(String name, String unit, String min) {
        IngredientForm f = new IngredientForm();
        f.setName(name);
        f.setBaseUnitCode(unit);
        f.setMinStockLevel(new BigDecimal(min));
        return catalog.saveIngredient(null, f).getId();
    }

    protected Long product(String name, ProductType type, String price) {
        ProductForm f = new ProductForm();
        f.setName(name);
        f.setType(type);
        f.setSellingPrice(new BigDecimal(price));
        return catalog.saveProduct(null, f).getId();
    }

    protected Long recipe(Long productId, String output, List<RecipeLineForm> lines) {
        RecipeForm f = new RecipeForm();
        f.setName("Recipe");
        f.setBatchOutputQty(new BigDecimal(output));
        f.setLines(new java.util.ArrayList<>(lines));
        return recipes.createVersion(productId, f).getId();
    }

    protected static RecipeLineForm recipeLine(Long ingredientId, String qty, String unit) {
        RecipeLineForm l = new RecipeLineForm();
        l.setIngredientId(ingredientId);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitCode(unit);
        return l;
    }

    protected static PurchaseLineForm purchaseLine(String key, String qty, String unit, String cost) {
        PurchaseLineForm l = new PurchaseLineForm();
        l.setItemKey(key);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitCode(unit);
        l.setUnitCost(new BigDecimal(cost));
        return l;
    }

    /** Creates and receives a purchase; returns its id. */
    protected Long receivedPurchase(Long supplierId, String additionalCosts, PurchaseLineForm... lines) {
        PurchaseForm f = new PurchaseForm();
        f.setSupplierId(supplierId);
        f.setReferenceNo("INV-" + SEQ.incrementAndGet());
        f.setPurchaseDate(LocalDate.now().minusDays(1));
        f.setAdditionalCosts(new BigDecimal(additionalCosts));
        f.setLines(new java.util.ArrayList<>(List.of(lines)));
        Long id = purchases.saveDraft(null, f).getId();
        purchases.receive(id, LocalDate.now().minusDays(1));
        return id;
    }
}
