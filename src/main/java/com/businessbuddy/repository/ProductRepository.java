package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);

    List<Product> findByBusinessIdAndActiveTrueOrderByName(Long businessId);

    List<Product> findByBusinessIdOrderByName(Long businessId);

    List<Product> findByBusinessIdAndTypeAndActiveTrueOrderByName(Long businessId, ProductType type);

    @Query("""
            select p from Product p where p.business.id = :businessId
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :q, '%')))
            order by p.name""")
    Page<Product> search(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);

    @Query("""
            select p from Product p where p.business.id = :businessId and p.active = true
              and p.currentQuantity < p.minStockLevel order by p.name""")
    List<Product> findBelowMinimum(@Param("businessId") Long businessId);

    long countByBusinessIdAndActiveTrue(Long businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(Long businessId, String name, Long id);

    boolean existsByBusinessIdAndSkuIgnoreCase(Long businessId, String sku);

    boolean existsByBusinessIdAndSkuIgnoreCaseAndIdNot(Long businessId, String sku, Long id);
}
