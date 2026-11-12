package com.businessbuddy.controller;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Forecast;
import com.businessbuddy.entity.Product;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.CatalogService;
import com.businessbuddy.service.ForecastService;
import com.businessbuddy.service.NightlyJobs;
import com.businessbuddy.service.insights.InsightsService;
import com.businessbuddy.util.DateRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forecasts, stockout estimates, model evaluation and insights. Every value on these pages
 * is labelled as an estimate, with its model and data cut-off date (4.7.3).
 */
@Controller
public class ForecastController {

    private final ForecastService forecasts;
    private final NightlyJobs jobs;
    private final InsightsService insights;
    private final CatalogService catalog;
    private final BusinessContext context;
    private final AuditService audit;

    public ForecastController(ForecastService forecasts, NightlyJobs jobs, InsightsService insights,
                              CatalogService catalog, BusinessContext context, AuditService audit) {
        this.forecasts = forecasts;
        this.jobs = jobs;
        this.insights = insights;
        this.catalog = catalog;
        this.context = context;
        this.audit = audit;
    }

    @GetMapping("/forecasts")
    public String index(Model model) {
        Long businessId = context.currentBusinessId();
        Map<Product, List<Forecast>> demand = new LinkedHashMap<>();
        for (Forecast f : forecasts.currentDemand(businessId)) {
            demand.computeIfAbsent(f.getProduct(), k -> new java.util.ArrayList<>()).add(f);
        }
        model.addAttribute("demand", demand);
        model.addAttribute("stockout", forecasts.currentStockout(businessId));
        model.addAttribute("evaluations", forecasts.evaluations(businessId));
        return "forecasts/index";
    }

    @GetMapping("/forecasts/product/{id}")
    public String product(@PathVariable Long id, Model model) {
        Product product = catalog.product(id);
        Business business = context.currentBusiness();
        List<com.businessbuddy.ml.DemandPoint> history = forecasts.dailyHistory(business, product,
                LocalDate.now(business.zoneId()).minusDays(1));
        List<Forecast> current = forecasts.currentDemand(business.getId()).stream()
                .filter(f -> f.getProduct().getId().equals(id)).toList();
        List<Forecast> past = forecasts.oneDayAheadHistory(id);

        // Chart series aligned on one date axis: last 60 days of actual sales, then the
        // current forecast (drawn dashed). Past one-day-ahead forecasts are shown for comparison.
        Map<LocalDate, java.math.BigDecimal> pastByDate = new java.util.HashMap<>();
        past.forEach(f -> pastByDate.put(f.getTargetDate(), f.getPredictedValue()));
        List<String> labels = new java.util.ArrayList<>();
        List<String> actual = new java.util.ArrayList<>();
        List<String> forecastLine = new java.util.ArrayList<>();
        List<String> pastLine = new java.util.ArrayList<>();
        int from = Math.max(0, history.size() - 60);
        for (int i = from; i < history.size(); i++) {
            LocalDate d = history.get(i).date();
            labels.add(d.toString());
            actual.add(String.valueOf(history.get(i).units()));
            forecastLine.add("");
            pastLine.add(pastByDate.containsKey(d) ? pastByDate.get(d).toPlainString() : "");
        }
        for (Forecast f : current) {
            if (f.getPredictedValue() == null) {
                continue;
            }
            labels.add(f.getTargetDate().toString());
            actual.add("");
            pastLine.add("");
            forecastLine.add(f.getPredictedValue().toPlainString());
        }
        model.addAttribute("product", product);
        model.addAttribute("current", current);
        model.addAttribute("historyDays", history.size());
        model.addAttribute("chartLabels", String.join("|", labels));
        model.addAttribute("chartActual", String.join("|", actual));
        model.addAttribute("chartForecast", String.join("|", forecastLine));
        model.addAttribute("chartPast", String.join("|", pastLine));
        return "forecasts/product";
    }

    /** Runs the nightly stock checks and forecasts now, for the current business. */
    @PostMapping("/forecasts/run")
    public String run(RedirectAttributes redirect) {
        Business business = context.currentBusiness();
        jobs.stockJob(business);
        Map<String, Integer> summary = forecasts.runAll(business);
        audit.record("FORECASTS_RUN", "BUSINESS", business.getId(), summary);
        redirect.addFlashAttribute("successMessage", "Forecasts updated: " + summary.get("demandForecasts")
                + " products forecast, " + summary.get("insufficientData") + " with too little history, "
                + summary.get("stockoutEstimates") + " stockout estimates.");
        return "redirect:/forecasts";
    }

    @GetMapping("/insights")
    public String insights(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        Business business = context.currentBusiness();
        DateRange period = from == null && to == null ? DateRange.lastDays(business.zoneId(), 30)
                : DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("insights", insights.generate(business, period));
        return "forecasts/insights";
    }
}
