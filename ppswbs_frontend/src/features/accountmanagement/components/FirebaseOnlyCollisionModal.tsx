import React, { useState } from 'react';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';

interface FirebaseOnlyCollisionModalProps {
  isOpen: boolean;
  onClose: () => void;
  email: string;
  onDeleteSuccess: () => void;
}

export const FirebaseOnlyCollisionModal: React.FC<FirebaseOnlyCollisionModalProps> = ({
  isOpen,
  onClose,
  email,
  onDeleteSuccess,
}) => {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleDelete = async () => {
    setDeleting(true);
    setError(null);
    try {
      await accountManagementApiClient.deleteFirebaseOnlyCollision({
        normalizedEmail: email,
        confirmation: true,
      });
      onDeleteSuccess();
    } catch (err: any) {
      setError(err?.message || 'Không thể xóa danh tính Firebase.');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="account-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="fb-only-title">
      <div className="account-modal-container" style={{ maxWidth: '520px' }}>
        <div className="account-modal-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 id="fb-only-title" style={{ margin: 0, fontSize: '1.15rem', color: '#0f172a' }}>
            Xử lý danh tính Firebase đơn lẻ
          </h2>
          <button
            type="button"
            className="account-modal-close"
            onClick={onClose}
            aria-label="Đóng"
            style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer' }}
          >
            ×
          </button>
        </div>

        <div className="account-modal-body" style={{ padding: '1rem 0' }}>
          {error && (
            <div
              className="account-alert account-alert-error"
              role="alert"
              style={{
                backgroundColor: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#b91c1c',
                padding: '0.75rem 1rem',
                borderRadius: '6px',
                marginBottom: '1rem',
              }}
            >
              {error}
            </div>
          )}

          <div
            className="account-alert account-alert-info"
            style={{
              backgroundColor: '#f0f9ff',
              border: '1px solid #bae6fd',
              color: '#0369a1',
              padding: '0.75rem 1rem',
              borderRadius: '6px',
              fontSize: '0.875rem',
            }}
          >
            <p style={{ margin: '0 0 0.5rem 0' }}>
              Email <strong>{email}</strong> đã được ghi nhận trên hệ thống định danh Firebase, nhưng Mland không tìm thấy bất kỳ dữ liệu tài khoản nào liên kết trong cơ sở dữ liệu.
            </p>
            <p style={{ margin: 0 }}>
              Để cấp tài khoản nhân sự mới cho email này, Quản trị viên cần xác nhận xóa danh tính Firebase mồ côi này trước khi tiến hành khởi tạo.
            </p>
          </div>
        </div>

        <div className="account-modal-footer" style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
          <button
            type="button"
            className="account-btn account-btn-danger"
            onClick={handleDelete}
            disabled={deleting}
            data-testid="confirm-fb-delete-btn"
          >
            {deleting ? 'Đang xóa…' : 'Xác nhận xóa danh tính Firebase'}
          </button>

          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={onClose}
          >
            Hủy bỏ
          </button>
        </div>
      </div>
    </div>
  );
};
