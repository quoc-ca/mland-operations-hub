package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification test targeting real MySQL database and Flyway migration V6.
 * Uses the default spring test datasource configuration (MySQL on localhost:3306 / TEST_JDBC_URL).
 */
@SpringBootTest
class WorkshopOperationsMySQLIT {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify Flyway V6 workshop operations migration applied on MySQL")
    void verifyV6MigrationAppliedOnMySQL() {
        assertThat(jdbcTemplate).isNotNull();

        Integer v6Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1",
                Integer.class
        );
        assertThat(v6Count).isNotNull().isGreaterThanOrEqualTo(1);

        Integer sessionTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'workshop_sessions'",
                Integer.class
        );
        assertThat(sessionTableExists).isNotNull().isEqualTo(1);
    }
}
