-- V80 changes the referenced checkout id and recreates this foreign key.
ALTER TABLE retail_checkout_holds
    DROP FOREIGN KEY fk_retail_checkout_holds_checkout;
