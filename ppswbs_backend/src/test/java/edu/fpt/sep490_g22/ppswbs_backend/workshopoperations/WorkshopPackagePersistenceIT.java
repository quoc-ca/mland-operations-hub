package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
class WorkshopPackagePersistenceIT {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopPackageRepository packageRepository;

    @BeforeEach
    void requireMySql() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WorkshopPackagePersistenceIT requires real MySQL/Flyway evidence");
        }
    }

    @Test
    void v21CreatesPackageMasterAndJpaRoundTripsGenericRules() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '21' and success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = 'workshop_packages'",
                Integer.class)).isEqualTo(1);

        String uniqueName = "Persistence IT " + UUID.randomUUID();
        WorkshopPackage saved = packageRepository.save(WorkshopPackage.builder()
                .name(uniqueName)
                .description("Generic package persistence contract")
                .publicPrice(500_000L)
                .minParticipants(1)
                .maxParticipants(10)
                .requiredSessionCount(2)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(1)
                .state(WorkshopPackageState.DRAFT)
                .build());

        WorkshopPackage reloaded = packageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(WorkshopPackageState.DRAFT);
        assertThat(reloaded.getPublicPrice()).isEqualTo(500_000L);
        assertThat(reloaded.getRequiredSessionCount()).isEqualTo(2);
        assertThat(reloaded.getMinimumSameDaySlotGap()).isEqualTo(1);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
