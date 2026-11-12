package com.businessbuddy.service.reporting;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.entity.StockItem;
import com.businessbuddy.repository.ExpenseRepository;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.PurchaseRepository;
import com.businessbuddy.repository.SaleItemRepository;
import com.businessbuddy.repository.SaleRepository;
import com.businessbuddy.service.RecipeService;
import com.businessbuddy.service.costing.CostBreakdown;
import com.businessbuddy.util.CsvWriter;
import com.businessbuddy.util.DateRange;
import com.businessbuddy.util.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business reports (spec 8.16 to 8.18, pseudocode ProfitabilityReport in 4.6.11).
 *
 * <p>Definitions used throughout:
 * <ul>
 *   <li>Revenue = sum of net line amounts of confirmed sales (after discounts, excluding tax).</li>
 *   <li>Cost of goods sold = sum of line cost snapshots stored at confirmation.</li>
 *   <li>Gross profit = revenue - cost of goods sold; gross margin = gross profit / revenue.</li>
 *   <li>Net operating result = gross profit - operating expenses of the period.</li>
 * </ul>
 * Voided and draft sales are excluded. Dates are interpreted in the business time zone.
 * Every method takes the business explicitly; controllers pass the current user's business.</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SaleItemRepository saleItems;
    private final SaleRepository sales;
    private final ExpenseRepository expenses;
    private final PurchaseRepository purchases;
    private final IngredientRepository ingredients;
    private final ProductRepository products;
    private final RecipeService recipes;
    private final AuditService audit;

    public ReportService(SaleItemRepository saleItems, SaleRepository sales, ExpenseRepository expenses,
                         PurchaseRepository purchases, IngredientRepository ingredients, ProductRepository products,
                         RecipeService recipes, AuditService audit) {
        this.saleItems = saleItems;
        this.sales = sales;
        this.expenses = expenses;
        this.purchases = purchases;
        this.ingredients = ingredients;
        this.products = products;
        this.recipes = recipes;
        this.audit = audit;
    }

    public record ProductRow(Long productId, String name, BigDecimal units, BigDecimal revenue, BigDecimal cogs,
                             BigDecimal grossProfit, BigDecimal grossMargin) {
    }

    public record Profitability(DateRange period, BigDecimal revenue, BigDecimal discounts, BigDecimal taxCollected,
                                BigDecimal cogs, BigDecimal grossProfit, BigDecimal grossMargin,
                                BigDecimal operatingExpenses, BigDecimal netOperatingResult, List<ProductRow> byProduct) {
    }

    public Profitability profitability(Business business, DateRange period) {
        ZoneId zone = business.zoneId();
        List<ProductRow> rows = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        for (Object[] r : saleItems.productTotals(business.getId(), period.startInstant(zone),
                period.endInstantExclusive(zone))) {
            BigDecimal units = (BigDecimal) r[2];
            BigDecimal rev = (BigDecimal) r[3];
            BigDecimal lineCogs = Money.money(Money.nz((BigDecimal) r[6]));
            BigDecimal gp = rev.subtract(lineCogs);
            rows.add(new ProductRow((Long) r[0], (String) r[1], units, rev, lineCogs, gp, margin(gp, rev)));
            revenue = revenue.add(rev);
            discounts = discounts.add((BigDecimal) r[4]);
            tax = tax.add((BigDecimal) r[5]);
            cogs = cogs.add(lineCogs);
        }
        rows.sort(Comparator.comparing(ProductRow::grossProfit).reversed());
        BigDecimal grossProfit = revenue.subtract(cogs);
        BigDecimal opex = expenses.total(business.getId(), period.from(), period.to());
        return new Profitability(period, revenue, discounts, tax, cogs, grossProfit, margin(grossProfit, revenue),
                opex, grossProfit.subtract(opex), rows);
    }

    /** Gross margin as a decimal, or null when revenue is zero ("not defined", 4.6.11). */
    static BigDecimal margin(BigDecimal profit, BigDecimal revenue) {
        return revenue.signum() == 0 ? null : Money.divide(profit, revenue).setScale(4, Money.ROUNDING);
    }

    public record StockRow(String type, Long id, String name, String category, BigDecimal quantity, String unit,
                           BigDecimal minimum, BigDecimal averageCost, BigDecimal value, boolean belowMinimum) {
    }

    public List<StockRow> inventoryBalance(Long businessId) {
        List<StockRow> rows = new ArrayList<>();
        for (Ingredient i : ingredients.findByBusinessIdOrderByName(businessId)) {
            rows.add(stockRow(i, i.getCategory() == null ? "" : i.getCategory().getName()));
        }
        for (Product p : products.findByBusinessIdOrderByName(businessId)) {
            rows.add(stockRow(p, p.getCategory() == null ? "" : p.getCategory().getName()));
        }
        return rows;
    }

    private static StockRow stockRow(StockItem item, String category) {
        return new StockRow(item.stockItemType(), item.getId(), item.getName(), category, item.getCurrentQuantity(),
                item.getStockUnit().getCode(), item.getMinStockLevel(), item.getAverageUnitCost(), item.stockValue(),
                item.isActive() && item.isBelowMinimum());
    }

    public record PaymentRow(PaymentMethod method, long count, BigDecimal revenue) {
    }

    public List<PaymentRow> salesByPayment(Business business, DateRange period) {
        ZoneId zone = business.zoneId();
        List<PaymentRow> rows = new ArrayList<>();
        for (Object[] r : sales.byPaymentMethod(business.getId(), period.startInstant(zone),
                period.endInstantExclusive(zone))) {
            rows.add(new PaymentRow((PaymentMethod) r[0], (Long) r[1], (BigDecimal) r[2]));
        }
        return rows;
    }

    public List<Sale> voidedSales(Business business, DateRange period) {
        ZoneId zone = business.zoneId();
        return sales.findVoided(business.getId(), period.startInstant(zone), period.endInstantExclusive(zone));
    }

    public record CategoryTotal(String category, BigDecimal total) {
    }

    public List<CategoryTotal> expensesByCategory(Long businessId, DateRange period) {
        return expenses.totalsByCategory(businessId, period.from(), period.to()).stream()
                .map(r -> new CategoryTotal((String) r[0], (BigDecimal) r[1])).toList();
    }

    public record MonthRow(String month, BigDecimal revenue, BigDecimal cogs, BigDecimal grossProfit,
                           BigDecimal expenses, BigDecimal netOperatingResult) {
    }

    /** Monthly revenue, COGS and expenses for the last {@code months} months including this one. */
    public List<MonthRow> monthlyTrend(Business business, int months) {
        ZoneId zone = business.zoneId();
        YearMonth current = YearMonth.now(zone);
        YearMonth first = current.minusMonths(months - 1L);
        LocalDate from = first.atDay(1);
        LocalDate to = current.atEndOfMonth();
        DateRange range = new DateRange(from, to);
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            map.put(m.toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Object[] r : saleItems.monthlyRevenue(business.getId(), business.getTimeZone(),
                range.startInstant(zone), range.endInstantExclusive(zone))) {
            BigDecimal[] v = map.get((String) r[0]);
            if (v != null) {
                v[0] = (BigDecimal) r[1];
                v[1] = Money.money((BigDecimal) r[2]);
            }
        }
        for (Object[] r : expenses.monthlyTotals(business.getId(), from, to)) {
            BigDecimal[] v = map.get((String) r[0]);
            if (v != null) {
                v[2] = (BigDecimal) r[1];
            }
        }
        List<MonthRow> rows = new ArrayList<>();
        map.forEach((month, v) -> {
            BigDecimal gp = v[0].subtract(v[1]);
            rows.add(new MonthRow(month, v[0], v[1], gp, v[2], gp.subtract(v[2])));
        });
        return rows;
    }

    public List<Purchase> purchaseHistory(Long businessId, DateRange period) {
        return purchases.findReceivedBetween(businessId, period.from(), period.to());
    }

    public record CostingRow(Product product, BigDecimal unitCost, String costSource, BigDecimal price,
                             BigDecimal margin, BigDecimal markup, boolean belowCost) {
    }

    /** Current estimated unit cost against the selling price of every active product. */
    public List<CostingRow> productCosting(Long businessId) {
        List<CostingRow> rows = new ArrayList<>();
        for (Product p : products.findByBusinessIdAndActiveTrueOrderByName(businessId)) {
            Optional<CostBreakdown> recipeCost = recipes.currentCost(p);
            BigDecimal unitCost = recipeCost.map(CostBreakdown::unitCost).orElse(p.getAverageUnitCost());
            String source = recipeCost.isPresent() ? "Active recipe" : "Average purchase cost";
            BigDecimal price = p.getSellingPrice();
            BigDecimal profit = price.subtract(unitCost);
            rows.add(new CostingRow(p, unitCost, source, price, margin(profit, price),
                    unitCost.signum() == 0 ? null : Money.divide(profit, unitCost).setScale(4, Money.ROUNDING),
                    price.compareTo(unitCost) < 0));
        }
        return rows;
    }

    /** Builds a CSV export. Owners and Managers only; every export is audited (4.8.6). */
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional                                    // writes the REPORT_EXPORTED audit entry
    public String exportCsv(Business business, String type, DateRange period) {
        CsvWriter csv = new CsvWriter();
        switch (type) {
            case "profitability" -> {
                Profitability p = profitability(business, period);
                csv.row("BusinessBuddy profitability report", period.toString());
                csv.row("Revenue (excl. tax)", p.revenue());
                csv.row("Discounts", p.discounts());
                csv.row("Tax collected", p.taxCollected());
                csv.row("Cost of goods sold", p.cogs());
                csv.row("Gross profit", p.grossProfit());
                csv.row("Operating expenses", p.operatingExpenses());
                csv.row("Net operating result", p.netOperatingResult());
                csv.row("");
                csv.row("Product", "Units", "Revenue", "COGS", "Gross profit", "Gross margin");
                for (ProductRow r : p.byProduct()) {
                    csv.row(r.name(), r.units(), r.revenue(), r.cogs(), r.grossProfit(), r.grossMargin());
                }
            }
            case "inventory" -> {
                csv.row("Type", "Item", "Category", "Quantity", "Unit", "Minimum", "Average cost", "Value", "Below minimum");
                for (StockRow r : inventoryBalance(business.getId())) {
                    csv.row(r.type(), r.name(), r.category(), r.quantity(), r.unit(), r.minimum(), r.averageCost(),
                            r.value(), r.belowMinimum() ? "Yes" : "No");
                }
            }
            case "expenses" -> {
                csv.row("Category", "Total", period.toString());
                for (CategoryTotal r : expensesByCategory(business.getId(), period)) {
                    csv.row(r.category(), r.total());
                }
            }
            case "sales" -> {
                csv.row("Payment method", "Sales", "Revenue (excl. tax)", period.toString());
                for (PaymentRow r : salesByPayment(business, period)) {
                    csv.row(r.method() == null ? "" : r.method().name(), r.count(), r.revenue());
                }
            }
            default -> throw new com.businessbuddy.exception.NotFoundException("Report");
        }
        audit.record("REPORT_EXPORTED", "REPORT", null, Map.of("type", type, "period", period.toString()));
        return csv.toString();
    }
}
