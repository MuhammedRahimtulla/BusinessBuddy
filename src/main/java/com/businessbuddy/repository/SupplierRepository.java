package com.businessbuddy.repository;

import com.businessbuddy.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndBusinessId(Long id, Long businessId);

    List<Supplier> findByBusinessIdAndActiveTrueOrderByName(Long businessId);

    @Query("""
            select s from Supplier s where s.business.id = :businessId
              and (:q = '' or lower(s.name) like lower(concat('%', :q, '%')))
            order by s.name""")
    Page<Supplier> search(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);

    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(Long businessId, String name, Long id);
}
