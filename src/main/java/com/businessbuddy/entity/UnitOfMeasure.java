package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.Dimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * A unit and its conversion factor to the base unit of its dimension (Table 4.15).
 * Reference data inserted by the V1 migration; the application never changes it.
 */
@Entity
@Immutable
@Table(name = "unit_of_measure")
public class UnitOfMeasure {

    @Id
    @Column(name = "unit_code", length = 10)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Dimension dimension;

    @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorToBase;

    protected UnitOfMeasure() {
    }

    public UnitOfMeasure(String code, String name, Dimension dimension, BigDecimal factorToBase) {
        this.code = code;
        this.name = name;
        this.dimension = dimension;
        this.factorToBase = factorToBase;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Dimension getDimension() { return dimension; }
    public BigDecimal getFactorToBase() { return factorToBase; }

    @Override
    public boolean equals(Object o) {
        return o instanceof UnitOfMeasure other && code != null && code.equals(other.code);
    }

    @Override
    public int hashCode() { return code == null ? 0 : code.hashCode(); }

    @Override
    public String toString() { return code; }
}
