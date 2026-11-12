-- Intentionally a no-op (2026-11-12). This migration used to drop
-- fk_retail_checkout_holds_checkout, but V80 already drops and recreates that
-- foreign key, so on an empty database V80 failed with MySQL 1091 (the key was
-- already gone). V80 is left unchanged because every existing database applied it.
-- A database that applied the old V79_1 needs one `flyway repair` for this checksum.
DO 0;
