package com.businessbuddy.service.reporting;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.PurchaseStatus;
import com.businessbuddy.entity.Enums.SaleStatus;
import com.businessbuddy.entity.Forecast;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.PurchaseRepository;
import com.businessbuddy.repository.SaleRepository;
import com.businessbuddy.service.ForecastService;
import com.businessbuddy.service.insights.InsightsService;
import com.businessbuddy.service.insights.InsightsService.Insight;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.service.reporting.ReportService.MonthRow;
import com.businessbuddy.service.reporting.ReportService.ProductRow;
import com.businessbuddy.service.reporting.ReportService.Profitability;
import com.businessbuddy.util.DateRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers the dashboard indicators of section 8.17 and Figure 4.13. Each figure comes from a
 * documented report calculation; nothing on the dashboard is invented or hard-coded.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ReportService reports;
    private final InventoryService inventory;
    private final ForecastService forecasts;
    private final InsightsService insights;
    private final IngredientRepository ingredients;
    private final ProductRepository products;
    private final PurchaseRepository purchases;
    private final SaleRepository sales;

    public DashboardService(ReportService reports, InventoryService inventory, ForecastService forecasts,
                            InsightsService insights, IngredientRepository ingredients, ProductRepository products,
                            PurchaseRepository purchases, SaleRepository sales) {
        this.reports = reports;
        this.inventory = inventory;
        this.forecasts = forecasts;
        this.insights = insights;
        this.ingredients = ingredients;
        this.products = products;
        this.purchases = purchases;
        this.sales = sales;
    }

    public record ForecastSummary(Product product, BigDecimal nextWeekUnits, String modelName, String cutoff) {
    }

    public record Dashboard(Profitability profitability, Map<String, BigDecimal> inventoryValue,
                            long activeProducts, long activeIngredients, List<Ingredient> lowIngredients,
                            List<Product> lowProducts, List<Purchase> recentPurchases, List<Sale> recentSales,
                            List<ProductRow> topSelling, List<ProductRow> topProfit, List<MonthRow> trend,
                            List<ForecastSummary> forecastSummary, List<Forecast> stockoutSoon,
                            List<Insight> insights) {
    }

    public Dashboard build(Business business, DateRange period, boolean includeMoney) {
        Long id = business.getId();
        Profitability p = includeMoney ? reports.profitability(business, period) : null;
        List<ProductRow> topSelling = p == null ? List.of() : p.byProduct().stream()
                .sorted(Comparator.comparing(ProductRow::units).reversed()).limit(5).toList();
        List<ProductRow> topProfit = p == null ? List.of() : p.byProduct().stream().limit(5).toList();

        Map<Long, ForecastSummary> summary = new LinkedHashMap<>();
        List<Forecast> demand = includeMoney ? forecasts.currentDemand(id) : List.of();
        for (Forecast f : demand) {
            if (f.getPredictedValue() == null) {
                continue;
            }
            summary.merge(f.getProduct().getId(),
                    new ForecastSummary(f.getProduct(), f.getPredictedValue(), f.getModelName(),
                            f.getTrainingCutoff().toString()),
                    (a, b) -> new ForecastSummary(a.product(), a.nextWeekUnits().add(b.nextWeekUnits()),
                            a.modelName(), a.cutoff()));
        }
        List<Forecast> stockout = forecasts.currentStockout(id).stream()
                .filter(f -> f.getPredictedValue() != null).limit(5).toList();

        return new Dashboard(p,
                includeMoney ? inventory.inventoryValue(id) : Map.of(),
                products.countByBusinessIdAndActiveTrue(id),
                ingredients.countByBusinessIdAndActiveTrue(id),
                ingredients.findBelowMinimum(id),
                products.findBelowMinimum(id),
                includeMoney ? purchases.findTop5ByBusinessIdAndStatusOrderByReceivedDateDescIdDesc(id,
                        PurchaseStatus.RECEIVED) : List.of(),
                sales.findTop5ByBusinessIdAndStatusOrderBySaleDateDesc(id, SaleStatus.CONFIRMED),
                topSelling, topProfit,
                includeMoney ? reports.monthlyTrend(business, 6) : List.of(),
                summary.values().stream().toList(), stockout,
                includeMoney ? insights.generate(business, period) : List.of());
    }
}
