package com.businessbuddy.service;

import com.businessbuddy.dto.AccountForms.RegistrationForm;
import com.businessbuddy.dto.AccountForms.StaffForm;
import com.businessbuddy.dto.CatalogForms.CategoryForm;
import com.businessbuddy.dto.CatalogForms.CustomerForm;
import com.businessbuddy.dto.CatalogForms.IngredientForm;
import com.businessbuddy.dto.CatalogForms.ProductForm;
import com.businessbuddy.dto.CatalogForms.RecipeForm;
import com.businessbuddy.dto.CatalogForms.RecipeLineForm;
import com.businessbuddy.dto.CatalogForms.SupplierForm;
import com.businessbuddy.dto.TransactionForms.ExpenseForm;
import com.businessbuddy.dto.TransactionForms.ProductionForm;
import com.businessbuddy.dto.TransactionForms.PurchaseForm;
import com.businessbuddy.dto.TransactionForms.PurchaseLineForm;
import com.businessbuddy.dto.TransactionForms.SaleForm;
import com.businessbuddy.dto.TransactionForms.SaleLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.ExpenseCategoryRepository;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.UserRepository;
import com.businessbuddy.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates the demonstration business "Sunrise Bakery (sample data)" (decision D11).
 *
 * <p>The data is <strong>generated</strong>, not real: it is produced from a fixed random seed
 * with a weekly pattern and is flagged with business.is_demo_data, which shows a "Sample data"
 * banner on every page. It exists so the system, the reports and the forecasting can be
 * demonstrated. Every record is created through the normal services, so the ledger, cost
 * snapshots and audit log are built by the same rules as real use.</p>
 */
