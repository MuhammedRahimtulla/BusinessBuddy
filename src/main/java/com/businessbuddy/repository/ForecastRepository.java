package com.businessbuddy.repository;

import com.businessbuddy.entity.Forecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ForecastRepository extends JpaRepository<Forecast, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update Forecast f set f.status = 'SUPERSEDED'
            where f.product.id = :productId and f.type = 'DEMAND' and f.status <> 'SUPERSEDED'""")
    int supersedeDemand(@Param("productId") Long productId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Forecast f set f.status = 'SUPERSEDED'
            where f.ingredient.id = :ingredientId and f.type = 'STOCKOUT' and f.status <> 'SUPERSEDED'""")
    int supersedeStockout(@Param("ingredientId") Long ingredientId);

    @Query("""
            select f from Forecast f join fetch f.product p left join fetch f.modelEvaluation
            where f.business.id = :businessId and f.type = 'DEMAND' and f.status <> 'SUPERSEDED'
            order by p.name, f.targetDate""")
    List<Forecast> currentDemand(@Param("businessId") Long businessId);

    @Query("""
            select f from Forecast f join fetch f.ingredient i
            where f.business.id = :businessId and f.type = 'STOCKOUT' and f.status <> 'SUPERSEDED'
            order by f.predictedValue nulls last, i.name""")
    List<Forecast> currentStockout(@Param("businessId") Long businessId);

    /** All demand forecasts of a product, for the actual-versus-forecast report. */
    @Query("""
            select f from Forecast f where f.product.id = :productId and f.type = 'DEMAND'
              and f.status <> 'INSUFFICIENT_DATA' and f.horizonDays = 1
            order by f.targetDate""")
    List<Forecast> oneDayAheadHistory(@Param("productId") Long productId);

    @Query("""
            select f from Forecast f where f.product.id = :productId and f.type = 'DEMAND'
              and f.actualValue is null and f.status <> 'INSUFFICIENT_DATA' and f.targetDate < :today""")
    List<Forecast> demandAwaitingActual(@Param("productId") Long productId, @Param("today") LocalDate today);
}
