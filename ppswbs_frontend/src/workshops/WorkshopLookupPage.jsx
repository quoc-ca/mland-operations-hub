import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  getOwnedBookingGroup,
  listMemberBookingGroups,
  lookupGuestBookingGroup,
  WorkshopApiError,
} from '../api/workshops';
import { useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { GroupDetailCard } from './GroupDetailCard';
import { MyBookingGroupsList } from './MyBookingGroupsList';
import { ReviewCta } from './ReviewCta';
import { buildContactProof, DEFAULT_PHONE_COUNTRY } from './contactValidation';
import { PhoneInput } from './PhoneInput';
import { useBookingIdentity } from './useBookingIdentity';
import { useReviewManagementApi } from '../features/reviewmanagement/api/useReviewManagementApi';
import './figmaPublicShell.css';

const GROUP_POLL_MS = 20000;

/**
 * Lookup and management of a booking group (003.1 FR-042, FR-048, FR-049). A signed-in Member reads their own groups
 * from `GET /members/me/workshop-booking-groups` and opens one through `GET /workshop-booking-groups/{groupCode}`; a
 * Guest proves the group with its code and exactly one stored contact value through
 * `POST /workshop-booking-group-lookups`, so contact proof never travels in a URL.
 */
export function WorkshopLookupPage() {
  const t = useT(messages);
  const { language } = useLanguage();
  // Error state is { key, params } (translated when shown, so it follows the language) or { text } (server text).
  const say = value => (value.key ? t(value.key, value.params) : value.text);
  const { authStatus, mode: lookupMode, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const [groupCode, setGroupCode] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [phoneCountry, setPhoneCountry] = useState(DEFAULT_PHONE_COUNTRY);

  const [myGroups, setMyGroups] = useState(null);
  const [myGroupsLoading, setMyGroupsLoading] = useState(false);
  const [myGroupsError, setMyGroupsError] = useState(null);
  const loadMyGroups = useRef(null);
  loadMyGroups.current = async () => {
    setMyGroupsLoading(true);
    setMyGroupsError(null);
    try {
      const authorization = await memberAuthorization();
      if (!authorization) throw new Error('MEMBER_SESSION_EXPIRED');
      setMyGroups(await listMemberBookingGroups({ authorization }));
    } catch {
      setMyGroups(null);
      setMyGroupsError({ key: 'lookup.myGroupsError' });
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
        fresh = await lookupGuestBookingGroup({ groupCode: code, ...guestProof });
      }
      if (fresh) setGroup(fresh);
    } catch {
      // The card keeps what it has; the actor can ask again.
    }
  };

  // Staff/Manager decisions (e.g. a schedule-change request) happen elsewhere, so the open group is read again every
  // GROUP_POLL_MS while the tab is visible, and right away when the tab becomes visible again.
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  const refreshing = useRef(false);
  const openCode = group?.groupCode;
  useEffect(() => {
    if (!openCode) return undefined;
    const poll = async () => {
      if (document.visibilityState === 'hidden' || refreshing.current) return;
      refreshing.current = true;
      try { await refreshRef.current(); } finally { refreshing.current = false; }
    };
    const timer = setInterval(poll, GROUP_POLL_MS);
    document.addEventListener('visibilitychange', poll);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', poll); };
  }, [openCode]);

  const handleLookup = async event => {
    event.preventDefault();
    setError(null);
    if (!groupCode.trim()) {
      setError({ key: 'lookup.codeRequired' });
      return;
    }
    if (lookupMode === null) {
      setError({ key: 'lookup.roleUnknown' });
      return;
    }
    setLoading(true);
    try {
      let result;
      if (lookupMode === 'MEMBER') {
        const authorization = await memberAuthorization();
        if (!authorization) throw new Error('MEMBER_SESSION_EXPIRED');
        result = await getOwnedBookingGroup(groupCode.trim(), { authorization });
      } else {
        const { proof, error: proofError } = buildContactProof({ email: contactEmail, phone: contactPhone, phoneCountry }, language);
        if (!proof) {
          setError(proofError ? { text: proofError } : null);
          setLoading(false);
          return;
        }
        result = await lookupGuestBookingGroup({ groupCode: groupCode.trim(), ...proof });
      }
      setGroup(result);
    } catch (err) {
      setGroup(null);
      if (err instanceof WorkshopApiError) {
        if (err.status === 404 || err.code === 'BOOKING_NOT_FOUND') {
          setError({ key: 'lookup.notFound' });
        } else if (err.status === 401 || err.status === 403) {
          setError({ key: 'lookup.forbidden' });
        } else {
          setError(err.detail ? { text: err.detail } : { key: 'lookup.error' });
        }
      } else {
        setError(err?.message === 'MEMBER_SESSION_EXPIRED' ? { key: 'lookup.sessionExpired' } : err?.message ? { text: err.message } : { key: 'lookup.errorGeneric' });
      }
    } finally {
      setLoading(false);
    }
  };

  // The proof the Guest typed, in the normalized form every call sends (empty until it is well formed).
  const guestProof = buildContactProof({ email: contactEmail, phone: contactPhone, phoneCountry }, language).proof || {};
  const [cardAuthorization, setCardAuthorization] = useState(null);
  useEffect(() => {
    let active = true;
    if (lookupMode !== 'MEMBER') { setCardAuthorization(null); return () => { active = false; }; }
    memberAuthorization().then(value => { if (active) setCardAuthorization(value); });
    return () => { active = false; };
  }, [lookupMode, group?.groupCode]);

  return (
    <main className="public-page figma-public-page">
      <p className="eyebrow">{t('lookup.eyebrow')}</p>
      <h1>{t('lookup.title')}</h1>

      <div role="status" style={{ marginTop: '20px', marginBottom: '24px', padding: '12px 16px', border: '1px solid #dcdfe6', background: '#f8fafc', color: '#002967' }}>
        {authStatus === 'loading' || profileLoading
          ? t('lookup.identity.loading')
          : lookupMode === 'MEMBER'
            ? t('lookup.identity.member')
            : lookupMode === 'GUEST'
              ? t('lookup.identity.guest')
              : t('lookup.identity.unknown')}
      </div>
      {lookupMode === null && authStatus === 'signed-in' && (
        <p style={{ color: '#b00', marginBottom: '16px' }}>
          {profileError || t('lookup.noMemberRole')}
        </p>
      )}

      {lookupMode === 'MEMBER' && (
        <MyBookingGroupsList
          groups={myGroups}
          loading={myGroupsLoading}
          error={myGroupsError ? say(myGroupsError) : null}
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
                {t('lookup.groupCode')}
                <input
                  type="text"
                  placeholder="GRP-…"
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
                    {t('lookup.verifyHint')}
                  </p>
                  <div className="form-grid" style={{ margin: 0 }}>
                    <label>
                      {t('lookup.email')}
                      <input
                        type="email"
                        value={contactEmail}
                        onChange={event => { setContactEmail(event.target.value); if (event.target.value) setContactPhone(''); }}
                        disabled={loading}
                      />
                    </label>
                    {/* A div, not a label: a label around the country select would name it like the phone field. */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <label htmlFor="lookup-contact-phone">{t('lookup.phone')}</label>
                      <PhoneInput
                        id="lookup-contact-phone"
                        country={phoneCountry}
                        value={contactPhone}
                        onCountryChange={setPhoneCountry}
                        onValueChange={value => { setContactPhone(value); if (value) setContactEmail(''); }}
                        disabled={loading}
                      />
                    </div>
                  </div>
                </div>
              )}

              <button type="submit" className="button primary" disabled={loading} style={{ width: '100%' }}>
                {loading ? t('lookup.searching') : t('lookup.search')}
              </button>
            </fieldset>
          </form>
        )}

        {error && <p role="alert" style={{ color: '#b00', marginTop: '14px', fontSize: '14px' }}>{say(error)}</p>}
      </section>

      {group && (
        <div ref={detailsRef}>
          <GroupDetailCard
            group={group}
            authorization={lookupMode === 'MEMBER' ? cardAuthorization : null}
            proof={lookupMode === 'GUEST' ? guestProof : undefined}
            onRefresh={refresh}
            allowManualRefresh={false}
            onTicketUpdated={refresh}
            renderTicketAction={ticket => (lookupMode === 'MEMBER' ? (
              <div style={{ marginTop: '12px' }}>
                <ReviewCta ticket={ticket} sources={reviewSources} onReview={startReview} />
              </div>
            ) : null)}
          />
        </div>
      )}
    </main>
  );
}
