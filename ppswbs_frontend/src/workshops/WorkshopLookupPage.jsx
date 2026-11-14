import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  getOwnedBookingGroup,
  listMemberBookingGroups,
  lookupGuestBookingGroup,
  WorkshopApiError,
} from '../api/workshops';
import { GroupDetailCard } from './GroupDetailCard';
import { MyBookingGroupsList } from './MyBookingGroupsList';
import { ReviewCta } from './ReviewCta';
import { useBookingIdentity } from './useBookingIdentity';
import { useReviewManagementApi } from '../features/reviewmanagement/api/useReviewManagementApi';
import './figmaPublicShell.css';

/**
 * Lookup and management of a booking group (003.1 FR-042, FR-048, FR-049). A signed-in Member reads their own groups
 * from `GET /members/me/workshop-booking-groups` and opens one through `GET /workshop-booking-groups/{groupCode}`; a
 * Guest proves the group with its code and exactly one stored contact value through
 * `POST /workshop-booking-group-lookups`, so contact proof never travels in a URL.
 */
export function WorkshopLookupPage() {
  const { authStatus, mode: lookupMode, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const [groupCode, setGroupCode] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [contactPhone, setContactPhone] = useState('');

  const [myGroups, setMyGroups] = useState(null);
  const [myGroupsLoading, setMyGroupsLoading] = useState(false);
  const [myGroupsError, setMyGroupsError] = useState(null);
  const loadMyGroups = useRef(null);
  loadMyGroups.current = async () => {
    setMyGroupsLoading(true);
    setMyGroupsError(null);
    try {
      const authorization = await memberAuthorization();
      if (!authorization) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      setMyGroups(await listMemberBookingGroups({ authorization }));
    } catch {
      setMyGroups(null);
      setMyGroupsError('Không tải được danh sách đặt chỗ của bạn.');
    } finally {
      setMyGroupsLoading(false);
    }
  };
  useEffect(() => {
    if (lookupMode !== 'MEMBER') { setMyGroups(null); setMyGroupsError(null); return; }
    loadMyGroups.current();
  }, [lookupMode]);

  // Which of the Member's sessions can be reviewed comes only from Feature 011; nothing is inferred here.
  const reviewApi = useReviewManagementApi();
  const navigate = useNavigate();
  const [reviewSources, setReviewSources] = useState(null);
  useEffect(() => {
    if (lookupMode !== 'MEMBER') { setReviewSources(null); return undefined; }
    let active = true;
    reviewApi.listEligibleSources()
      .then(result => { if (active) setReviewSources(Array.isArray(result?.items) ? result.items : []); })
      .catch(() => { if (active) setReviewSources(null); });
    return () => { active = false; };
  }, [lookupMode, reviewApi]);
  const startReview = source => navigate('/reviews/new', { state: { source } });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [group, setGroup] = useState(null);
  const detailsRef = useRef(null);

  const [searchParams] = useSearchParams();
  const requestedCode = searchParams.get('code');
  const openedRequestedCode = useRef(false);

  const openGroup = next => {
    setError(null);
    setGroupCode(next.groupCode);
    setGroup(next);
    setTimeout(() => detailsRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }), 0);
  };

  useEffect(() => {
    if (!requestedCode || openedRequestedCode.current || !Array.isArray(myGroups)) return;
    const match = myGroups.find(item => item.groupCode === requestedCode);
    openedRequestedCode.current = true;
    if (match) openGroup(match);
  }, [requestedCode, myGroups]);

  /** Reads the group again through the route the current actor is allowed to use. */
  const refresh = async () => {
    const code = group?.groupCode;
    if (!code) return;
    try {
      let fresh;
      if (lookupMode === 'MEMBER') {
        const authorization = await memberAuthorization();
        fresh = await getOwnedBookingGroup(code, { authorization });
        loadMyGroups.current();
      } else {
        const hasEmail = Boolean(contactEmail.trim());
        fresh = await lookupGuestBookingGroup({
          groupCode: code,
          contactEmail: hasEmail ? contactEmail.trim() : undefined,
          contactPhone: hasEmail ? undefined : contactPhone.trim(),
        });
      }
      if (fresh) setGroup(fresh);
    } catch {
      // The card keeps what it has; the actor can ask again.
    }
  };

  const handleLookup = async event => {
    event.preventDefault();
    setError(null);
    if (!groupCode.trim()) {
      setError('Vui lòng nhập mã nhóm đặt chỗ.');
      return;
    }
    if (lookupMode === null) {
      setError('Không thể xác minh vai trò tài khoản để tra cứu đặt chỗ.');
      return;
    }
    setLoading(true);
    try {
      let result;
      if (lookupMode === 'MEMBER') {
        const authorization = await memberAuthorization();
        if (!authorization) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        result = await getOwnedBookingGroup(groupCode.trim(), { authorization });
      } else {
        const hasEmail = Boolean(contactEmail.trim());
        const hasPhone = Boolean(contactPhone.trim());
        if ((hasEmail && hasPhone) || (!hasEmail && !hasPhone)) {
          setError('Khách vãng lai vui lòng cung cấp đúng một trong hai: Email HOẶC Số điện thoại đã đặt.');
          setLoading(false);
          return;
        }
        result = await lookupGuestBookingGroup({
          groupCode: groupCode.trim(),
          contactEmail: hasEmail ? contactEmail.trim() : undefined,
          contactPhone: hasPhone ? contactPhone.trim() : undefined,
        });
      }
      setGroup(result);
    } catch (err) {
      setGroup(null);
      if (err instanceof WorkshopApiError) {
        if (err.status === 404 || err.code === 'BOOKING_NOT_FOUND') {
          setError('Không tìm thấy nhóm đặt chỗ phù hợp với mã và thông tin liên hệ đã cung cấp.');
        } else if (err.status === 401 || err.status === 403) {
          setError('Bạn không có quyền xem nhóm đặt chỗ này.');
        } else {
          setError(err.detail || 'Lỗi tra cứu nhóm đặt chỗ.');
        }
      } else {
        setError(err?.message || 'Có lỗi xảy ra khi tra cứu.');
      }
    } finally {
      setLoading(false);
    }
  };

  const guestProof = {
    contactEmail: contactEmail.trim() || undefined,
    contactPhone: contactPhone.trim() || undefined,
  };
  const [cardAuthorization, setCardAuthorization] = useState(null);
  useEffect(() => {
    let active = true;
    if (lookupMode !== 'MEMBER') { setCardAuthorization(null); return () => { active = false; }; }
    memberAuthorization().then(value => { if (active) setCardAuthorization(value); });
    return () => { active = false; };
  }, [lookupMode, group?.groupCode]);

  return (
    <main className="public-page figma-public-page">
      <p className="eyebrow">TRA CỨU &amp; QUẢN LÝ ĐẶT CHỖ</p>
      <h1>Tra cứu nhóm đặt chỗ Workshop</h1>

      <div role="status" style={{ marginTop: '20px', marginBottom: '24px', padding: '12px 16px', border: '1px solid #dcdfe6', background: '#f8fafc', color: '#002967' }}>
        {authStatus === 'loading' || profileLoading
          ? 'Đang xác định thông tin tài khoản...'
          : lookupMode === 'MEMBER'
            ? 'Tra cứu nhóm đặt chỗ thuộc tài khoản Member đang đăng nhập.'
            : lookupMode === 'GUEST'
              ? 'Tra cứu Guest bằng mã nhóm đặt chỗ và một thông tin liên hệ.'
              : 'Không thể xác minh vai trò tài khoản.'}
      </div>
      {lookupMode === null && authStatus === 'signed-in' && (
        <p style={{ color: '#b00', marginBottom: '16px' }}>
          {profileError || 'Tài khoản hiện tại không có vai trò Member để tra cứu đặt chỗ.'}
        </p>
      )}

      {lookupMode === 'MEMBER' && (
        <MyBookingGroupsList
          groups={myGroups}
          loading={myGroupsLoading}
          error={myGroupsError}
          onRetry={() => loadMyGroups.current()}
          onOpen={openGroup}
          selectedCode={group?.groupCode}
        />
      )}

      {/* A signed-in Member picks a group from the list above, so only Guests (and the unknown-role state) type a code. */}
      <section className="workspace-card lookup-panel" style={lookupMode === 'MEMBER' && !error ? { display: 'none' } : undefined}>
        {lookupMode !== 'MEMBER' && (
          <form onSubmit={handleLookup}>
            <fieldset disabled={authStatus === 'loading' || profileLoading || lookupMode === null} style={{ border: 0, padding: 0, margin: 0, minInlineSize: 0 }}>
              <label style={{ display: 'block', marginBottom: '14px' }}>
                Mã nhóm đặt chỗ *
                <input
                  type="text"
                  placeholder="e.g. WG-20261113-ABCD"
                  value={groupCode}
                  onChange={event => setGroupCode(event.target.value.toUpperCase())}
                  disabled={loading}
                  required
                  style={{ width: '100%', marginTop: '6px' }}
                />
              </label>

              {lookupMode === 'GUEST' && (
                <div style={{ padding: '14px', background: '#fafafa', border: '1px solid #e3e3e3', marginBottom: '16px' }}>
                  <p style={{ margin: '0 0 10px', fontSize: '13px', fontWeight: 'bold' }}>
                    Xác minh thông tin (chỉ cần điền 1 trong 2 mục):
                  </p>
                  <div className="form-grid" style={{ margin: 0 }}>
                    <label>
                      Email khi đặt
                      <input
                        type="email"
                        value={contactEmail}
                        onChange={event => { setContactEmail(event.target.value); if (event.target.value) setContactPhone(''); }}
                        disabled={loading}
                      />
                    </label>
                    <label>
                      Số điện thoại khi đặt
                      <input
                        type="tel"
                        value={contactPhone}
                        onChange={event => { setContactPhone(event.target.value); if (event.target.value) setContactEmail(''); }}
                        disabled={loading}
                      />
                    </label>
                  </div>
                </div>
              )}

              <button type="submit" className="button primary" disabled={loading} style={{ width: '100%' }}>
                {loading ? 'Đang tra cứu...' : 'Tra cứu nhóm đặt chỗ'}
              </button>
            </fieldset>
          </form>
        )}

        {error && <p role="alert" style={{ color: '#b00', marginTop: '14px', fontSize: '14px' }}>{error}</p>}
      </section>

      {group && (
        <div ref={detailsRef}>
          <GroupDetailCard
            group={group}
            authorization={lookupMode === 'MEMBER' ? cardAuthorization : null}
            proof={lookupMode === 'GUEST' ? guestProof : undefined}
            onRefresh={refresh}
            onTicketUpdated={refresh}
            renderTicketExtra={current => (lookupMode === 'MEMBER' ? (
              <div style={{ marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {(current.tickets || []).map(ticket => (
                  <ReviewCta key={`review-${ticket.ticketCode}`} ticket={ticket} sources={reviewSources} onReview={startReview} />
                ))}
              </div>
            ) : null)}
          />
        </div>
      )}
    </main>
  );
}
