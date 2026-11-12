package com.businessbuddy.service.insights;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Forecast;
import com.businessbuddy.repository.ForecastRepository;
import com.businessbuddy.repository.PurchaseItemRepository;
import com.businessbuddy.repository.SaleItemRepository;
import com.businessbuddy.service.reporting.ReportService;
import com.businessbuddy.service.reporting.ReportService.Profitability;
import com.businessbuddy.service.reporting.ReportService.ProductRow;
import com.businessbuddy.util.DateRange;
import com.businessbuddy.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based business insights (spec 9.6, pseudocode 4.6.14).
 *
 * <p>Insights come from fixed rules and text templates, not from a language model. Every
 * number in an insight is read from stored data, so the text cannot invent values. Each
 * insight states its period, the supporting figure and whether it is CALCULATED (from
 * recorded data) or PREDICTED (from a forecast).</p>
 */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    public enum Nature { CALCULATED, PREDICTED }

    public record Insight(String text, String period, String supportingFigure, Nature nature, int priority) {
    }

    private final ReportService reports;
    private final PurchaseItemRepository purchaseItems;
    private final ForecastRepository forecasts;
    private final SaleItemRepository saleItems;
    private final BigDecimal costRiseThreshold;
    private final BigDecimal demandChangeThreshold;
    private final int stockoutWarningDays;

    public InsightsService(ReportService reports, PurchaseItemRepository purchaseItems, ForecastRepository forecasts,
                           SaleItemRepository saleItems,
                           @Value("${businessbuddy.insights.cost-rise-threshold}") BigDecimal costRiseThreshold,
                           @Value("${businessbuddy.insights.demand-change-threshold}") BigDecimal demandChangeThreshold,
                           @Value("${businessbuddy.forecast.stockout-warning-days}") int stockoutWarningDays) {
        this.reports = reports;
        this.purchaseItems = purchaseItems;
        this.forecasts = forecasts;
        this.saleItems = saleItems;
        this.costRiseThreshold = costRiseThreshold;
        this.demandChangeThreshold = demandChangeThreshold;
        this.stockoutWarningDays = stockoutWarningDays;
    }

    public List<Insight> generate(Business business, DateRange period) {
        List<Insight> insights = new ArrayList<>();
        String cur = business.getCurrencyCode() + " ";
        Profitability current = reports.profitability(business, period);
        BigDecimal target = business.getDefaultTargetMargin();

        // Rule 1: gross margin below the business target
        for (ProductRow p : current.byProduct()) {
            if (p.grossMargin() != null && target.signum() > 0 && p.grossMargin().compareTo(target) < 0) {
                insights.add(new Insight(p.name() + " earned a gross margin of " + pct(p.grossMargin())
                        + ", below your target of " + pct(target) + ".", period.toString(),
                        "Gross profit " + cur + Money.money(p.grossProfit()) + " on revenue " + cur
                                + Money.money(p.revenue()), Nature.CALCULATED, 2));
            }
        }

        // Rule 2: high sales volume but low gross profit (top 20% by units, bottom 20% by profit)
        List<ProductRow> rows = current.byProduct();
        if (rows.size() >= 3) {
            int cut = Math.max(1, (int) Math.ceil(rows.size() * 0.2));
            List<ProductRow> byUnits = rows.stream().sorted(Comparator.comparing(ProductRow::units).reversed()).toList();
            List<ProductRow> byProfit = rows.stream().sorted(Comparator.comparing(ProductRow::grossProfit)).toList();
            for (ProductRow p : byUnits.subList(0, cut)) {
                if (byProfit.subList(0, cut).contains(p)) {
                    insights.add(new Insight(p.name() + " sold " + p.units().stripTrailingZeros().toPlainString()
                            + " units but earned only " + cur + Money.money(p.grossProfit()) + " gross profit.",
                            period.toString(), "Top " + cut + " by units, bottom " + cut + " by gross profit",
                            Nature.CALCULATED, 2));
                }
            }
        }

        // Rule 3: ingredient purchase price rose compared with the previous period
        DateRange previous = period.previous();
        Map<Long, Object[]> before = new HashMap<>();
        for (Object[] r : purchaseItems.averageLandedCostByIngredient(business.getId(), previous.from(), previous.to())) {
            before.put((Long) r[0], r);
        }
        for (Object[] r : purchaseItems.averageLandedCostByIngredient(business.getId(), period.from(), period.to())) {
            Object[] old = before.get((Long) r[0]);
            if (old == null) {
                continue;
            }
            BigDecimal now = (BigDecimal) r[2];
            BigDecimal then = (BigDecimal) old[2];
            if (then.signum() > 0 && now.compareTo(then.multiply(BigDecimal.ONE.add(costRiseThreshold))) > 0) {
                insights.add(new Insight("The average purchase price of " + r[1] + " rose from " + cur
                        + Money.unitCost(then) + " to " + cur + Money.unitCost(now) + " per base unit.",
                        period + " compared with " + previous, "Increase of "
                        + pct(Money.divide(now.subtract(then), then)), Nature.CALCULATED, 1));
            }
        }

        // Rule 4: predicted stockout within the warning window
        for (Forecast f : forecasts.currentStockout(business.getId())) {
            if (f.getPredictedValue() != null
                    && f.getPredictedValue().compareTo(BigDecimal.valueOf(stockoutWarningDays)) <= 0) {
                insights.add(new Insight(f.getIngredient().getName() + " may run out "
                        + runOutWords(f.getPredictedValue()) + " (estimate).",
                        "Usage up to " + f.getTrainingCutoff(), "Model " + f.getModelName(), Nature.PREDICTED, 0));
            }
        }

        // Rule 5: next week's forecast differs strongly from last week's actual sales
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(zone);
        Map<Long, List<Forecast>> byProduct = new LinkedHashMap<>();
        for (Forecast f : forecasts.currentDemand(business.getId())) {
            if (f.getPredictedValue() != null) {
                byProduct.computeIfAbsent(f.getProduct().getId(), k -> new ArrayList<>()).add(f);
            }
        }
        for (List<Forecast> list : byProduct.values()) {
            BigDecimal expected = list.stream().map(Forecast::getPredictedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal recent = saleItems.unitsSold(list.get(0).getProduct().getId(),
                    today.minusDays(7).atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant());
            if (recent.signum() > 0) {
                BigDecimal change = Money.divide(expected.subtract(recent).abs(), recent);
                if (change.compareTo(demandChangeThreshold) > 0) {
                    insights.add(new Insight("Demand for " + list.get(0).getProduct().getName() + " is forecast at "
                            + expected.setScale(0, RoundingMode.HALF_UP) + " units next week, compared with "
                            + recent.stripTrailingZeros().toPlainString() + " last week (estimate).",
                            "Next " + list.size() + " days", "Model " + list.get(0).getModelName(),
                            Nature.PREDICTED, 1));
                }
            }
        }
        insights.sort(Comparator.comparingInt(Insight::priority));
        return insights;
    }

    /** "within a day", "in about 1 day" or "in about 5 days" for an estimated number of days. */
    public static String runOutWords(BigDecimal days) {
        if (days.compareTo(BigDecimal.ONE) < 0) {
            return "within a day";
        }
        int whole = days.setScale(0, RoundingMode.HALF_UP).intValue();
        return whole == 1 ? "in about 1 day" : "in about " + whole + " days";
    }

    private static String pct(BigDecimal rate) {
        return rate.multiply(Money.HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
