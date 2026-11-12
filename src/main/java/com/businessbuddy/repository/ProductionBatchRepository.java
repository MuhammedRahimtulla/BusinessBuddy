package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.BatchStatus;
import com.businessbuddy.entity.ProductionBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    Optional<ProductionBatch> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatch b where b.id = :id")
    Optional<ProductionBatch> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"product", "recipeVersion", "producedBy"})
    @Query("""
            select b from ProductionBatch b where b.business.id = :businessId
              and (:status is null or b.status = :status)
            order by b.productionDate desc, b.id desc""")
    Page<ProductionBatch> search(@Param("businessId") Long businessId, @Param("status") BatchStatus status,
                                 Pageable pageable);
}
