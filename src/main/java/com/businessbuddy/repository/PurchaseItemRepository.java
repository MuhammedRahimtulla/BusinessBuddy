package com.businessbuddy.repository;

import com.businessbuddy.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    /** Purchase price history of one ingredient, newest first (Table 4.49). */
    @Query("""
            select pi from PurchaseItem pi join fetch pi.purchase p join fetch p.supplier
            where pi.ingredient.id = :ingredientId and p.status = 'RECEIVED'
            order by p.receivedDate desc, pi.id desc""")
    List<PurchaseItem> priceHistory(@Param("ingredientId") Long ingredientId);

    /**
     * Weighted average landed cost per base unit for each ingredient received in a period.
     * Each row: ingredient id, ingredient name, average cost. Used by InsightsService.
     */
    @Query("""
            select i.id, i.name, sum(pi.landedUnitCost * pi.baseQuantity) / sum(pi.baseQuantity)
            from PurchaseItem pi join pi.purchase p join pi.ingredient i
            where p.business.id = :businessId and p.status = 'RECEIVED'
              and p.receivedDate between :from and :to
            group by i.id, i.name""")
    List<Object[]> averageLandedCostByIngredient(@Param("businessId") Long businessId,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Received deliveries with an expiry date whose ingredient still has stock (4.6.10). */
    @Query("""
            select pi from PurchaseItem pi join fetch pi.ingredient i join pi.purchase p
            where p.business.id = :businessId and p.status = 'RECEIVED' and pi.expiryDate is not null
              and i.active = true and i.currentQuantity > 0
            order by pi.expiryDate""")
    List<PurchaseItem> expiryCandidates(@Param("businessId") Long businessId);
}
