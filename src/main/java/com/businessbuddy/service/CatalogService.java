package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.CatalogForms.CategoryForm;
import com.businessbuddy.dto.CatalogForms.CustomerForm;
import com.businessbuddy.dto.CatalogForms.IngredientForm;
import com.businessbuddy.dto.CatalogForms.ProductForm;
import com.businessbuddy.dto.CatalogForms.SupplierForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Category;
import com.businessbuddy.entity.Customer;
import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.ExpenseCategory;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Supplier;
import com.businessbuddy.entity.UnitOfMeasure;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.CategoryRepository;
import com.businessbuddy.repository.CustomerRepository;
import com.businessbuddy.repository.ExpenseCategoryRepository;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.SupplierRepository;
import com.businessbuddy.repository.UnitOfMeasureRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.util.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Master data of a business: categories, suppliers, ingredients, products and customers
 * (spec 8.3 to 8.8). Every lookup is scoped to the current business, and every reference
 * chosen in a form (category, supplier) is checked to belong to that business.
 */
@Service
@Transactional
public class CatalogService {

    private final CategoryRepository categories;
    private final ExpenseCategoryRepository expenseCategories;
    private final SupplierRepository suppliers;
    private final IngredientRepository ingredients;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final UnitOfMeasureRepository units;
    private final InventoryMovementRepository movements;
    private final BusinessContext context;
    private final AuditService audit;

    public CatalogService(CategoryRepository categories, ExpenseCategoryRepository expenseCategories,
                          SupplierRepository suppliers, IngredientRepository ingredients, ProductRepository products,
                          CustomerRepository customers, UnitOfMeasureRepository units,
                          InventoryMovementRepository movements, BusinessContext context, AuditService audit) {
        this.categories = categories;
        this.expenseCategories = expenseCategories;
        this.suppliers = suppliers;
        this.ingredients = ingredients;
        this.products = products;
        this.customers = customers;
        this.units = units;
        this.movements = movements;
        this.context = context;
        this.audit = audit;
    }

