package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.Dimension;
import com.businessbuddy.entity.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, String> {

    List<UnitOfMeasure> findAllByOrderByDimensionAscFactorToBaseAsc();

    List<UnitOfMeasure> findByDimensionOrderByFactorToBase(Dimension dimension);
}
