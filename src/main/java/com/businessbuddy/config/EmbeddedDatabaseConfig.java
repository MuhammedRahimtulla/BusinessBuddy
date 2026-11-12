package com.businessbuddy.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Starts a real PostgreSQL server inside the JVM for the "local" and "test" profiles.
 *
 * <p>This lets the system and its integration tests run on a computer without a separate
 * PostgreSQL installation or Docker. Production uses an external PostgreSQL server
 * configured through environment variables (application-prod.yml).</p>
 */
@Configuration
@Profile({"local", "test"})
public class EmbeddedDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedDatabaseConfig.class);
    private static final String DATABASE_NAME = "businessbuddy_db";

    /** Empty means a throw-away data directory (tests); a path keeps data between runs (local). */
    @Value("${businessbuddy.embedded-db.data-dir:}")
    private String dataDir;

    @Value("${businessbuddy.embedded-db.port:0}")
    private int port;

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
        if (port > 0) {
            builder.setPort(port);
        }
        if (!dataDir.isBlank()) {
            builder.setDataDirectory(Path.of(dataDir)).setCleanDataDirectory(false);
        }
        EmbeddedPostgres postgres = builder.start();
        log.info("Embedded PostgreSQL started on port {}", postgres.getPort());
        return postgres;
    }

    @Bean
    public DataSource dataSource(EmbeddedPostgres postgres) throws SQLException {
        createDatabaseIfMissing(postgres);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl("postgres", DATABASE_NAME));
        config.setUsername("postgres");
        config.setMaximumPoolSize(10);
        config.setPoolName("businessbuddy-embedded");
        return new HikariDataSource(config);
    }

    private void createDatabaseIfMissing(EmbeddedPostgres postgres) throws SQLException {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '" + DATABASE_NAME + "'")) {
            if (!rs.next()) {
                statement.execute("CREATE DATABASE " + DATABASE_NAME);
            }
        }
    }
}
