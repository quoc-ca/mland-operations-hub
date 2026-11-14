-- Align review rating column types from TINYINT UNSIGNED to INT for Hibernate Integer mapping
ALTER TABLE reviews
    MODIFY COLUMN rating INT NOT NULL;

ALTER TABLE review_revisions
    MODIFY COLUMN rating INT NOT NULL;
