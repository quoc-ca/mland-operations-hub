INSERT INTO notification_request (
  id, business_event_reference, template_code, template_version_id, recipient_role,
  recipient_address_fingerprint, recipient_address_masked, recipient_snapshot, business_data_snapshot,
  delivery_state, attempt_count, next_attempt_at, created_at, updated_at
) VALUES (
  1008, 'BK-20261106-008', 'BOOKING_CONFIRMED', 1, 'CUSTOMER',
  'fp1008', 'r***y@example.com', '{"email":"retry@example.com"}', '{"bookingCode":"BK-20261106-008"}',
  'RETRY_SCHEDULED', 1, DATE_ADD(NOW(), INTERVAL 1 HOUR), NOW(), NOW()
);
