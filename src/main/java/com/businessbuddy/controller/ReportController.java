package com.businessbuddy.controller;

import com.businessbuddy.entity.Business;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.service.reporting.ReportService;
import com.businessbuddy.util.DateRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Reports and CSV export (Table 4.49). Owner and Manager only (URL rules and @PreAuthorize). */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reports;
    private final InventoryService inventory;
    private final BusinessContext context;

    public ReportController(ReportService reports, InventoryService inventory, BusinessContext context) {
        this.reports = reports;
        this.inventory = inventory;
        this.context = context;
    }

    @GetMapping
    public String index() {
        return "reports/index";
    }

    @GetMapping("/profitability")
    public String profitability(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                Model model) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("report", reports.profitability(business, period));
        model.addAttribute("trend", reports.monthlyTrend(business, 6));
        return "reports/profitability";
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        Long businessId = context.currentBusinessId();
        model.addAttribute("rows", reports.inventoryBalance(businessId));
        model.addAttribute("value", inventory.inventoryValue(businessId));
        return "reports/inventory";
    }

    @GetMapping("/reconciliation")
    public String reconciliation(Model model) {
        model.addAttribute("rows", inventory.reconcile(context.currentBusinessId()));
        return "reports/reconciliation";
    }

    @GetMapping("/sales")
    public String sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("payments", reports.salesByPayment(business, period));
        model.addAttribute("report", reports.profitability(business, period));
        model.addAttribute("voided", reports.voidedSales(business, period));
        return "reports/sales";
    }

    @GetMapping("/expenses")
    public String expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("rows", reports.expensesByCategory(business.getId(), period));
        model.addAttribute("trend", reports.monthlyTrend(business, 6));
        return "reports/expenses";
    }

    @GetMapping("/purchases")
    public String purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("purchases", reports.purchaseHistory(business.getId(), period));
        return "reports/purchases";
    }

    @GetMapping("/costing")
    public String costing(Model model) {
        model.addAttribute("rows", reports.productCosting(context.currentBusinessId()));
        return "reports/costing";
    }

    @GetMapping("/export/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        String csv = reports.exportCsv(business, type, period);
        String filename = "businessbuddy-" + type.replaceAll("[^a-z]", "") + "-" + period.from() + "-" + period.to() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
