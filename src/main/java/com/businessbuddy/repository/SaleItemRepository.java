package com.businessbuddy.repository;

import com.businessbuddy.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate queries over confirmed sale lines. They read only stored transaction values
 * (net amount, tax, line cost snapshots), never current prices or costs (4.6.11).
 */
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /**
     * Per-product totals for a period. Each row: product id, product name, units,
     * revenue (net), discounts, tax, cost of goods sold.
     */
    @Query("""
            select p.id, p.name, sum(si.quantity), sum(si.netAmount), sum(si.discountAmount),
                   sum(si.taxAmount), sum(si.lineCost)
            from SaleItem si join si.sale s join si.product p
            where s.business.id = :businessId and s.status = 'CONFIRMED'
              and s.saleDate >= :from and s.saleDate < :to
            group by p.id, p.name""")
    List<Object[]> productTotals(@Param("businessId") Long businessId, @Param("from") Instant from,
                                 @Param("to") Instant to);

    /** Daily units sold of one product in the business time zone. Each row: date, units. */
    @Query(value = """
            SELECT CAST(s.sale_date AT TIME ZONE :tz AS date) AS day, SUM(si.quantity) AS units
            FROM sale_item si JOIN sale s ON s.sale_id = si.sale_id
            WHERE s.business_id = :businessId AND si.product_id = :productId AND s.status = 'CONFIRMED'
              AND s.sale_date >= :from
            GROUP BY day ORDER BY day""", nativeQuery = true)
    List<Object[]> dailyUnits(@Param("businessId") Long businessId, @Param("productId") Long productId,
                              @Param("tz") String timeZone, @Param("from") Instant from);

    /** Monthly revenue and cost of goods sold. Each row: 'YYYY-MM', revenue, COGS. */
    @Query(value = """
            SELECT to_char(s.sale_date AT TIME ZONE :tz, 'YYYY-MM') AS month,
                   SUM(si.net_amount) AS revenue, COALESCE(SUM(si.line_cost), 0) AS cogs
            FROM sale_item si JOIN sale s ON s.sale_id = si.sale_id
            WHERE s.business_id = :businessId AND s.status = 'CONFIRMED'
              AND s.sale_date >= :from AND s.sale_date < :to
            GROUP BY month ORDER BY month""", nativeQuery = true)
    List<Object[]> monthlyRevenue(@Param("businessId") Long businessId, @Param("tz") String timeZone,
                                  @Param("from") Instant from, @Param("to") Instant to);

    /** Units of a product sold since a moment (confirmed sales only). */
    @Query("""
            select coalesce(sum(si.quantity), 0) from SaleItem si join si.sale s
            where si.product.id = :productId and s.status = 'CONFIRMED'
              and s.saleDate >= :from and s.saleDate < :to""")
    java.math.BigDecimal unitsSold(@Param("productId") Long productId, @Param("from") Instant from,
                                   @Param("to") Instant to);
}
