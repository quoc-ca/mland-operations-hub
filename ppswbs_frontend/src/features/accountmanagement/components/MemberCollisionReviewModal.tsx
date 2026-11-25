import React, { useState } from 'react';
import { ReplacementReviewResponse, accountManagementApiClient } from '../api/AccountManagementApiClient';
import { useT, useFormat } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';

interface MemberCollisionReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  review: ReplacementReviewResponse | null;
  onRetireSuccess?: () => void;
}

export const MemberCollisionReviewModal: React.FC<MemberCollisionReviewModalProps> = ({
  isOpen,
  onClose,
  review,
  onRetireSuccess,
}) => {
  const t = useT(accountMessages);
  const fmt = useFormat();
  const [retiring, setRetiring] = useState(false);
  const [retireError, setRetireError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  if (!isOpen || !review) return null;

  const isDemo = review.dataOrigin === 'DEMO' || review.obligationStatus === 'UNAVAILABLE';

  const handleExportJson = async () => {
    setExporting(true);
    try {
      let exportData = review;
      try {
        exportData = await accountManagementApiClient.exportMemberReplacement(review.accountId);
      } catch (err) {
        // Fallback to current in-memory review if export endpoint not mocked
        exportData = review;
      }
      const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(exportData, null, 2));
      const downloadAnchor = document.createElement('a');
      downloadAnchor.setAttribute('href', dataStr);
      downloadAnchor.setAttribute('download', `member-replacement-ACC-${review.accountId}.json`);
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
    } finally {
      setExporting(false);
    }
  };

  const handleRetire = async () => {
    setRetiring(true);
    setRetireError(null);
    try {
      if (isDemo) {
        // Per FR-049/050 and 2026-11-25 approval, retirement is denied while source is DEMO/UNAVAILABLE
        throw new Error(
          'Nguồn dữ liệu là DEMO/UNAVAILABLE hoặc chưa có hàng rào chấp thuận thay thế MEMBER; không thể thay thế tài khoản hoặc giải phóng email.'
        );
      }
      await accountManagementApiClient.retireMemberForReplacement(review.accountId, {
        reviewVersion: review.reviewVersion,
        sourceVersion: review.sourceVersion,
        normalizedEmail: review.loginEmail,
        confirmedTargetAccountId: review.accountId,
      });
      if (onRetireSuccess) onRetireSuccess();
    } catch (err: any) {
      setRetireError(err?.message || 'Không thể thay thế tài khoản Member.');
    } finally {
      setRetiring(false);
    }
  };

  return (
    <div className="account-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="collision-review-title">
      <div className="account-modal-container" style={{ maxWidth: '800px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="account-modal-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 id="collision-review-title" style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a' }}>
            Đối soát tài khoản MEMBER xung đột email
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
          {/* Prominent DEMO / UNAVAILABLE Badge per FR-050 & 2026-11-25 Scoped Approval */}
          {isDemo && (
            <div
              className="account-alert account-alert-warning"
              style={{
                backgroundColor: '#fffbeb',
                border: '1px solid #fde68a',
                color: '#92400e',
                padding: '0.75rem 1rem',
                borderRadius: '6px',
                marginBottom: '1rem',
                fontSize: '0.9rem',
              }}
              data-testid="demo-preview-badge"
            >
              <strong>[DEMO / KHÔNG KHẢ DỤNG] Bản ghi xem thử</strong>
              <p style={{ margin: '0.25rem 0 0 0', fontSize: '0.85rem' }}>
                Hệ thống đang hiển thị dữ liệu mẫu xem thử của tài khoản Member. Theo quyết định phê duyệt 2026-11-25,
                việc thay thế tài khoản Member và giải phóng email bị từ chối (409) cho đến khi có nguồn dữ liệu trực tiếp
                và hàng rào chấp thuận được phê duyệt.
              </p>
            </div>
          )}

          {retireError && (
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
              {retireError}
            </div>
          )}

          {/* Member Basic Profile */}
          <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
            <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#1e293b' }}>Thông tin tài khoản MEMBER</h3>
            <table className="account-table" style={{ width: '100%', fontSize: '0.875rem' }}>
              <tbody>
                <tr>
                  <th style={{ width: '180px', textAlign: 'left', color: '#64748b' }}>Mã tài khoản:</th>
                  <td><strong>ACC-{review.accountId}</strong></td>
                </tr>
                <tr>
                  <th style={{ textAlign: 'left', color: '#64748b' }}>Email định danh:</th>
                  <td>{review.loginEmail}</td>
                </tr>
                <tr>
                  <th style={{ textAlign: 'left', color: '#64748b' }}>Họ và tên:</th>
                  <td>{review.fullName || '—'}</td>
                </tr>
                <tr>
                  <th style={{ textAlign: 'left', color: '#64748b' }}>Số điện thoại:</th>
                  <td>{review.phone || '—'}</td>
                </tr>
                <tr>
                  <th style={{ textAlign: 'left', color: '#64748b' }}>Trạng thái tài khoản:</th>
                  <td>
                    <span className="account-badge account-badge-readonly">{review.status}</span>
                  </td>
                </tr>
                <tr>
                  <th style={{ textAlign: 'left', color: '#64748b' }}>Trạng thái nghĩa vụ:</th>
                  <td>
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: '4px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        backgroundColor: review.obligationStatus === 'CLEAR' ? '#dcfce7' : '#fee2e2',
                        color: review.obligationStatus === 'CLEAR' ? '#166534' : '#991b1b',
                      }}
                    >
                      {review.obligationStatus}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Sổ địa chỉ */}
          <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
            <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#1e293b' }}>
              Địa chỉ nhận hàng ({review.addresses?.length || 0})
            </h3>
            {review.addresses && review.addresses.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.875rem' }}>
                {review.addresses.map(addr => (
                  <li key={addr.id} style={{ marginBottom: '0.25rem' }}>
                    <strong>{addr.receiverName}</strong> ({addr.phone}) — {addr.detailAddress}{' '}
                    {addr.isDefault && <span style={{ color: '#2563eb', fontWeight: 600 }}>[Mặc định]</span>}
                  </li>
                ))}
              </ul>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>Không có địa chỉ lưu trữ.</p>
            )}
          </div>

          {/* Giỏ hàng bán lẻ lưu trên server (Server-stored Retail Cart) */}
          <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
            <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#1e293b' }}>
              Giỏ hàng bán lẻ trên server ({review.serverStoredRetailCart?.length || 0})
            </h3>
            {review.serverStoredRetailCart && review.serverStoredRetailCart.length > 0 ? (
              <table className="account-table" style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc' }}>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Sản phẩm</th>
                    <th style={{ textAlign: 'center', padding: '4px 8px' }}>Số lượng</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Cập nhật</th>
                  </tr>
                </thead>
                <tbody>
                  {review.serverStoredRetailCart.map((item, idx) => (
                    <tr key={idx}>
                      <td style={{ padding: '4px 8px' }}>{item.displayName}</td>
                      <td style={{ textAlign: 'center', padding: '4px 8px' }}>{item.quantity}</td>
                      <td style={{ padding: '4px 8px' }}>{fmt.dateTime(item.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>Giỏ hàng bán lẻ trống.</p>
            )}
          </div>

          {/* Lịch sử mua hàng bán lẻ */}
          <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
            <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#1e293b' }}>
              Lịch sử đơn hàng bán lẻ ({review.retailPurchaseHistory?.length || 0})
            </h3>
            {review.retailPurchaseHistory && review.retailPurchaseHistory.length > 0 ? (
              <table className="account-table" style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc' }}>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Mã đơn</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Ngày tạo</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Thanh toán</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Giao hàng</th>
                  </tr>
                </thead>
                <tbody>
                  {review.retailPurchaseHistory.map((order, idx) => (
                    <tr key={idx}>
                      <td style={{ padding: '4px 8px' }}>{order.displayCode}</td>
                      <td style={{ padding: '4px 8px' }}>{fmt.dateTime(order.acceptedAt)}</td>
                      <td style={{ padding: '4px 8px' }}>{order.paymentState}</td>
                      <td style={{ padding: '4px 8px' }}>{order.fulfilmentState}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>Chưa có đơn hàng bán lẻ.</p>
            )}
          </div>

          {/* Lịch sử đặt lịch Workshop */}
          <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
            <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#1e293b' }}>
              Lịch sử đặt lịch Workshop ({review.workshopBookingHistory?.length || 0})
            </h3>
            {review.workshopBookingHistory && review.workshopBookingHistory.length > 0 ? (
              <table className="account-table" style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc' }}>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Mã lịch</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Ngày & Ca</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Cơ sở</th>
                    <th style={{ textAlign: 'center', padding: '4px 8px' }}>Số vé</th>
                    <th style={{ textAlign: 'left', padding: '4px 8px' }}>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {review.workshopBookingHistory.map((b, idx) => (
                    <tr key={idx}>
                      <td style={{ padding: '4px 8px' }}>{b.displayCode}</td>
                      <td style={{ padding: '4px 8px' }}>{b.workshopDate} ({b.sessionTime})</td>
                      <td style={{ padding: '4px 8px' }}>{b.locationName}</td>
                      <td style={{ textAlign: 'center', padding: '4px 8px' }}>{b.seatCount}</td>
                      <td style={{ padding: '4px 8px' }}>{b.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>Chưa có lịch workshop.</p>
            )}
          </div>

          {/* Nghĩa vụ đang chặn (Obligation Blockers) */}
          {review.obligationBlockers && review.obligationBlockers.length > 0 && (
            <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem', borderColor: '#fca5a5' }}>
              <h3 style={{ fontSize: '1rem', margin: '0 0 0.5rem 0', color: '#b91c1c' }}>Nghĩa vụ đang chặn</h3>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#7f1d1d' }}>
                {review.obligationBlockers.map((blk, idx) => (
                  <li key={idx}>
                    <strong>[{blk.reference}]</strong> {blk.message} ({blk.reasonCode})
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <div className="account-modal-footer" style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={handleExportJson}
            disabled={exporting}
            data-testid="export-json-btn"
          >
            {exporting ? 'Đang xuất…' : 'Xuất dữ liệu (JSON)'}
          </button>

          <button
            type="button"
            className="account-btn account-btn-danger"
            onClick={handleRetire}
            disabled={retiring}
            title={isDemo ? 'Thay thế bị từ chối khi dữ liệu là DEMO/UNAVAILABLE' : undefined}
            data-testid="retire-member-btn"
          >
            {retiring ? 'Đang xử lý…' : 'Thay thế tài khoản Member'}
          </button>

          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};
