import React, { useEffect, useRef, useState } from 'react';
import { exchangeGroupMagicLinkToken, WorkshopApiError } from '../api/workshops';
import { GroupDetailCard } from './GroupDetailCard';
import { parseMagicLinkToken } from './magicLinkToken';

/**
 * The group Magic Link (003.1 FR-042, FR-048). The token is read strictly from the URL fragment, exchanged once for a
 * read-only group projection and never persisted or logged; the address is scrubbed immediately. Expired, revoked and
 * unknown tokens answer the same 404. A recipient's QR email is not a Magic Link and gives no group view.
 */
export function WorkshopAccessPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [group, setGroup] = useState(null);
  // The token and its one exchange live for the page: the address is cleaned at once, so a second run of this effect
  // (React StrictMode in development) must not read the address again or exchange the token twice.
  const tokenRef = useRef(null);
  const exchangeRef = useRef(null);

  useEffect(() => {
    if (tokenRef.current === null) {
      tokenRef.current = parseMagicLinkToken(window.location.hash) || '';
    }
    const token = tokenRef.current;

    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, '', '/workshop-booking/access');
    }

    if (!token) {
      setLoading(false);
      setError('Không tìm thấy token bảo mật trong liên kết Magic Link. Vui lòng kiểm tra lại liên kết trong email của bạn.');
      return undefined;
    }

    if (!exchangeRef.current) exchangeRef.current = exchangeGroupMagicLinkToken(token);
    let active = true;
    exchangeRef.current
      .then(data => { if (active) setGroup(data); })
      .catch(err => {
        if (!active) return;
        if (err instanceof WorkshopApiError) {
          if (err.status === 404 || err.code === 'MAGIC_LINK_NOT_FOUND' || err.code === 'TOKEN_NOT_FOUND') {
            setError('Liên kết truy cập Magic Link không tồn tại hoặc đã hết hạn.');
          } else if (err.status === 401 || err.status === 403) {
            setError('Liên kết truy cập Magic Link đã bị thu hồi hoặc đã bị thay đổi.');
          } else {
            setError(err.detail || 'Không thể xác thực liên kết truy cập.');
          }
        } else {
          setError(err?.message || 'Có lỗi xảy ra khi xác thực liên kết Magic Link.');
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <main className="container public-page">
      <p className="eyebrow">XÁC THỰC MAGIC LINK</p>
      <h1>Truy cập nhóm đặt chỗ bảo mật</h1>

      {loading && <div className="empty-state"><p>Đang xác thực liên kết truy cập an toàn...</p></div>}

      {error && !loading && (
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00', marginBottom: '16px' }}>{error}</p>
          <p className="muted" style={{ fontSize: '14px', lineHeight: '1.6' }}>
            Bạn có thể tra cứu nhóm đặt chỗ bằng mã nhóm và email hoặc số điện thoại đã đặt tại trang Tra cứu.
          </p>
          <div style={{ marginTop: '20px' }}>
            <a className="button primary" href="/workshop-booking/lookup">Chuyển tới trang Tra cứu</a>
          </div>
        </div>
      )}

      {!loading && !error && group && (
        <>
          <GroupDetailCard group={group} authorization={null} allowRelease={false} allowRecipientChange={false} />
          <div className="actions" style={{ marginTop: '24px' }}>
            <a className="button outline" href="/workshop-booking/lookup">Quản lý tại trang Tra cứu</a>
            <a className="button primary" href="/">Về trang chủ</a>
          </div>
        </>
      )}
    </main>
  );
}
