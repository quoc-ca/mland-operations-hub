-- Flyway Migration V119: Align Feature 004 dependent tables with independent design_requests table
-- Resolves BUG-E2E-0005: foreign keys were still referencing legacy booking_design_requests

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
