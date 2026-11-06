-- V92__workshop_package_foundry_working_day_rule.sql

ALTER TABLE workshop_packages
    ADD COLUMN required_foundry_working_days_between_sessions INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_workshop_packages_foundry_days
        CHECK (required_foundry_working_days_between_sessions >= 0);

ALTER TABLE workshop_booking_package_policy_snapshots
    ADD COLUMN required_foundry_working_days_between_sessions INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_workshop_booking_policy_foundry_days
        CHECK (required_foundry_working_days_between_sessions >= 0);
