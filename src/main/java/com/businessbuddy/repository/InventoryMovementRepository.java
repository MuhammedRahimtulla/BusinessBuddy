package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    Optional<InventoryMovement> findByTypeAndSourceTypeAndSourceId(MovementType type, SourceType sourceType, Long sourceId);

    /** Movement history with optional filters (null means "any"). */
    @EntityGraph(attributePaths = {"ingredient", "product", "createdBy"})
    @Query("""
            select m from InventoryMovement m where m.business.id = :businessId
              and (:ingredientId is null or m.ingredient.id = :ingredientId)
              and (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
              and m.movementDate >= :from and m.movementDate < :to
            order by m.movementDate desc, m.id desc""")
    Page<InventoryMovement> search(@Param("businessId") Long businessId, @Param("ingredientId") Long ingredientId,
                                   @Param("productId") Long productId, @Param("type") MovementType type,
                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** Ledger total for reconciliation against the cached quantity (4.6.10). */
    @Query("select coalesce(sum(m.quantityChange), 0) from InventoryMovement m where m.ingredient.id = :id")
    BigDecimal ledgerTotalForIngredient(@Param("id") Long ingredientId);

    @Query("select coalesce(sum(m.quantityChange), 0) from InventoryMovement m where m.product.id = :id")
    BigDecimal ledgerTotalForProduct(@Param("id") Long productId);

    /** Quantity used (production and waste) since a moment, as a positive number (4.6.13). */
    @Query("""
            select coalesce(-sum(m.quantityChange), 0) from InventoryMovement m
            where m.ingredient.id = :id and m.movementDate >= :since
              and m.type in ('PRODUCTION_CONSUMPTION', 'WASTE')""")
    BigDecimal usageSince(@Param("id") Long ingredientId, @Param("since") Instant since);

    @Query("""
            select min(m.movementDate) from InventoryMovement m
            where m.ingredient.id = :id and m.type in ('PRODUCTION_CONSUMPTION', 'WASTE')""")
    Instant firstUsage(@Param("id") Long ingredientId);

    boolean existsByIngredientId(Long ingredientId);

    boolean existsByProductId(Long productId);
}
