-- Flyway Migration V141: Align Feature 004 dependent tables with independent design_requests table
-- Resolves BUG-E2E-0005: foreign keys were still referencing legacy booking_design_requests
-- Renumbered from V119 on 2026-11-14: version 119 was already taken by
-- V119__loyalty_accepted_benefit_parts.sql (006, 2026-11-12), so Flyway refused to start with
-- "Found more than one migration with version 119" and every test needing the database failed.
-- This file is the later of the two and had never been applied anywhere, so it is the one that moved.

ALTER TABLE temporary_image_references
    DROP FOREIGN KEY fk_tir_request;
ALTER TABLE temporary_image_references
    ADD CONSTRAINT fk_tir_design_request FOREIGN KEY (design_request_id) REFERENCES design_requests (id) ON DELETE CASCADE;

ALTER TABLE design_analysis_results
    DROP FOREIGN KEY fk_dar_request;
ALTER TABLE design_analysis_results
    ADD CONSTRAINT fk_dar_design_request FOREIGN KEY (design_request_id) REFERENCES design_requests (id) ON DELETE CASCADE;

ALTER TABLE human_review_decisions
    DROP FOREIGN KEY fk_hrd_request;
ALTER TABLE human_review_decisions
    ADD CONSTRAINT fk_hrd_design_request FOREIGN KEY (design_request_id) REFERENCES design_requests (id) ON DELETE CASCADE;
