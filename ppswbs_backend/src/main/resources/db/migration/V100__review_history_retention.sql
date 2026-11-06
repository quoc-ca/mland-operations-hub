-- Preserve review history when a current review row is removed from the operational view.
-- Review history is retained by business policy; no hard-delete cascade is allowed.
ALTER TABLE review_revisions
    DROP FOREIGN KEY fk_review_revisions_review;

ALTER TABLE review_revisions
    ADD CONSTRAINT fk_review_revisions_review
        FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE RESTRICT;

ALTER TABLE review_replies
    DROP FOREIGN KEY fk_review_replies_review;

ALTER TABLE review_replies
    ADD CONSTRAINT fk_review_replies_review
        FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE RESTRICT;

ALTER TABLE review_moderation_events
    DROP FOREIGN KEY fk_review_mod_review;

ALTER TABLE review_moderation_events
    ADD CONSTRAINT fk_review_mod_review
        FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE RESTRICT;

