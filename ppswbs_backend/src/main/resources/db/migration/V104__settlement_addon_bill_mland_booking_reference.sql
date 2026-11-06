-- A shop add-on bill of an OTA booking also records the Mland booking that holds the tickets, so a Staff member can be
-- authorized by their assignment to that booking's sessions (owner decision 2026-11-06). Legacy bills carry none and
-- stay Manager-only for Staff.
ALTER TABLE settlement_addon_bills
    ADD COLUMN mland_booking_reference VARCHAR(128) NULL;
