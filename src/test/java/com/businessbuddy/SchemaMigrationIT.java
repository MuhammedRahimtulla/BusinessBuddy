package com.businessbuddy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database tests: Flyway builds the Chapter 4 schema in a real PostgreSQL database, Hibernate's
 * validation of the entity mappings passes (the context starts), and the CHECK constraints
 * act as the last barrier against invalid data (4.8.3).
 */
class SchemaMigrationIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("TC-DB-01: all 23 tables exist in businessbuddy_db")
    void allTablesCreated() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'""", Integer.class);
        assertThat(tables).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("businessbuddy_db");
    }

    @Test
    @DisplayName("TC-DB-02: unit of measure reference data is seeded (1 kg = 1000 g)")
    void unitsSeeded() {
        assertThat(jdbc.queryForObject("SELECT factor_to_base FROM unit_of_measure WHERE unit_code = 'kg'",
                BigDecimal.class)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("TC-DB-03: the database rejects a waste movement that increases stock (ck_movement_sign)")
    void movementSignConstraint() {
        Long ownerId = newBusinessOwner("Constraint test").getId();
        Long businessId = users.findById(ownerId).orElseThrow().owningBusinessId();
        Long ingredientId = ingredient("Salt", "g", "0");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory_movement (business_id, ingredient_id, movement_type, quantity_change, unit_cost,
                    source_type, reason, created_by) VALUES (?, ?, 'WASTE', 5, 0, 'MANUAL', 'test', ?)""",
                businessId, ingredientId, ownerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC-DB-04: the database rejects an email that is not lower case (ck_app_user_email_lower)")
    void emailLowerCaseConstraint() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (full_name, email, password_hash, role) VALUES ('X', 'Admin@X.test', 'h', 'ADMINISTRATOR')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
