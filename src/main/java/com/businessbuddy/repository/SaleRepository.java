package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.SaleStatus;
import com.businessbuddy.entity.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);

    /** Sales list; recordedBy restricts an Employee to their own sales (Table 4.37). */
    @EntityGraph(attributePaths = {"customer", "recordedBy"})
    @Query("""
            select s from Sale s where s.business.id = :businessId
              and (:status is null or s.status = :status)
              and (:recordedBy is null or s.recordedBy.id = :recordedBy)
              and s.saleDate >= :from and s.saleDate < :to
            order by s.saleDate desc, s.id desc""")
    Page<Sale> search(@Param("businessId") Long businessId, @Param("status") SaleStatus status,
                      @Param("recordedBy") Long recordedBy, @Param("from") Instant from,
                      @Param("to") Instant to, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    List<Sale> findTop5ByBusinessIdAndStatusOrderBySaleDateDesc(Long businessId, SaleStatus status);

    long countByBusinessId(Long businessId);

    boolean existsByBusinessIdAndSaleNumber(Long businessId, String saleNumber);

    /** Sales grouped by payment method: method, count, revenue excluding tax. */
    @Query("""
            select s.paymentMethod, count(s), sum(s.total - s.taxTotal) from Sale s
            where s.business.id = :businessId and s.status = 'CONFIRMED'
              and s.saleDate >= :from and s.saleDate < :to
            group by s.paymentMethod order by s.paymentMethod""")
    List<Object[]> byPaymentMethod(@Param("businessId") Long businessId, @Param("from") Instant from,
                                   @Param("to") Instant to);

    @EntityGraph(attributePaths = {"recordedBy", "voidedBy"})
    @Query("""
            select s from Sale s where s.business.id = :businessId and s.status = 'VOIDED'
              and s.saleDate >= :from and s.saleDate < :to order by s.saleDate desc""")
    List<Sale> findVoided(@Param("businessId") Long businessId, @Param("from") Instant from,
                          @Param("to") Instant to);
}
