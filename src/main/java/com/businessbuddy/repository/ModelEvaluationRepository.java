package com.businessbuddy.repository;

import com.businessbuddy.entity.ModelEvaluation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelEvaluationRepository extends JpaRepository<ModelEvaluation, Long> {

    @EntityGraph(attributePaths = "product")
    List<ModelEvaluation> findByBusinessIdOrderByIdDesc(Long businessId);
}
