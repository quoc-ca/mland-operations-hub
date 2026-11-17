CREATE TABLE workshop_booking_package_policy_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_reference VARCHAR(64) NOT NULL,
    package_reference VARCHAR(64) NOT NULL,
    required_session_count INT NOT NULL,
    min_participants INT NOT NULL,
    max_participants INT NOT NULL,
    same_location_required BOOLEAN NOT NULL,
    minimum_same_day_slot_gap INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workshop_booking_policy_booking (booking_reference),
    CONSTRAINT chk_workshop_booking_policy_sessions CHECK (required_session_count > 0),
    CONSTRAINT chk_workshop_booking_policy_participants CHECK (min_participants > 0 AND max_participants >= min_participants),
    CONSTRAINT chk_workshop_booking_policy_slot_gap CHECK (minimum_same_day_slot_gap >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
