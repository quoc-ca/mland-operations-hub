ALTER TABLE retail_checkout_quote_items
    DROP FOREIGN KEY fk_retail_quote_item_branch;

ALTER TABLE retail_checkout_quote_branches
    DROP FOREIGN KEY fk_retail_quote_branch_quote;

ALTER TABLE retail_checkout_quotes
    MODIFY COLUMN id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_quote_branches
    MODIFY COLUMN id VARCHAR(36) NOT NULL,
    MODIFY COLUMN quote_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_quote_items
    MODIFY COLUMN id VARCHAR(36) NOT NULL,
    MODIFY COLUMN quote_branch_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_quote_branches
    ADD CONSTRAINT fk_retail_quote_branch_quote
        FOREIGN KEY (quote_id) REFERENCES retail_checkout_quotes (id) ON DELETE CASCADE;

ALTER TABLE retail_checkout_quote_items
    ADD CONSTRAINT fk_retail_quote_item_branch
        FOREIGN KEY (quote_branch_id) REFERENCES retail_checkout_quote_branches (id) ON DELETE CASCADE;
