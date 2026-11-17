-- Feature 002 FR-061 (Session 2026-11-16): branch scope of the submitter, captured when a price proposal or a
-- product review is submitted, so history is not rewritten when a Staff member's branches change later.
-- JSON value data: {"all": true} for a Manager, {"all": false, "branches": [{"locationId": 12, "name": "..."}]}
-- for Staff. NULL for drafts, rows submitted before this migration and system actors; old rows are not backfilled.
ALTER TABLE catalogue_price_proposals
    ADD COLUMN submitter_branches JSON NULL;

ALTER TABLE catalogue_product_reviews
    ADD COLUMN submitter_branches JSON NULL;
