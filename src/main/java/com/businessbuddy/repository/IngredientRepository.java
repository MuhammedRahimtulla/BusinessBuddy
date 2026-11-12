package com.businessbuddy.repository;

import com.businessbuddy.entity.Ingredient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByIdAndBusinessId(Long id, Long businessId);

    /** Row lock used by every stock change so concurrent movements cannot be lost (Table 4.36). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id = :id")
    Optional<Ingredient> lockById(@Param("id") Long id);

    List<Ingredient> findByBusinessIdAndActiveTrueOrderByName(Long businessId);

    List<Ingredient> findByBusinessIdOrderByName(Long businessId);

    @Query("""
            select i from Ingredient i where i.business.id = :businessId
              and (:q = '' or lower(i.name) like lower(concat('%', :q, '%')))
              and (:lowOnly = false or i.currentQuantity < i.minStockLevel)
            order by i.name""")
    Page<Ingredient> search(@Param("businessId") Long businessId, @Param("q") String q,
                            @Param("lowOnly") boolean lowOnly, Pageable pageable);

    @Query("""
            select i from Ingredient i where i.business.id = :businessId and i.active = true
              and i.currentQuantity < i.minStockLevel order by i.name""")
    List<Ingredient> findBelowMinimum(@Param("businessId") Long businessId);

    long countByBusinessIdAndActiveTrue(Long businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(Long businessId, String name, Long id);
}