    // --- Units ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UnitOfMeasure> units() {
        return units.findAllByOrderByDimensionAscFactorToBaseAsc();
    }

    @Transactional(readOnly = true)
    public UnitOfMeasure unit(String code) {
        return units.findById(code == null ? "" : code).orElseThrow(() -> new BusinessRuleException("Choose a valid unit"));
    }

    // --- Categories ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Category> categories(CategoryType type) {
        Long businessId = context.currentBusinessId();
        return type == null ? categories.findByBusinessIdOrderByTypeAscNameAsc(businessId)
                : categories.findByBusinessIdAndTypeOrderByName(businessId, type);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Category createCategory(CategoryForm form) {
        Long businessId = context.currentBusinessId();
        String name = form.getName().trim();
        if (categories.existsByBusinessIdAndTypeAndNameIgnoreCase(businessId, form.getType(), name)) {
            throw new BusinessRuleException("A category with this name already exists");
        }
        Category c = new Category();
        c.setBusiness(context.currentBusiness());
        c.setName(name);
        c.setType(form.getType());
        return categories.save(c);
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategory> expenseCategories() {
        return expenseCategories.findByBusinessIdOrderByName(context.currentBusinessId());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ExpenseCategory createExpenseCategory(String name) {
        Long businessId = context.currentBusinessId();
        if (name == null || name.isBlank() || name.trim().length() > 60) {
            throw new BusinessRuleException("Enter a name of up to 60 characters");
        }
        if (expenseCategories.existsByBusinessIdAndNameIgnoreCase(businessId, name.trim())) {
            throw new BusinessRuleException("An expense category with this name already exists");
        }
        ExpenseCategory c = new ExpenseCategory();
        c.setBusiness(context.currentBusiness());
        c.setName(name.trim());
        return expenseCategories.save(c);
    }

    private Category ownedCategory(Long id, CategoryType expected) {
        if (id == null) {
            return null;
        }
        Category c = categories.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Category"));
        if (c.getType() != expected) {
            throw new BusinessRuleException("Choose a " + expected.name().toLowerCase() + " category");
        }
        return c;
    }

    // --- Suppliers ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Supplier supplier(Long id) {
        return suppliers.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Supplier"));
    }

    @Transactional(readOnly = true)
    public List<Supplier> activeSuppliers() {
        return suppliers.findByBusinessIdAndActiveTrueOrderByName(context.currentBusinessId());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Supplier saveSupplier(Long id, SupplierForm form) {
        Long businessId = context.currentBusinessId();
        String name = form.getName().trim();
        boolean duplicate = id == null ? suppliers.existsByBusinessIdAndNameIgnoreCase(businessId, name)
                : suppliers.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, id);
        if (duplicate) {
            throw new BusinessRuleException("A supplier with this name already exists");
        }
        Supplier s = id == null ? new Supplier() : supplier(id);
        if (id == null) {
            s.setBusiness(context.currentBusiness());
        }
        s.setName(name);
        s.setContactPerson(BusinessService.blankToNull(form.getContactPerson()));
        s.setEmail(BusinessService.blankToNull(form.getEmail()));
        s.setPhone(BusinessService.blankToNull(form.getPhone()));
        s.setAddress(BusinessService.blankToNull(form.getAddress()));
        s.setNotes(BusinessService.blankToNull(form.getNotes()));
        s.setActive(form.isActive());
        suppliers.save(s);
        audit.record(id == null ? "SUPPLIER_CREATED" : "SUPPLIER_UPDATED", "SUPPLIER", s.getId());
        return s;
    }

    // --- Ingredients --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Ingredient ingredient(Long id) {
        return ingredients.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Ingredient"));
    }

    @Transactional(readOnly = true)
    public List<Ingredient> activeIngredients() {
        return ingredients.findByBusinessIdAndActiveTrueOrderByName(context.currentBusinessId());
    }

    /** Saves an ingredient (pseudocode SaveIngredient, 4.6.3). Opening stock is entered later as an adjustment. */
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Ingredient saveIngredient(Long id, IngredientForm form) {
        Long businessId = context.currentBusinessId();
        String name = form.getName().trim();
        boolean duplicate = id == null ? ingredients.existsByBusinessIdAndNameIgnoreCase(businessId, name)
                : ingredients.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, id);
        if (duplicate) {
            throw new BusinessRuleException("An ingredient with this name already exists");
        }
        Ingredient i = id == null ? new Ingredient() : ingredient(id);
        UnitOfMeasure baseUnit = unit(form.getBaseUnitCode());
        if (id != null && !i.getBaseUnit().equals(baseUnit) && movements.existsByIngredientId(id)) {
            throw new BusinessRuleException("The base unit cannot change once stock movements exist");
        }
        if (id == null) {
            i.setBusiness(context.currentBusiness());
        }
        i.setName(name);
        i.setCategory(ownedCategory(form.getCategoryId(), CategoryType.INGREDIENT));
        i.setBaseUnit(baseUnit);
        i.setMinStockLevel(Money.quantity(form.getMinStockLevel()));
        i.setReorderQuantity(form.getReorderQuantity());
        i.setPreferredSupplier(form.getPreferredSupplierId() == null ? null : supplier(form.getPreferredSupplierId()));
        i.setExpiryTracked(form.isExpiryTracked());
        i.setDescription(BusinessService.blankToNull(form.getDescription()));
        i.setActive(form.isActive());
        ingredients.save(i);
        audit.record(id == null ? "INGREDIENT_CREATED" : "INGREDIENT_SAVED", "INGREDIENT", i.getId());
        return i;
    }

    // --- Products -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Product product(Long id) {
        return products.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Product"));
    }

    @Transactional(readOnly = true)
    public List<Product> activeProducts() {
        return products.findByBusinessIdAndActiveTrueOrderByName(context.currentBusinessId());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Product saveProduct(Long id, ProductForm form) {
        Long businessId = context.currentBusinessId();
        String name = form.getName().trim();
        String sku = BusinessService.blankToNull(form.getSku());
        boolean duplicateName = id == null ? products.existsByBusinessIdAndNameIgnoreCase(businessId, name)
                : products.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, id);
        if (duplicateName) {
            throw new BusinessRuleException("A product with this name already exists");
        }
        if (sku != null && (id == null ? products.existsByBusinessIdAndSkuIgnoreCase(businessId, sku)
                : products.existsByBusinessIdAndSkuIgnoreCaseAndIdNot(businessId, sku, id))) {
            throw new BusinessRuleException("Another product already uses this SKU");
        }
        Product p = id == null ? new Product() : product(id);
        UnitOfMeasure saleUnit = unit(form.getSaleUnitCode());
        if (id != null && !p.getSaleUnit().equals(saleUnit) && movements.existsByProductId(id)) {
            throw new BusinessRuleException("The unit of sale cannot change once stock movements exist");
        }
        if (id == null) {
            p.setBusiness(context.currentBusiness());
        }
        boolean priceChanged = id != null && p.getSellingPrice().compareTo(form.getSellingPrice()) != 0;
        String oldPrice = id == null ? null : p.getSellingPrice().toPlainString();
        p.setName(name);
        p.setSku(sku);
        p.setCategory(ownedCategory(form.getCategoryId(), CategoryType.PRODUCT));
        p.setType(form.getType());
        p.setSaleUnit(saleUnit);
        p.setSellingPrice(Money.money(form.getSellingPrice()));
        p.setMinStockLevel(Money.quantity(form.getMinStockLevel()));
        p.setDescription(BusinessService.blankToNull(form.getDescription()));
        p.setActive(form.isActive());
        products.save(p);
        audit.record(id == null ? "PRODUCT_CREATED" : "PRODUCT_SAVED", "PRODUCT", p.getId());
        if (priceChanged) {
            audit.record("PRICE_CHANGED", "PRODUCT", p.getId(),
                    Map.of("from", oldPrice, "to", p.getSellingPrice().toPlainString()));
        }
        return p;
    }

    /** Applies a price from the pricing calculator (Table 4.48). Audited as a price change. */
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public void applySellingPrice(Long productId, java.math.BigDecimal price) {
        Product p = product(productId);
        String old = p.getSellingPrice().toPlainString();
        p.setSellingPrice(Money.money(price));
        audit.record("PRICE_CHANGED", "PRODUCT", p.getId(), Map.of("from", old, "to", p.getSellingPrice().toPlainString()));
    }

    // --- Customers ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Customer customer(Long id) {
        return customers.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Customer"));
    }

    @Transactional(readOnly = true)
    public List<Customer> customers() {
        return customers.findByBusinessIdOrderByName(context.currentBusinessId());
    }

    public Customer saveCustomer(Long id, CustomerForm form) {
        Customer c = id == null ? new Customer() : customer(id);
        if (id == null) {
            Business business = context.currentBusiness();
            c.setBusiness(business);
        }
        c.setName(form.getName().trim());
        c.setEmail(BusinessService.blankToNull(form.getEmail()));
        c.setPhone(BusinessService.blankToNull(form.getPhone()));
        c.setNotes(BusinessService.blankToNull(form.getNotes()));
        return customers.save(c);
    }
}
