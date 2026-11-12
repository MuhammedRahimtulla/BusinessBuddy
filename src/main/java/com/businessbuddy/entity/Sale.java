package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.SaleStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A sale document header (Table 4.28). A confirmed sale is never deleted; it is voided
 * with a reason and its stock is returned through REVERSAL movements (4.6.9).
 */
@Entity
@Table(name = "sale")
@AttributeOverride(name = "id", column = @Column(name = "sale_id"))
public class Sale extends BusinessOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_number", nullable = false, length = 30)
    private String saleNumber;

    @Column(name = "sale_date", nullable = false)
    private Instant saleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SaleStatus status = SaleStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 15)
    private PaymentMethod paymentMethod;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false, updatable = false)
    private User recordedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getSaleNumber() { return saleNumber; }
    public void setSaleNumber(String saleNumber) { this.saleNumber = saleNumber; }
    public Instant getSaleDate() { return saleDate; }
    public void setSaleDate(Instant saleDate) { this.saleDate = saleDate; }
    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxTotal = taxTotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }
    public User getVoidedBy() { return voidedBy; }
    public void setVoidedBy(User voidedBy) { this.voidedBy = voidedBy; }
    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public List<SaleItem> getItems() { return items; }
}
