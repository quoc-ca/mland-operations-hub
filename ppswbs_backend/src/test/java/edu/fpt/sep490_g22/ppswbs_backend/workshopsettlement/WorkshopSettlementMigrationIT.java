package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkshopSettlementMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireMySql() throws SQLException {
        String product = dataSource.getConnection().getMetaData().getDatabaseProductName();
        Assumptions.assumeTrue("MySQL".equalsIgnoreCase(product), "Requires real MySQL evidence");
    }

    @Test
    void v17SettlementHandoffTablesAreApplied() {
        Integer cases = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'settlement_cancellation_cases'",
                Integer.class);
        Integer idempotency = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'settlement_handoff_idempotency'",
                Integer.class);
        Integer migration = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '17' AND success = 1",
                Integer.class);
        assertThat(cases).isEqualTo(1);
        assertThat(idempotency).isEqualTo(1);
        assertThat(migration).isEqualTo(1);
    }
}
