-- A workshop review is about one session, but a customer deciding on a package needs the reviews of that package, and the
-- public list must say which package a review belongs to. The package of the attended ticket's booking is stored with the
-- review when it is written (the id to filter by, the name as it was at that time). Retail reviews have none.
ALTER TABLE reviews
    ADD COLUMN package_id BIGINT NULL,
    ADD COLUMN package_name VARCHAR(255) NULL;

CREATE INDEX idx_reviews_package_status_created ON reviews (package_id, status, created_at);

-- Reviews written before this column existed: take the package from the ticket's booking.
UPDATE reviews r
    JOIN attendance_tickets t ON t.reference = r.source_reference
    JOIN workshop_bookings b ON b.booking_code = t.booking_reference
SET r.package_id = b.package_id,
    r.package_name = b.package_name_snapshot
WHERE r.source_type = 'WORKSHOP_ATTENDANCE'
  AND r.package_id IS NULL;
