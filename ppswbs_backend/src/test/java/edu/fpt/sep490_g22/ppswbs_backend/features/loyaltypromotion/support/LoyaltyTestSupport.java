package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Shared test fixtures for loyaltypromotion (T005). Test scope only: nothing here is a production
 * fallback, and results that use these fixtures are not integration evidence for identity (FE-01),
 * catalogue (FE-02), booking (FE-03) or retail (FE-08), which need the owners' real environments.
 */
public final class LoyaltyTestSupport {

    public static final String MYSQL_PROFILE = "loyaltypromotion-mysql-test";

    /** Opaque actor references and member ids; no credential or personal data. */
    public static final String MANAGER = "test-manager-1";
    public static final String STAFF = "test-staff-1";
    public static final String ADMIN = "test-admin-1";
    public static final long MEMBER_ID = 9_000_001L;
    public static final long OTHER_MEMBER_ID = 9_000_002L;
    public static final long MANAGER_ACCOUNT_ID = 9_000_100L;

    public static final Instant FIXED_NOW = Instant.parse("2026-10-26T03:00:00.123456Z");

    private LoyaltyTestSupport() {
    }

    /** Deterministic clock for tests that must not read the wall clock. */
    public static Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    /**
     * Refuses anything but MySQL 8.4, so a database test can never pass on H2 or another engine by
     * accident (constitution IV: database evidence must come from the selected baseline).
     */
    public static String requireMySql84(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData.getDatabaseProductName();
            String version = metaData.getDatabaseProductVersion();
            if (!"MySQL".equalsIgnoreCase(product) || !version.startsWith("8.4")) {
                throw new IllegalStateException("Expected MySQL 8.4 for loyalty database tests but found "
                        + product + " " + version);
            }
            return version;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the test database metadata", e);
        }
    }

    /** One finished action: either a value or the failure it ended with. */
    public record Outcome<T>(T value, Throwable failure) {
        public boolean succeeded() {
            return failure == null;
        }
    }

    /**
     * Two-transaction barrier: runs the actions on separate threads that start together (for last-slot,
     * same-wallet and duplicate-command races) and returns each result or failure in order.
     */
    @SafeVarargs
    public static <T> List<Outcome<T>> concurrently(Callable<T>... actions) {
        ExecutorService pool = Executors.newFixedThreadPool(actions.length);
        CyclicBarrier start = new CyclicBarrier(actions.length);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                } catch (Exception e) {
                    outcomes.add(new Outcome<>(null, e));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
