package com.businessbuddy.repository;

import com.businessbuddy.entity.Category;
import com.businessbuddy.entity.Enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndBusinessId(Long id, Long businessId);

    List<Category> findByBusinessIdOrderByTypeAscNameAsc(Long businessId);

    List<Category> findByBusinessIdAndTypeOrderByName(Long businessId, CategoryType type);

    boolean existsByBusinessIdAndTypeAndNameIgnoreCase(Long businessId, CategoryType type, String name);
}
