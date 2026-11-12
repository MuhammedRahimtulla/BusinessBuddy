package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** A registered business and its settings (Table 4.12). */
@Entity
@Table(name = "business")
@AttributeOverride(name = "id", column = @Column(name = "business_id"))
public class Business extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    private String email;
    private String phone;
    private String address;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "default_tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal defaultTaxRate = BigDecimal.ZERO;

    @Column(name = "default_target_margin", nullable = false, precision = 5, scale = 4)
    private BigDecimal defaultTargetMargin = BigDecimal.ZERO;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    /** True only for the generated demonstration business (V2 migration). */
    @Column(name = "is_demo_data", nullable = false)
    private boolean demoData;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public BigDecimal getDefaultTaxRate() { return defaultTaxRate; }
    public void setDefaultTaxRate(BigDecimal defaultTaxRate) { this.defaultTaxRate = defaultTaxRate; }
    public BigDecimal getDefaultTargetMargin() { return defaultTargetMargin; }
    public void setDefaultTargetMargin(BigDecimal defaultTargetMargin) { this.defaultTargetMargin = defaultTargetMargin; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
    public boolean isDemoData() { return demoData; }
    public void setDemoData(boolean demoData) { this.demoData = demoData; }

    public java.time.ZoneId zoneId() { return java.time.ZoneId.of(timeZone); }
}
