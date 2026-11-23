import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useCurrentAccount } from '../hooks/useCurrentAccount';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useHttp } from '../../../shared/api/useHttp';
import { useLanguage } from '../../../shared/i18n/I18nProvider';
import '../accountmanagement.css';

type Shipment = { status?: string; createdAt?: string };
type TodayCounts = { today: number; waitingPickup: number; delivering: number };

const VN_OFFSET_MS = 7 * 3_600_000;
const vnDay = (value: string | number) => new Date(new Date(value).getTime() + VN_OFFSET_MS).toISOString().slice(0, 10);

/** Today's GHTK work for the carrier home page (FE-08, Session 2026-11-23): new shipments today (Vietnam time),
 * shipments waiting to be picked up and shipments on the way. */
export function carrierTodayCounts(items: Shipment[], now = Date.now()): TodayCounts {
  const today = vnDay(now);
  return {
    today: items.filter(item => item.createdAt && vnDay(item.createdAt) === today).length,
    waitingPickup: items.filter(item => item.status === 'READY_FOR_CARRIER').length,
    delivering: items.filter(item => item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT').length,
  };
}

function useCarrierToday(enabled: boolean): TodayCounts | null {
  const request = useHttp() as unknown as (path: string, options?: { query?: Record<string, unknown> }) => Promise<{ items?: Shipment[]; totalPages?: number }>;
  const [counts, setCounts] = useState<TodayCounts | null>(null);
  useEffect(() => {
    if (!enabled) return undefined;
    let live = true;
    (async () => {
      const items: Shipment[] = [];
      for (let page = 0; page < 10; page += 1) {
        const result = await request('/api/v1/carrier/shipments', { query: { page, size: 200 } }); // eslint-disable-line no-await-in-loop
        items.push(...(result?.items || []));
        if (!result || page + 1 >= (result.totalPages || 0)) break;
      }
      if (live) setCounts(carrierTodayCounts(items));
    })().catch(() => { /* no notice when the shipments cannot be read */ });
    return () => { live = false; };
  }, [enabled, request]);
  return counts;
}

export const CarrierPortalPage: React.FC = () => {
  const { account, status: accountStatus, error } = useCurrentAccount();
  const { signOut } = useAuth();
  const { language } = useLanguage();
  const t = (vi: string, en: string) => (language === 'en' ? en : vi);
  const today = useCarrierToday(account?.role === 'CARRIER' && account?.status !== 'SUSPENDED');

  if (accountStatus === 'loading') {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>{t('Đang xác thực thông tin tài khoản đối tác vận chuyển…', 'Checking the carrier partner account…')}</p>
        </div>
      </div>
    );
  }

  // Access defense (FR-003, FR-026, FR-027): Ensure role is CARRIER (or ADMIN managing carrier)
  if (account && account.role !== 'CARRIER' && account.role !== 'ADMIN') {
    return (
      <div className="account-page-container">
        <div className="account-alert account-alert-error" role="alert">
          <span>{t('Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển.', 'Your account cannot access the carrier partner portal.')}</span>
        </div>
        <div className="account-card" style={{ marginTop: '1rem', textAlign: 'center', padding: '2rem' }}>
          <Link to="/" className="account-btn account-btn-primary">
            {t('Quay lại trang chủ', 'Back to home')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page-container">
      <header className="account-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h1 className="account-page-title" style={{ margin: 0 }}>
            {t('Cổng đối tác vận chuyển (GHTK)', 'Carrier partner portal (GHTK)')}
          </h1>
          <span className="account-badge account-badge-active">
            CARRIER · {account?.status || 'ACTIVE'}
          </span>
        </div>
        <p className="account-page-description" style={{ marginTop: '0.5rem' }}>
          {t('Hệ thống quản lý dành riêng cho Đối tác giao nhận vận chuyển GHTK. Quản trị phân luồng giao nhận và tiến độ vận đơn.', 'The portal for the GHTK delivery partner: manage pickups, deliveries and shipment progress.')}
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error.message || t('Lỗi khi đồng bộ dữ liệu tài khoản đối tác.', 'Could not load the partner account.')}</span>
        </div>
      )}

      {account?.status === 'SUSPENDED' && (
        <div className="account-alert account-alert-error" role="alert">
          <strong>{t('Tài khoản đang bị tạm khóa (SUSPENDED).', 'This account is suspended (SUSPENDED).')}</strong> {t('Vui lòng liên hệ Quản trị viên để được hỗ trợ.', 'Please contact an administrator.')}
        </div>
      )}

      <div className="account-card" style={{ marginBottom: '1.5rem' }}>
        <h2 className="account-card-title">{t('Thông tin định danh đối tác', 'Partner identity')}</h2>
        <table className="account-table">
          <tbody>
            <tr>
              <th style={{ width: '220px' }}>{t('Tên đối tác / Đại diện:', 'Partner / representative:')}</th>
              <td><strong>{account?.email?.split('@')[0] || t('Đối tác vận chuyển (GHTK)', 'Carrier partner (GHTK)')}</strong></td>
            </tr>
            <tr>
              <th>{t('Email đăng nhập:', 'Sign-in email:')}</th>
              <td>{account?.email || 'ghtk.test@mland.vn'}</td>
            </tr>
            <tr>
              <th>{t('Vai trò hệ thống:', 'System role:')}</th>
              <td>
                <span className="account-badge account-badge-readonly">CARRIER</span>
              </td>
            </tr>
            <tr>
              <th>{t('Phạm vi cơ sở:', 'Branch scope:')}</th>
              <td>{t('Toàn quốc (Không giới hạn theo cơ sở đơn lẻ)', 'Nationwide (not limited to one branch)')}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem' }}>
        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              {t('🚚 Đơn vận chuyển cần giao', '🚚 Shipments to deliver')}
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              {t('Tiếp nhận các đơn hàng trang sức bán lẻ đã chuẩn bị sẵn sàng và bàn giao cho GHTK vận chuyển đến khách hàng.', 'Pick up packed retail jewellery orders and deliver them to customers with GHTK.')}
            </p>
            {today && (
              <div className="carrier-today" role="status">
                <strong>{t(`Hôm nay có ${today.today} vận đơn mới`, `${today.today} new shipments today`)}</strong>
                <span>{t(`${today.waitingPickup} đơn chờ lấy hàng · ${today.delivering} đơn đang giao`, `${today.waitingPickup} waiting for pickup · ${today.delivering} on the way`)}</span>
              </div>
            )}
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/carrier/orders"
              className="account-btn account-btn-primary"
              style={{ display: 'inline-block' }}
            >
              {t('Xem danh sách vận đơn →', 'View shipments →')}
            </Link>
          </div>
        </div>

        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              {t('🔑 Bảo mật & Mật khẩu', '🔑 Security & password')}
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              {t('Chủ động thay đổi mật khẩu định kỳ để bảo đảm an toàn truy cập tài khoản đối tác vận chuyển.', 'Change your password regularly to keep the carrier account safe.')}
            </p>
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/security/password"
              className="account-btn account-btn-secondary"
              style={{ display: 'inline-block' }}
            >
              {t('Đổi mật khẩu tài khoản →', 'Change password →')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CarrierPortalPage;
