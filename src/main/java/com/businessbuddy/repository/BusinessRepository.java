package com.businessbuddy.repository;

import com.businessbuddy.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findFirstByDemoDataTrue();
}
