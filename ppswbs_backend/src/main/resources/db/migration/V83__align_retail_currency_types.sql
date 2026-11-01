ALTER TABLE retail_checkout_quotes
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE retail_grouped_checkouts
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;
