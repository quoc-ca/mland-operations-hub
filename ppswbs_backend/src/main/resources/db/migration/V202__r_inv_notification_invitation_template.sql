-- V202__r_inv_notification_invitation_template.sql
-- Revision R-INV: Internal Account Setup Invitation Template Registration (T038)

INSERT INTO notification_template_definition (
    code,
    name,
    description,
    owning_module,
    lifecycle_status,
    allowed_placeholders,
    created_at,
    updated_at
) VALUES (
    'INTERNAL_ACCOUNT_INVITATION',
    'Internal Account Setup Invitation',
    'Setup invitation email with single-use setup link for Staff and Manager',
    'accountmanagement',
    'ACTIVE',
    'recipient_name,setup_url,expiry_time',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
) ON DUPLICATE KEY UPDATE lifecycle_status = 'ACTIVE';

INSERT INTO notification_template_version (
    template_definition_id,
    version_number,
    subject_template,
    html_template,
    plain_text_template,
    status,
    created_by,
    activated_by,
    activated_at,
    created_at,
    updated_at
)
SELECT
    id,
    1,
    '[Mland] Thiết lập tài khoản nội bộ - Hết hạn vào {{expiry_time}}',
    '<p>Kính gửi <strong>{{recipient_name}}</strong>,</p><p>Bạn đã được tạo tài khoản nội bộ tại Mland. Vui lòng truy cập liên kết sau để hoàn tất thiết lập tài khoản:</p><p><a href="{{setup_url}}">{{setup_url}}</a></p><p>Liên kết này có hiệu lực trong 48 giờ kể từ khi được tạo và sẽ hết hạn vào <strong>{{expiry_time}}</strong>.</p><p>Nếu bạn không yêu cầu tài khoản này, vui lòng bỏ qua email.</p>',
    'Kính gửi {{recipient_name}},\n\nBạn đã được tạo tài khoản nội bộ tại Mland. Vui lòng truy cập liên kết sau để hoàn tất thiết lập tài khoản:\n{{setup_url}}\n\nLiên kết này có hiệu lực trong 48 giờ kể từ khi được tạo và sẽ hết hạn vào {{expiry_time}}.\n\nNếu bạn không yêu cầu tài khoản này, vui lòng bỏ qua email.',
    'ACTIVE',
    'system',
    'system',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM notification_template_definition
WHERE code = 'INTERNAL_ACCOUNT_INVITATION'
ON DUPLICATE KEY UPDATE status = 'ACTIVE';