@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    public static final String OWNER_EMAIL = "owner@sunrise-bakery.test";
    public static final String MANAGER_EMAIL = "manager@sunrise-bakery.test";
    public static final String EMPLOYEE_EMAIL = "employee@sunrise-bakery.test";
    private static final java.util.Set<String> DAIRY_ITEMS = java.util.Set.of("Eggs", "Butter", "Milk", "Blueberries");

    private final BusinessService businessService;
    private final UserService userService;
    private final CatalogService catalog;
    private final RecipeService recipes;
    private final PurchaseService purchases;
    private final ProductionService production;
    private final SalesService sales;
    private final ExpenseService expenses;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final IngredientRepository ingredientRepo;
    private final ProductRepository productRepo;
    private final ExpenseCategoryRepository expenseCategories;
    private final Clock clock;

    public DemoDataService(BusinessService businessService, UserService userService, CatalogService catalog,
                           RecipeService recipes, PurchaseService purchases, ProductionService production,
                           SalesService sales, ExpenseService expenses, BusinessRepository businesses,
                           UserRepository users, IngredientRepository ingredientRepo, ProductRepository productRepo,
                           ExpenseCategoryRepository expenseCategories, Clock clock) {
        this.businessService = businessService;
        this.userService = userService;
        this.catalog = catalog;
        this.recipes = recipes;
        this.purchases = purchases;
        this.production = production;
        this.sales = sales;
        this.expenses = expenses;
        this.businesses = businesses;
        this.users = users;
        this.ingredientRepo = ingredientRepo;
        this.productRepo = productRepo;
        this.expenseCategories = expenseCategories;
        this.clock = clock;
    }

    public boolean exists() {
        return businesses.findFirstByDemoDataTrue().isPresent();
    }

    /** Creates the sample business with {@code days} days of history ending yesterday. */
    public Business seed(String password, int days) {
        RegistrationForm reg = new RegistrationForm();
        reg.setBusinessName("Sunrise Bakery (sample data)");
        reg.setCurrencyCode("ZAR");
        reg.setTimeZone("Africa/Johannesburg");
        reg.setFullName("Sample Owner");
        reg.setEmail(OWNER_EMAIL);
        reg.setPassword(password);
        reg.setConfirmPassword(password);
        User owner = businessService.register(reg);
        Business business = businesses.findById(owner.owningBusinessId()).orElseThrow();
        business.setDemoData(true);
        business.setDefaultTaxRate(new BigDecimal("0.1500"));
        business.setDefaultTargetMargin(new BigDecimal("0.4500"));
        business.setPhone("+27 21 000 0000");
        businesses.save(business);

        runAs(owner.getId(), () -> build(business, password, days));
        log.info("Sample business created with {} days of generated history", days);
        return business;
    }

    private void build(Business business, String password, int days) {
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = today.minusDays(days);
        Random random = new Random(20261026L);

        staff("Sample Manager", MANAGER_EMAIL, Role.MANAGER, password);
        staff("Sample Employee", EMPLOYEE_EMAIL, Role.EMPLOYEE, password);

        Long ingCat = category("Baking ingredients", CategoryType.INGREDIENT);
        Long packCat = category("Packaging", CategoryType.INGREDIENT);
        Long cakeCat = category("Cakes and muffins", CategoryType.PRODUCT);
        Long breadCat = category("Bread", CategoryType.PRODUCT);
        Long drinkCat = category("Drinks", CategoryType.PRODUCT);

        Long mill = supplier("Cape Mills Wholesale", "Thandi Mokoena");
        Long dairy = supplier("Valley Dairy Co-op", "Pieter van Wyk");
        Long drinks = supplier("Coastal Beverages", "Aisha Patel");

        Map<String, Long> ing = new LinkedHashMap<>();
        ing.put("Cake flour", ingredient("Cake flour", "g", ingCat, mill, "5000", false));
        ing.put("Bread flour", ingredient("Bread flour", "g", ingCat, mill, "10000", false));
        ing.put("Sugar", ingredient("Sugar", "g", ingCat, mill, "3000", false));
        ing.put("Eggs", ingredient("Eggs", "unit", ingCat, dairy, "36", false));
        ing.put("Butter", ingredient("Butter", "g", ingCat, dairy, "2000", true));
        ing.put("Milk", ingredient("Milk", "ml", ingCat, dairy, "3000", true));
        ing.put("Dark chocolate", ingredient("Dark chocolate", "g", ingCat, mill, "1500", false));
        ing.put("Yeast", ingredient("Yeast", "g", ingCat, mill, "200", false));
        ing.put("Blueberries", ingredient("Blueberries", "g", ingCat, dairy, "800", true));
        ing.put("Cake box", ingredient("Cake box", "unit", packCat, mill, "50", false));

        Long cake = product("Chocolate cake slice", "CAK-001", ProductType.MANUFACTURED, cakeCat, "38.00", "10");
        Long bread = product("White bread loaf", "BRD-001", ProductType.MANUFACTURED, breadCat, "24.00", "8");
        Long muffin = product("Blueberry muffin", "MUF-001", ProductType.MANUFACTURED, cakeCat, "22.00", "10");
        Long water = product("Still water 500 ml", "DRK-001", ProductType.RESALE, drinkCat, "15.00", "24");
        Long juice = product("Orange juice 330 ml", "DRK-002", ProductType.RESALE, drinkCat, "22.00", "12");

        recipe(cake, "Chocolate cake", "10", "18.00", "6.00", "4.00", "3.00", "10", List.of(
                line(ing.get("Cake flour"), "500", "g", "2"), line(ing.get("Sugar"), "300", "g", "0"),
                line(ing.get("Eggs"), "4", "unit", "0"), line(ing.get("Butter"), "250", "g", "0"),
                line(ing.get("Dark chocolate"), "200", "g", "5"), line(ing.get("Cake box"), "10", "unit", "0")));
        recipe(bread, "White bread", "10", "25.00", "4.00", "8.00", "5.00", "5", List.of(
                line(ing.get("Bread flour"), "5", "kg", "1"), line(ing.get("Yeast"), "60", "g", "0"),
                line(ing.get("Sugar"), "100", "g", "0"), line(ing.get("Butter"), "150", "g", "0"),
                line(ing.get("Milk"), "1", "l", "0")));
        recipe(muffin, "Blueberry muffins", "12", "12.00", "3.00", "3.00", "2.00", "10", List.of(
                line(ing.get("Cake flour"), "400", "g", "2"), line(ing.get("Sugar"), "200", "g", "0"),
                line(ing.get("Eggs"), "3", "unit", "0"), line(ing.get("Butter"), "150", "g", "0"),
                line(ing.get("Milk"), "250", "ml", "0"), line(ing.get("Blueberries"), "250", "g", "3")));

        List<Long> customers = new ArrayList<>();
        for (String name : List.of("Harbour Café", "St Mary's School tuck shop", "Office Park Canteen")) {
            CustomerForm cf = new CustomerForm();
            cf.setName(name);
            customers.add(catalog.saveCustomer(null, cf).getId());
        }

        // Ingredient prices per stock-unit purchase; butter and chocolate rise in the last month.
        Map<String, String[]> buy = new LinkedHashMap<>();
        //            quantity, unit, unit cost
        buy.put("Cake flour", new String[]{"25", "kg", "21.50"});
        buy.put("Bread flour", new String[]{"75", "kg", "18.90"});
        buy.put("Sugar", new String[]{"12", "kg", "24.00"});
        buy.put("Eggs", new String[]{"10", "dozen", "42.00"});
        buy.put("Butter", new String[]{"8", "kg", "115.00"});
        buy.put("Milk", new String[]{"12", "l", "19.50"});
        buy.put("Dark chocolate", new String[]{"5", "kg", "160.00"});
        buy.put("Yeast", new String[]{"1", "kg", "95.00"});
        buy.put("Blueberries", new String[]{"3", "kg", "120.00"});
        buy.put("Cake box", new String[]{"150", "unit", "3.20"});

        int purchaseNo = 1;
        for (LocalDate day = start; day.isBefore(today); day = day.plusDays(1)) {
            boolean lastMonth = day.isAfter(today.minusDays(31));
            if (day.equals(start) || day.getDayOfWeek() == DayOfWeek.MONDAY) {
                purchaseIngredients(mill, dairy, ing, buy, day, lastMonth, purchaseNo++);
                purchaseResale(drinks, water, juice, day, purchaseNo++);
            }
            if (day.getDayOfMonth() == 1 || day.equals(start)) {
                monthlyExpenses(business.getId(), day);
            }
            // Expected demand: weekly pattern, slow upward trend, seeded noise.
            double trend = 1.0 + 0.15 * (double) java.time.temporal.ChronoUnit.DAYS.between(start, day) / days;
            double weekend = switch (day.getDayOfWeek()) {
                case SATURDAY -> 1.8;
                case FRIDAY -> 1.35;
                case SUNDAY -> 0.6;
                default -> 1.0;
            };
            int cakeDemand = demand(random, 14 * weekend * trend);
            int breadDemand = demand(random, 16 * (day.getDayOfWeek() == DayOfWeek.SUNDAY ? 0.5 : 1.0) * trend);
            int muffinDemand = demand(random, 12 * weekend * trend);

            produce(cake, cakeDemand, "10", day);
            produce(bread, breadDemand, "10", day);
            produce(muffin, muffinDemand, "12", day);

            Instant morning = day.atTime(9, 15).atZone(zone).toInstant();
            Instant afternoon = day.atTime(14, 40).atZone(zone).toInstant();
            int waterDemand = demand(random, 9 * weekend);
            int juiceDemand = demand(random, 5 * weekend);
            sell(List.of(new long[]{cake, cakeDemand / 2}, new long[]{bread, breadDemand / 2},
                    new long[]{muffin, muffinDemand / 2}, new long[]{water, waterDemand / 2}), morning,
                    PaymentMethod.CARD, null);
            sell(List.of(new long[]{cake, cakeDemand - cakeDemand / 2}, new long[]{bread, breadDemand - breadDemand / 2},
                    new long[]{muffin, muffinDemand - muffinDemand / 2}, new long[]{water, waterDemand - waterDemand / 2},
                    new long[]{juice, juiceDemand}), afternoon, PaymentMethod.CASH,
                    random.nextInt(4) == 0 ? customers.get(random.nextInt(customers.size())) : null);
        }
    }

    private static int demand(Random random, double mean) {
        return (int) Math.max(0, Math.round(mean + random.nextGaussian() * Math.sqrt(mean)));
    }

    private void staff(String name, String email, Role role, String password) {
        StaffForm form = new StaffForm();
        form.setFullName(name);
        form.setEmail(email);
        form.setRole(role);
        form.setPassword(password);
        userService.createStaff(form);
    }

    private Long category(String name, CategoryType type) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setType(type);
        return catalog.createCategory(form).getId();
    }

    private Long supplier(String name, String contact) {
        SupplierForm form = new SupplierForm();
        form.setName(name);
        form.setContactPerson(contact);
        return catalog.saveSupplier(null, form).getId();
    }

    private Long ingredient(String name, String unit, Long categoryId, Long supplierId, String min, boolean expiry) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setBaseUnitCode(unit);
        form.setCategoryId(categoryId);
        form.setPreferredSupplierId(supplierId);
        form.setMinStockLevel(new BigDecimal(min));
        form.setExpiryTracked(expiry);
        return catalog.saveIngredient(null, form).getId();
    }

    private Long product(String name, String sku, ProductType type, Long categoryId, String price, String min) {
        ProductForm form = new ProductForm();
        form.setName(name);
        form.setSku(sku);
        form.setType(type);
        form.setCategoryId(categoryId);
        form.setSellingPrice(new BigDecimal(price));
        form.setMinStockLevel(new BigDecimal(min));
        return catalog.saveProduct(null, form).getId();
    }

    private static RecipeLineForm line(Long ingredientId, String qty, String unit, String waste) {
        RecipeLineForm l = new RecipeLineForm();
        l.setIngredientId(ingredientId);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitCode(unit);
        l.setWastePercent(new BigDecimal(waste));
        return l;
    }

    private void recipe(Long productId, String name, String output, String labour, String packaging, String utilities,
                        String overheadFixed, String overheadPercent, List<RecipeLineForm> lines) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setBatchOutputQty(new BigDecimal(output));
        form.setOutputUnitCode("unit");
        form.setLabourCost(new BigDecimal(labour));
        form.setPackagingCost(new BigDecimal(packaging));
        form.setUtilitiesCost(new BigDecimal(utilities));
        form.setOverheadFixed(new BigDecimal(overheadFixed));
        form.setOverheadPercent(new BigDecimal(overheadPercent));
        form.setEffectiveFrom(LocalDate.now(clock).minusYears(1));
        form.setLines(new ArrayList<>(lines));
        recipes.createVersion(productId, form);
    }

    /** Weekly ingredient order, topped up to a par level of about one week and a half of use. */
    private void purchaseIngredients(Long mill, Long dairy, Map<String, Long> ing, Map<String, String[]> buy,
                                     LocalDate day, boolean lastMonth, int number) {
        PurchaseForm millOrder = purchaseForm(mill, "INV-M" + number, day);
        PurchaseForm dairyOrder = purchaseForm(dairy, "INV-D" + number, day);
        for (Map.Entry<String, String[]> e : buy.entrySet()) {
            Ingredient current = ingredientRepo.findById(ing.get(e.getKey())).orElseThrow();
            BigDecimal par = catalog.unit(e.getValue()[1]).getFactorToBase()
                    .multiply(new BigDecimal(e.getValue()[0]));
            if (current.getCurrentQuantity().compareTo(par.multiply(new BigDecimal("0.6"))) > 0) {
                continue;                                             // enough stock this week
            }
            BigDecimal cost = new BigDecimal(e.getValue()[2]);
            if (lastMonth && (e.getKey().equals("Butter") || e.getKey().equals("Dark chocolate"))) {
                cost = cost.multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
            }
            PurchaseLineForm l = new PurchaseLineForm();
            l.setItemKey("I:" + current.getId());
            l.setQuantity(new BigDecimal(e.getValue()[0]));
            l.setUnitCode(e.getValue()[1]);
            l.setUnitCost(cost);
            if (current.isExpiryTracked()) {
                l.setExpiryDate(day.plusDays(e.getKey().equals("Milk") ? 10 : 21));
            }
            boolean isDairy = DAIRY_ITEMS.contains(e.getKey());
            (isDairy ? dairyOrder : millOrder).getLines().add(l);
        }
        for (PurchaseForm form : List.of(millOrder, dairyOrder)) {
            if (!form.getLines().isEmpty()) {
                form.setAdditionalCosts(new BigDecimal("120.00"));     // delivery charge, shared by value
                Purchase p = purchases.saveDraft(null, form);
                purchases.receive(p.getId(), day);
            }
        }
    }

    private void purchaseResale(Long drinks, Long water, Long juice, LocalDate day, int number) {
        PurchaseForm form = purchaseForm(drinks, "CB-" + number, day);
        Product w = productRepo.findById(water).orElseThrow();
        Product j = productRepo.findById(juice).orElseThrow();
        if (w.getCurrentQuantity().compareTo(new BigDecimal("60")) < 0) {
            form.getLines().add(resaleLine(water, "96", "6.50"));
        }
        if (j.getCurrentQuantity().compareTo(new BigDecimal("40")) < 0) {
            form.getLines().add(resaleLine(juice, "60", "11.20"));
        }
        if (!form.getLines().isEmpty()) {
            Purchase p = purchases.saveDraft(null, form);
            purchases.receive(p.getId(), day);
        }
    }

    private static PurchaseLineForm resaleLine(Long productId, String qty, String cost) {
        PurchaseLineForm l = new PurchaseLineForm();
        l.setItemKey("P:" + productId);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitCode("unit");
        l.setUnitCost(new BigDecimal(cost));
        return l;
    }

    private static PurchaseForm purchaseForm(Long supplierId, String reference, LocalDate day) {
        PurchaseForm form = new PurchaseForm();
        form.setSupplierId(supplierId);
        form.setReferenceNo(reference);
        form.setPurchaseDate(day);
        return form;
    }

    private void produce(Long productId, int demand, String batchSize, LocalDate day) {
        Product p = productRepo.findById(productId).orElseThrow();
        BigDecimal needed = BigDecimal.valueOf(demand).subtract(p.getCurrentQuantity()).max(BigDecimal.ZERO);
        if (needed.signum() == 0) {
            return;
        }
        BigDecimal size = new BigDecimal(batchSize);
        BigDecimal planned = needed.divide(size, 0, RoundingMode.CEILING).multiply(size);
        ProductionForm form = new ProductionForm();
        form.setProductId(productId);
        form.setPlannedOutput(planned);
        form.setProductionDate(day);
        try {
            Long id = production.createDraft(form).getId();
            production.confirm(id, planned, null, null);
        } catch (BusinessRuleException e) {
            log.debug("Sample production skipped on {}: {}", day, e.getMessage());   // not enough ingredients
        }
    }

    private void sell(List<long[]> wanted, Instant when, PaymentMethod method, Long customerId) {
        SaleForm form = new SaleForm();
        form.setPaymentMethod(method);
        form.setCustomerId(customerId);
        for (long[] w : wanted) {
            Product p = productRepo.findById(w[0]).orElseThrow();
            long available = p.getCurrentQuantity().setScale(0, RoundingMode.FLOOR).longValue();
            long qty = Math.min(w[1], available);
            if (qty > 0) {
                form.getLines().add(new SaleLineForm(w[0], BigDecimal.valueOf(qty), null, BigDecimal.ZERO));
            }
        }
        if (!form.getLines().isEmpty()) {
            Sale sale = sales.saveDraft(null, form, when);
            sales.confirm(sale.getId(), method);
        }
    }

    private void monthlyExpenses(Long businessId, LocalDate day) {
        Map<String, String> monthly = new LinkedHashMap<>();
        // Sized for a small bakery; baking labour is already in the recipes, so salaries
        // here are only the remaining staff costs (spec 8.15, no double counting).
        monthly.put("Rent", "6000.00");
        monthly.put("Electricity", "1800.00");
        monthly.put("Internet", "650.00");
        monthly.put("Salaries", "8500.00");
        monthly.put("Marketing", "500.00");
        expenseCategories.findByBusinessIdOrderByName(businessId).forEach(c -> {
            String amount = monthly.get(c.getName());
            if (amount == null) {
                return;
            }
            ExpenseForm form = new ExpenseForm();
            form.setCategoryId(c.getId());
            form.setDescription(c.getName() + " " + day.getMonth().name().charAt(0)
                    + day.getMonth().name().substring(1).toLowerCase() + " " + day.getYear());
            form.setAmount(new BigDecimal(amount));
            form.setExpenseDate(day);
            form.setPaymentMethod(PaymentMethod.EFT);
            expenses.save(null, form);
        });
    }

    /** Runs code as the given user, so the services apply the same rules as for a logged-in user. */
    private void runAs(Long userId, Runnable work) {
        User user = users.findById(userId).orElseThrow();
        CurrentUser principal = new CurrentUser(user, Instant.now(clock));
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            work.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
