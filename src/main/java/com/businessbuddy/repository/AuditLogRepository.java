package com.businessbuddy.repository;

import com.businessbuddy.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = {"user", "business"})
    Page<AuditLog> findByBusinessIdOrderByIdDesc(Long businessId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "business"})
    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditLog> findByActionOrderByIdDesc(String action);
}
