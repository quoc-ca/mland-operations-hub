-- ==============================================================================
-- V159: Manager alert template for a workshop session still CANCELLATION_REQUESTED
-- 24 hours after its scheduled end (Feature 003.2, T133, FR-010a, F06-email).
-- Approved by Hồ Hoàng Quốc (owner of Feature 003) 2026-11-16; the placeholder
-- unresolvedTicketCount was removed the same day (no count facade in 003.3).
-- ==============================================================================

INSERT INTO notification_template_definition (code, name, description, owning_module, lifecycle_status, allowed_placeholders, created_at, updated_at)
VALUES (
    'WORKSHOP_CANCELLATION_UNRESOLVED_ALERT',
    'Cảnh báo buổi workshop bị hủy chưa xử lý xong',
    'Gửi Manager một lần khi buổi workshop vẫn ở trạng thái chờ hủy sau 24 giờ kể từ giờ kết thúc theo lịch',
    'workshopoperations',
    'ACTIVE',
    '["sessionReference", "sessionName", "locationName", "scheduledEndAt", "hoursSinceScheduledEnd"]',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO notification_template_version (template_definition_id, version_number, subject_template, html_template, plain_text_template, status, created_by, activated_by, activated_at, created_at, updated_at)
SELECT id, 1,
    '[Mland] Buổi workshop {{sessionName}} vẫn còn vé chưa xử lý sau khi bị hủy',
    '<div style="font-family: sans-serif; padding: 20px;"><p>Kính gửi Quản lý chi nhánh {{locationName}},</p><p>Buổi workshop <strong>{{sessionName}}</strong> (mã {{sessionReference}}) kết thúc theo lịch lúc {{scheduledEndAt}} nhưng vẫn ở trạng thái chờ hủy sau {{hoursSinceScheduledEnd}} giờ.</p><p>Vẫn còn vé chưa được giải quyết (đổi buổi hoặc mở yêu cầu hoàn tiền). Vui lòng kiểm tra các vé của buổi này trong màn hình quản lý và xử lý.</p><p>Đây là cảnh báo gửi một lần cho mỗi buổi.</p><p>Trân trọng,<br>Hệ thống Mland</p></div>',
    'Kính gửi Quản lý chi nhánh {{locationName}}, Buổi workshop {{sessionName}} (mã {{sessionReference}}) kết thúc theo lịch lúc {{scheduledEndAt}} nhưng vẫn ở trạng thái chờ hủy sau {{hoursSinceScheduledEnd}} giờ. Vẫn còn vé chưa được giải quyết (đổi buổi hoặc mở yêu cầu hoàn tiền). Vui lòng kiểm tra các vé của buổi này trong màn hình quản lý và xử lý. Đây là cảnh báo gửi một lần cho mỗi buổi. Trân trọng, Hệ thống Mland.',
    'ACTIVE',
    'system',
    'system',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM notification_template_definition
WHERE code = 'WORKSHOP_CANCELLATION_UNRESOLVED_ALERT';
