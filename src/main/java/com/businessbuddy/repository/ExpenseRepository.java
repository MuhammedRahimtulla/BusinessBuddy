package com.businessbuddy.repository;

import com.businessbuddy.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = {"category", "supplier"})
    @Query("""
            select e from Expense e where e.business.id = :businessId
              and (:categoryId is null or e.category.id = :categoryId)
              and e.expenseDate between :from and :to
            order by e.expenseDate desc, e.id desc""")
    Page<Expense> search(@Param("businessId") Long businessId, @Param("categoryId") Long categoryId,
                         @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.business.id = :businessId and e.expenseDate between :from and :to""")
    BigDecimal total(@Param("businessId") Long businessId, @Param("from") LocalDate from,
                     @Param("to") LocalDate to);

    /** Each row: category name, total. */
    @Query("""
            select c.name, sum(e.amount) from Expense e join e.category c
            where e.business.id = :businessId and e.expenseDate between :from and :to
            group by c.name order by sum(e.amount) desc""")
    List<Object[]> totalsByCategory(@Param("businessId") Long businessId, @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /** Each row: 'YYYY-MM', total. */
    @Query(value = """
            SELECT to_char(expense_date, 'YYYY-MM') AS month, SUM(amount) AS total FROM expense
            WHERE business_id = :businessId AND expense_date BETWEEN :from AND :to
            GROUP BY month ORDER BY month""", nativeQuery = true)
    List<Object[]> monthlyTotals(@Param("businessId") Long businessId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
