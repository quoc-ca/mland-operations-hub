package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Shared test fixtures for productmanagement (T018). Test scope only: these values are never a
 * production fallback and fixture-based results are not integration evidence for identity, media
 * or FE-08 holds, which need the owners' real environments.
 */
public final class CatalogueTestSupport {

    public static final String MYSQL_PROFILE = "productmanagement-mysql-test";

    /** Opaque actor references; no credential or personal data. */
    public static final String STAFF = "test-staff-1";
    public static final String MANAGER = "test-manager-1";
    public static final String OTHER_MANAGER = "test-manager-2";
    public static final String ADMIN = "test-admin-1";
    public static final String MEMBER = "test-member-1";

    public static final Instant FIXED_NOW = Instant.parse("2026-10-26T03:00:00.123456Z");

    private CatalogueTestSupport() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    public static List<Integer> allSizes() {
        return IntStream.rangeClosed(5, 29).boxed().toList();
    }

    public static ProductCategory activeCategory(String name) {
        return ProductCategory.create(name, null);
    }

    public static ProductCategory inactiveCategory(String name) {
        ProductCategory category = ProductCategory.create(name, null);
        category.setActive(false);
        return category;
    }

    /**
     * Refuses anything but MySQL 8.4, so a test can never pass on H2 or another engine by accident
     * (constitution IV: database evidence must come from the selected baseline).
     */
    public static String requireMySql84(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData.getDatabaseProductName();
            String version = metaData.getDatabaseProductVersion();
            if (!"MySQL".equalsIgnoreCase(product) || !version.startsWith("8.4")) {
                throw new IllegalStateException("Expected MySQL 8.4 for catalogue database tests but found "
                        + product + " " + version);
            }
            return version;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the test database metadata", e);
        }
    }
}
