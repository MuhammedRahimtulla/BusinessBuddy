package com.businessbuddy.repository;

import com.businessbuddy.entity.RecipeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {

    List<RecipeVersion> findByProductIdOrderByVersionNoDesc(Long productId);

    Optional<RecipeVersion> findByProductIdAndActiveTrue(Long productId);

    @Query("select coalesce(max(r.versionNo), 0) from RecipeVersion r where r.product.id = :productId")
    int maxVersionNo(@Param("productId") Long productId);

    @Query("select r from RecipeVersion r where r.id = :id and r.product.business.id = :businessId")
    Optional<RecipeVersion> findOwned(@Param("id") Long id, @Param("businessId") Long businessId);

    /** Clears the active flag before another version is activated (uq_recipe_active allows one). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update RecipeVersion r set r.active = false where r.product.id = :productId and r.active = true")
    int deactivateAll(@Param("productId") Long productId);
}
