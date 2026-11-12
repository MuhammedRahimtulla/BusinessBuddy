package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.PurchaseStatus;
import com.businessbuddy.entity.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = "supplier")
    @Query("""
            select p from Purchase p where p.business.id = :businessId
              and (:status is null or p.status = :status)
              and (:supplierId is null or p.supplier.id = :supplierId)
            order by p.purchaseDate desc, p.id desc""")
    Page<Purchase> search(@Param("businessId") Long businessId, @Param("status") PurchaseStatus status,
                          @Param("supplierId") Long supplierId, Pageable pageable);

    @EntityGraph(attributePaths = "supplier")
    List<Purchase> findTop5ByBusinessIdAndStatusOrderByReceivedDateDescIdDesc(Long businessId, PurchaseStatus status);

    @EntityGraph(attributePaths = "supplier")
    @Query("""
            select p from Purchase p where p.business.id = :businessId and p.status = 'RECEIVED'
              and p.receivedDate between :from and :to
            order by p.receivedDate, p.id""")
    List<Purchase> findReceivedBetween(@Param("businessId") Long businessId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    boolean existsByBusinessIdAndSupplierIdAndReferenceNoIgnoreCase(Long businessId, Long supplierId, String referenceNo);

    boolean existsByBusinessIdAndSupplierIdAndReferenceNoIgnoreCaseAndIdNot(Long businessId, Long supplierId,
                                                                              String referenceNo, Long id);
}
