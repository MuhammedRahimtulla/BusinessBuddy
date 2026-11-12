package com.businessbuddy.service;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.ForecastStatus;
import com.businessbuddy.entity.Enums.ForecastType;
import com.businessbuddy.entity.Enums.NotificationNature;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Forecast;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.ModelEvaluation;
import com.businessbuddy.entity.Product;
import com.businessbuddy.ml.DemandPoint;
import com.businessbuddy.ml.ForecastEvaluator;
import com.businessbuddy.ml.ForecastModel;
import com.businessbuddy.ml.MovingAverageForecaster;
import com.businessbuddy.ml.RegressionForecaster;
import com.businessbuddy.repository.ForecastRepository;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.ModelEvaluationRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.SaleItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demand forecasting and stockout estimates (spec 9.1, 9.2; pseudocode 4.6.12 and 4.6.13).
 *
 * <p>Every forecast is stored with its model, version and data cut-off, and is always shown
 * as an estimate. When there is not enough history the service stores INSUFFICIENT_DATA
 * instead of pretending to forecast. Methods take the business explicitly so the nightly
 * job can run them without a logged-in user.</p>
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    private final SaleItemRepository saleItems;
    private final ForecastRepository forecasts;
    private final ModelEvaluationRepository evaluations;
    private final ProductRepository products;
    private final IngredientRepository ingredients;
    private final InventoryMovementRepository movements;
    private final NotificationService notifications;
    private final Clock clock;
    private final int minHistoryDays;
    private final int testDays;
    private final int horizonDays;
    private final int movingAverageDays;
    private final int stockoutWindowDays;
    private final int stockoutMinDays;
    private final int stockoutWarningDays;

    public ForecastService(SaleItemRepository saleItems, ForecastRepository forecasts,
                           ModelEvaluationRepository evaluations, ProductRepository products,
                           IngredientRepository ingredients, InventoryMovementRepository movements,
                           NotificationService notifications, Clock clock,
                           @Value("${businessbuddy.forecast.min-history-days}") int minHistoryDays,
                           @Value("${businessbuddy.forecast.test-days}") int testDays,
                           @Value("${businessbuddy.forecast.horizon-days}") int horizonDays,
                           @Value("${businessbuddy.forecast.moving-average-days}") int movingAverageDays,
                           @Value("${businessbuddy.forecast.stockout-window-days}") int stockoutWindowDays,
                           @Value("${businessbuddy.forecast.stockout-min-days}") int stockoutMinDays,
                           @Value("${businessbuddy.forecast.stockout-warning-days}") int stockoutWarningDays) {
        this.saleItems = saleItems;
        this.forecasts = forecasts;
        this.evaluations = evaluations;
        this.products = products;
        this.ingredients = ingredients;
        this.movements = movements;
        this.notifications = notifications;
        this.clock = clock;
        this.minHistoryDays = minHistoryDays;
        this.testDays = testDays;
        this.horizonDays = horizonDays;
        this.movingAverageDays = movingAverageDays;
        this.stockoutWindowDays = stockoutWindowDays;
        this.stockoutMinDays = stockoutMinDays;
        this.stockoutWarningDays = stockoutWarningDays;
    }

    /** Outcome of one product's forecast run, for the screen and the tests. */
    public record DemandResult(Product product, boolean sufficientData, int historyDays, ModelEvaluation evaluation,
                               List<Forecast> forecasts) {
    }

    /** Runs demand forecasts for every active product and stockout estimates for every ingredient. */
    @Transactional
    public Map<String, Integer> runAll(Business business) {
        int demand = 0;
        int insufficient = 0;
        for (Product p : products.findByBusinessIdAndActiveTrueOrderByName(business.getId())) {
            DemandResult r = runDemandForecast(business, p);
            if (r.sufficientData()) {
                demand++;
            } else {
                insufficient++;
            }
        }
        int stockout = 0;
        for (Ingredient i : ingredients.findByBusinessIdAndActiveTrueOrderByName(business.getId())) {
            if (estimateStockout(business, i) != null) {
                stockout++;
            }
        }
        Map<String, Integer> summary = new HashMap<>();
        summary.put("demandForecasts", demand);
        summary.put("insufficientData", insufficient);
        summary.put("stockoutEstimates", stockout);
        return summary;
    }

    /** Gap-free daily units sold of a product up to and including {@code lastDay}. */
    @Transactional(readOnly = true)
    public List<DemandPoint> dailyHistory(Business business, Product product, LocalDate lastDay) {
        ZoneId zone = business.zoneId();
        Instant from = lastDay.minusDays(365).atStartOfDay(zone).toInstant();
        Map<LocalDate, Double> byDay = new HashMap<>();
        LocalDate first = null;
        for (Object[] row : saleItems.dailyUnits(business.getId(), product.getId(), business.getTimeZone(), from)) {
            LocalDate day = toLocalDate(row[0]);
            if (day.isAfter(lastDay)) {
                continue;
            }
            byDay.put(day, ((BigDecimal) row[1]).doubleValue());
            if (first == null || day.isBefore(first)) {
                first = day;
            }
        }
        List<DemandPoint> series = new ArrayList<>();
        if (first == null) {
            return series;
        }
        for (LocalDate d = first; !d.isAfter(lastDay); d = d.plusDays(1)) {
            series.add(new DemandPoint(d, byDay.getOrDefault(d, 0.0)));
        }
        return series;
    }

    @Transactional
    public DemandResult runDemandForecast(Business business, Product product) {
        LocalDate today = LocalDate.now(clock.withZone(business.zoneId()));
        LocalDate cutoff = today.minusDays(1);                       // today is not complete yet
        List<DemandPoint> history = dailyHistory(business, product, cutoff);
        fillActuals(product, history, today);
        forecasts.supersedeDemand(product.getId());

        if (history.size() < minHistoryDays) {
            Forecast f = baseForecast(business, ForecastType.DEMAND, cutoff, today, 1);
            f.setProduct(product);
            f.setModelName("NONE");
            f.setModelVersion("-");
            f.setValueUnit("units");
            f.setStatus(ForecastStatus.INSUFFICIENT_DATA);
            forecasts.save(f);
            return new DemandResult(product, false, history.size(), null, List.of(f));
        }

        ForecastModel baseline = new MovingAverageForecaster(movingAverageDays);
        ForecastModel candidate = new RegressionForecaster();
        ForecastEvaluator.Result result = ForecastEvaluator.evaluate(history, testDays, baseline, candidate);
        ModelEvaluation evaluation = saveEvaluation(business, product, candidate, result);

        ForecastModel selected = result.candidateSelected() ? candidate : baseline;
        selected.train(history);                                     // retrain on all data
        List<DemandPoint> known = new ArrayList<>(history);
        List<Forecast> created = new ArrayList<>();
        for (int h = 1; h <= horizonDays; h++) {
            LocalDate target = cutoff.plusDays(h);
            double predicted = Math.max(0.0, selected.predict(target, known));
            known.add(new DemandPoint(target, predicted));             // multi-step: feed predictions forward
            Forecast f = baseForecast(business, ForecastType.DEMAND, cutoff, target, h);
            f.setProduct(product);
            f.setModelEvaluation(evaluation);
            f.setModelName(selected.name());
            f.setModelVersion(selected.version());
            f.setValueUnit("units");
            f.setPredictedValue(BigDecimal.valueOf(predicted).setScale(3, RoundingMode.HALF_UP));
            f.setStatus(ForecastStatus.ACTIVE);
            created.add(forecasts.save(f));
        }
        log.debug("Demand forecast for {}: {} (MAE {} vs baseline {})", product.getName(), selected.name(),
                result.candidateMae(), result.baselineMae());
        return new DemandResult(product, true, history.size(), evaluation, created);
    }

    private ModelEvaluation saveEvaluation(Business business, Product product, ForecastModel candidate,
                                           ForecastEvaluator.Result r) {
        ModelEvaluation e = new ModelEvaluation();
        e.setBusiness(business);
        e.setProduct(product);
        e.setForecastType(ForecastType.DEMAND);
        e.setModelName(candidate.name());
        e.setModelVersion(candidate.version());
        e.setTrainStart(r.trainStart());
        e.setTrainEnd(r.trainEnd());
        e.setTestStart(r.testStart());
        e.setTestEnd(r.testEnd());
        e.setSampleDays(r.sampleDays());
        e.setMae(scale4(r.candidateMae()));
        e.setRmse(scale4(r.candidateRmse()));
        e.setBaselineMae(scale4(r.baselineMae()));
        e.setBaselineRmse(scale4(r.baselineRmse()));
        e.setSelectedModel(r.selectedModel());
        return evaluations.save(e);
    }

    /** Fills in the actual units for past forecasts so actual-versus-forecast can be reported. */
    private void fillActuals(Product product, List<DemandPoint> history, LocalDate today) {
        Map<LocalDate, Double> byDay = new HashMap<>();
        history.forEach(p -> byDay.put(p.date(), p.units()));
        for (Forecast f : forecasts.demandAwaitingActual(product.getId(), today)) {
            double actual = byDay.getOrDefault(f.getTargetDate(), 0.0);
            f.setActualValue(BigDecimal.valueOf(actual).setScale(3, RoundingMode.HALF_UP));
        }
    }

    /**
     * Estimated days until an ingredient runs out at its recent rate of use (4.6.13):
     * days left = usable stock / average daily use over the last 28 days (production and waste).
     * Returns null when there is no recent use, because no stockout is then expected.
     */
    @Transactional
    public Forecast estimateStockout(Business business, Ingredient ingredient) {
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant firstUse = movements.firstUsage(ingredient.getId());
        forecasts.supersedeStockout(ingredient.getId());
        long daysObserved = firstUse == null ? 0
                : Math.min(stockoutWindowDays, ChronoUnit.DAYS.between(firstUse.atZone(zone).toLocalDate(), today));
        if (firstUse == null || daysObserved < stockoutMinDays) {
            Forecast f = baseForecast(business, ForecastType.STOCKOUT, today, today, 1);
            f.setIngredient(ingredient);
            f.setModelName("AVERAGE_DAILY_USAGE");
            f.setModelVersion("1");
            f.setValueUnit("days");
            f.setStatus(ForecastStatus.INSUFFICIENT_DATA);
            return forecasts.save(f);
        }
        Instant since = today.minusDays(daysObserved).atStartOfDay(zone).toInstant();
        BigDecimal used = movements.usageSince(ingredient.getId(), since);
        if (used.signum() <= 0) {
            notifications.resolve(business.getId(), NotificationType.PREDICTED_STOCKOUT, "INGREDIENT", ingredient.getId());
            return null;
        }
        BigDecimal averageDaily = used.divide(BigDecimal.valueOf(daysObserved), 10, RoundingMode.HALF_UP);
        BigDecimal usable = ingredient.getCurrentQuantity().max(BigDecimal.ZERO);
        BigDecimal daysLeft = usable.divide(averageDaily, 3, RoundingMode.HALF_UP);
        int horizon = Math.max(1, daysLeft.setScale(0, RoundingMode.CEILING).intValue());
        Forecast f = baseForecast(business, ForecastType.STOCKOUT, today, today.plusDays(horizon), horizon);
        f.setIngredient(ingredient);
        f.setModelName("AVERAGE_DAILY_USAGE");
        f.setModelVersion("1");
        f.setValueUnit("days");
        f.setPredictedValue(daysLeft);
        f.setStatus(ForecastStatus.ACTIVE);
        forecasts.save(f);
        if (daysLeft.compareTo(BigDecimal.valueOf(stockoutWarningDays)) <= 0) {
            notifications.raise(business, NotificationType.PREDICTED_STOCKOUT, NotificationNature.PREDICTED,
                    "INGREDIENT", ingredient.getId(), ingredient.getName() + " may run out "
                            + com.businessbuddy.service.insights.InsightsService.runOutWords(daysLeft) + " (estimate)");
        } else {
            notifications.resolve(business.getId(), NotificationType.PREDICTED_STOCKOUT, "INGREDIENT", ingredient.getId());
        }
        return f;
    }

    private Forecast baseForecast(Business business, ForecastType type, LocalDate cutoff, LocalDate target, int horizon) {
        Forecast f = new Forecast();
        f.setBusiness(business);
        f.setType(type);
        f.setTrainingCutoff(cutoff);
        f.setTargetDate(target);
        f.setHorizonDays(horizon);
        f.setGeneratedAt(Instant.now(clock));
        return f;
    }

    @Transactional(readOnly = true)
    public List<Forecast> currentDemand(Long businessId) {
        return forecasts.currentDemand(businessId);
    }

    @Transactional(readOnly = true)
    public List<Forecast> currentStockout(Long businessId) {
        return forecasts.currentStockout(businessId);
    }

    @Transactional(readOnly = true)
    public List<ModelEvaluation> evaluations(Long businessId) {
        return evaluations.findByBusinessIdOrderByIdDesc(businessId);
    }

    @Transactional(readOnly = true)
    public List<Forecast> oneDayAheadHistory(Long productId) {
        return forecasts.oneDayAheadHistory(productId);
    }

    public boolean isResale(Product p) {
        return p.getType() == ProductType.RESALE;
    }

    private static BigDecimal scale4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
