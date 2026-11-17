-- Intentionally does nothing.
-- V80 drops, retypes and recreates fk_retail_checkout_holds_checkout itself.
-- Dropping the key here made V80 fail on a fresh database (MySQL 1091), because
-- V80 then had no key left to drop. The file is kept so databases that already
-- applied 79.1 still resolve it.
SELECT 1;
