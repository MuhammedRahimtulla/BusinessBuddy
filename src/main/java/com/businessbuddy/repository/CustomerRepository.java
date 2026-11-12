package com.businessbuddy.repository;

import com.businessbuddy.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    List<Customer> findByBusinessIdOrderByName(Long businessId);

    @Query("""
            select c from Customer c where c.business.id = :businessId
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.phone, '')) like lower(concat('%', :q, '%')))
            order by c.name""")
    Page<Customer> search(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);
}
