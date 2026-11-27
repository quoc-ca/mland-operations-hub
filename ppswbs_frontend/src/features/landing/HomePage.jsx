import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useFormat, useLanguage, useT } from '../../shared/i18n/I18nProvider';
import { useCurrentAccount } from '../accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../accountmanagement/auth/FirebaseAuthClient';
import { listWorkshopPackages, listMemberBookingGroups } from '../../api/workshops';
import { listPublicProducts, publicMediaUrl } from '../../api/catalogue';
import MainTopbar from '../../shared/ui/MainTopbar';
import { PublicProductGrid } from '../product-management/ui/public/PublicProductGrid';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../loyalty-promotion/publicOffers';
import { landingMessages } from './messages';
import './landing.css';

const LOCALES = { vi: 'vi-VN', en: 'en-US' };

/**
 * 3 Gói Workshop cốt lõi từ thiết kế Figma (Node 402:2862)
 * Dùng làm baseline chuẩn và fallback khi chưa có kết nối backend.
 * Chữ hiển thị nằm trong messages.js (khóa `workshops.default.<key>.*`); `price` là giá trị số.
 */
const DEFAULT_WORKSHOP_PACKAGES = [
  {
    id: 'signature',
    key: 'signature',
    isFeatured: false,
    title: 'Signature Workshop',
    price: 490000,
    actionKey: 'workshops.actionDetail',
    actionUrl: '/workshops',
  },
  {
    id: 'silver-clay',
    key: 'silverClay',
    isFeatured: true,
    title: 'Silver Clay Workshop',
    price: 690000,
    actionKey: 'workshops.actionBook',
    actionUrl: '/workshops',
  },
  {
    id: 'wax-carving',
    key: 'waxCarving',
    isFeatured: false,
    title: 'Wax Carving Workshop',
    price: 890000,
    actionKey: 'workshops.actionDetail',
    actionUrl: '/workshops',
  },
];

/**
 * Bộ sưu tập Ready Rings bán chạy từ thiết kế Figma (Node 402:2892)
 * Dùng làm baseline chuẩn và fallback khi chưa có dữ liệu sản phẩm
 */
const DEFAULT_READY_RINGS = [
  { code: 'ML-R01', key: 'r01', price: 450000, url: '/products' },
  { code: 'ML-R02', key: 'r02', price: 620000, url: '/products' },
  { code: 'ML-R03', key: 'r03', price: 850000, url: '/products' },
  { code: 'ML-R04', key: 'r04', price: 380000, url: '/products' },
];

/**
 * 6 Thẻ dịch vụ Ecosystem Hub (Node 402:2927)
 */
const ECOSYSTEM_SERVICES = [
  { id: 'workshops', key: 'workshops', url: '/workshops' },
  { id: 'products', key: 'products', url: '/products' },
  { id: 'custom', key: 'custom', url: '/design-requests/new' },
  { id: 'ai-support', key: 'aiSupport', url: '/ai-support' },
  { id: 'booking-lookup', key: 'bookingLookup', url: '/workshop-booking/lookup' },
  { id: 'orders', key: 'orders', url: '/member/orders' },
];

const MEMBER_SERVICE_IDS = ['custom', 'ai-support', 'orders'];

/**
 * Đánh giá khách hàng thực tế (Node 402:2952)
 */
const CUSTOMER_REVIEWS = [
  { n: 1, reviewer: 'Hà My & Quốc Trung' },
  { n: 2, reviewer: 'Trần Hoàng Long' },
  { n: 3, reviewer: 'Lê Thuỳ Dung' },
];

export default function HomePage() {
  const location = useLocation();
  const [dismissedNotice, setDismissedNotice] = useState(false);
  const sessionNotice = (!dismissedNotice && location.state?.sessionNotice) || null;
  const { status, getIdToken } = useAuth();
  const http = useHttp();
  const format = useFormat();
  const t = useT(landingMessages);
  const { language } = useLanguage();
  const { account } = useCurrentAccount();

  // Live state kết nối từ các features khác. Lưu dữ liệu thô; chữ hiển thị dựng lúc render theo ngôn ngữ.
  const [livePackages, setLivePackages] = useState(null);
  const workshopPrograms = usePublicWorkshopPrograms();
  const [liveRings, setLiveRings] = useState(null);
  const [readyProducts, setReadyProducts] = useState([]);
  const [orderCount, setOrderCount] = useState(1);
  const [liveFullName, setLiveFullName] = useState('');

  const isAuthenticated = status === 'signed-in';

  // The Member's next confirmed workshop ticket (real data from their bookings), for the quick bar.
  const [nextTicket, setNextTicket] = useState(null);
  const [ticketLookupDone, setTicketLookupDone] = useState(false);
  const currentUser = firebaseAuthClient.getCurrentUser();

  // Tên hiển thị người dùng thực tế
  const memberDisplayName =
    liveFullName ||
    account?.fullName?.trim() ||
    currentUser?.displayName?.trim() ||
    (account?.email ? account.email.split('@')[0] : '') ||
    (currentUser?.email ? currentUser.email.split('@')[0] : '') ||
    t('common.memberFallbackName');

  const userRole = isAuthenticated ? account?.role || null : null;
  const isMember = userRole === 'MEMBER';
  // Member journeys that a Guest may also start; hidden from every other signed-in role (and until the role is known).
  const isCustomerView = !isAuthenticated || isMember;
  const visibleEcosystemServices = ECOSYSTEM_SERVICES.filter(
    (srv) => isCustomerView || !MEMBER_SERVICE_IDS.includes(srv.id),
  );
  const isShopStaff = userRole === 'STAFF' || userRole === 'MANAGER';
  const hasPortal = isShopStaff || userRole === 'ADMIN';

  const workshopPackages = useMemo(() => {
    if (!livePackages) {
      return DEFAULT_WORKSHOP_PACKAGES.map((pkg) => ({
        id: pkg.id,
        isFeatured: pkg.isFeatured,
        tag: pkg.isFeatured
          ? t('workshops.tagFeatured')
          : t(`workshops.default.${pkg.key}.tag`),
        title: pkg.title,
        description: t(`workshops.default.${pkg.key}.desc`),
        durationAndMaterial: t(`workshops.default.${pkg.key}.meta`),
        price: `${format.money(pkg.price)} ${t('common.perPerson')}`,
        actionLabel: t(pkg.actionKey),
        actionUrl: pkg.actionUrl,
      }));
    }
    return livePackages.map((pkg, idx) => ({
      id: pkg.packageId || String(idx),
      packageId: pkg.packageId,
      publicPrice: pkg.publicPrice,
      branchIds: pkg.branchIds,
      tag: idx === 1 ? t('workshops.tagFeatured') : t('workshops.tagLive', { number: idx + 1 }),
      isFeatured: idx === 1,
      title: pkg.name || t('workshops.fallbackName'),
      description: pkg.description || t('workshops.fallbackDesc'),
      durationAndMaterial: t('workshops.liveMeta', {
        sessions: pkg.requiredSessionCount || 1,
        min: pkg.minParticipants || 1,
        max: pkg.maxParticipants || 4,
      }),
      price: `${format.money(pkg.publicPrice || 490000)} ${t('common.perPerson')}`,
      actionLabel: idx === 1 ? t('workshops.actionBook') : t('workshops.actionDetail'),
      actionUrl: `/workshops/${encodeURIComponent(pkg.packageId || '')}`,
    }));
  }, [livePackages, t, format]);

  const readyRings = useMemo(() => {
    if (!liveRings) {
      return DEFAULT_READY_RINGS.map((ring) => ({
        code: ring.code,
        name: t(`rings.default.${ring.key}.name`),
        material: t(`rings.default.${ring.key}.material`),
        price: format.money(ring.price),
        url: ring.url,
      }));
    }
    return liveRings.map((ring) => ({
      code: ring.code,
      name: ring.name,
      material: ring.material || t('rings.fallbackMaterial'),
      price: format.money(ring.minPrice || 450000),
      imageUrl: ring.imageUrl,
      url: ring.url,
    }));
  }, [liveRings, t, format]);

  useEffect(() => {
    if (!isAuthenticated || account?.role !== 'MEMBER') { setNextTicket(null); setTicketLookupDone(false); return undefined; }
    let active = true;
    (async () => {
      try {
        const token = await getIdToken();
        if (!token) return;
        // A direct booking is a booking group and attendance belongs to one ticket (003.1 FR-046, FR-050).
        const groups = await listMemberBookingGroups({ authorization: `Bearer ${token}` });
        const now = Date.now();
        const upcoming = [];
        for (const group of groups) {
          for (const ticket of group.tickets || []) {
            if (ticket.ticketStatus !== 'CONFIRMED') continue;
            for (const session of ticket.sessions || []) {
              const start = new Date(session.scheduledStart).getTime();
              if (!Number.isNaN(start) && start >= now) upcoming.push({ bookingCode: group.groupCode, start });
            }
          }
        }
        upcoming.sort((left, right) => left.start - right.start);
        if (active) setNextTicket(upcoming[0] || null);
      } catch {
        if (active) setNextTicket(null);
      } finally {
        if (active) setTicketLookupDone(true);
      }
    })();
    return () => { active = false; };
  }, [isAuthenticated, account?.role, getIdToken]);

  // ==========================================================================
  // 1. Tích hợp dữ liệu công khai: Gói Workshop (Feature 003) & Ready Rings (Feature 002)
  // ==========================================================================
  useEffect(() => {
    let active = true;

    // Lấy danh sách gói workshop thực tế từ Feature 003
    listWorkshopPackages()
      .then((data) => {
        if (!active || !Array.isArray(data) || data.length === 0) return;
        setLivePackages(data.slice(0, 3));
      })
      .catch(() => { });

    // Lấy danh mục sản phẩm nhẫn chế tác sẵn thực tế từ Feature 002
    listPublicProducts({ page: 0, sort: 'newest' })
      .then((res) => {
        if (!active) return;
        const items = Array.isArray(res) ? res : res?.items || [];
        if (items.length === 0) return;
        // 002 product cards (discount price, offer badges, cart button) in a sideways strip: the whole first page.
        setReadyProducts(items);
        const mapped = items.slice(0, 4).map((p, idx) => {
          const img = p.images?.[0];
          return {
            code: p.variants?.[0]?.sku || `ML-R0${idx + 1}`,
            name: p.name,
            material: p.category?.name || p.description || '',
            minPrice: p.priceRange?.minimum || p.variants?.[0]?.appliedPrice,
            imageUrl: img?.url ? publicMediaUrl(img.url) : null,
            url: `/products/${encodeURIComponent(p.id)}`,
          };
        });
        setLiveRings(mapped);
      })
      .catch(() => { });

    return () => {
      active = false;
    };
  }, []);

  // ==========================================================================
  // 2. Tích hợp dữ liệu thành viên khi đăng nhập: Orders (008), Profile (001)
  // ==========================================================================
  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;

    // Lấy họ và tên hồ sơ thực tế từ Feature 001 (Account Management)
    http('/api/v1/accounts/me/profile')
      .then((prof) => {
        if (!active) return;
        if (prof?.fullName?.trim()) {
          setLiveFullName(prof.fullName.trim());
        }
        // Chỉ lấy đơn hàng nếu người dùng có vai trò MEMBER
        if (prof?.role === 'MEMBER') {
          http('/api/v1/members/me/ready-ring-orders')
            .then((data) => {
              if (!active) return;
              const orders = Array.isArray(data) ? data : data?.items || [];
              setOrderCount(orders.length);
            })
            .catch(() => { });
        }
      })
      .catch(() => { });

    return () => {
      active = false;
    };
  }, [isAuthenticated, http]);

  const nextTicketTime = nextTicket
    ? new Date(nextTicket.start).toLocaleString(LOCALES[language] || LOCALES.vi, { weekday: 'long', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
    : '';

  return (
    <div className="landing-page-root">
      {/* Topbar điều hướng dùng chung chuẩn Atelier Light */}
      <MainTopbar />

      <div className="landing-container" style={{ marginTop: '16px' }}>
        {sessionNotice && (
          <div
            className="landing-session-notice"
            role="status"
            aria-live="polite"
            style={{
              padding: '0.875rem 1.25rem',
              backgroundColor: '#fef3c7',
              border: '1px solid #f59e0b',
              borderRadius: '8px',
              color: '#92400e',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '1rem',
              fontWeight: 500,
            }}
          >
            <span>{sessionNotice}</span>
            <button
              type="button"
              onClick={() => setDismissedNotice(true)}
              aria-label={t('notice.dismiss')}
              style={{
                background: 'none',
                border: 'none',
                fontSize: '1.25rem',
                cursor: 'pointer',
                color: '#92400e',
                lineHeight: 1,
              }}
            >
              ×
            </button>
          </div>
        )}
        {/* ================================================================
            KHỐI 0: MEMBER QUICK-ACTION BAR (KHI ĐÃ ĐĂNG NHẬP)
            ================================================================ */}
        {isMember && (
          <section
            className="landing-member-quick-bar"
            aria-label={t('member.barLabel')}
          >
            <div className="landing-member-info">
              <span className="landing-member-badge">{t('member.badge')}</span>
              <p className="landing-member-greeting">
                {t('member.greeting', { name: memberDisplayName })}
                {nextTicket ? (
                  <>
                    {' '}{t('member.nextWorkshop')}{' '}
                    <strong>{nextTicketTime}</strong>
                  </>
                ) : (ticketLookupDone ? ` ${t('member.noUpcoming')}` : '')}
              </p>
            </div>
            <div className="landing-member-actions">
              {nextTicket && (
                <Link
                  to={`/workshop-booking/lookup?code=${encodeURIComponent(nextTicket.bookingCode)}`}
                  className="landing-quick-btn"
                >
                  {t('member.viewTicket')}
                </Link>
              )}
              <Link to="/member/orders" className="landing-quick-btn">
                {t('member.orders', { count: orderCount })}
              </Link>
            </div>
          </section>
        )}

        {/* ================================================================
            KHỐI 1: HERO BANNER (TRỌNG TÂM CHUYỂN ĐỔI KÉP)
            ================================================================ */}
        <section className="landing-hero" aria-labelledby="hero-title">
          <div className="landing-eyebrow">
            {t('hero.eyebrow')}
          </div>
          <h2 id="hero-title" className="landing-hero-title">
            {t('hero.titleLine1')}
            <br />
            {t('hero.titleLine2')}
          </h2>
          <p className="landing-hero-desc">
            {t('hero.desc')}
          </p>
          <div className="landing-hero-actions">
            <Link to="/workshops" className="landing-btn-primary">
              {t('hero.ctaWorkshop')}
            </Link>
            <Link to="/products" className="landing-btn-secondary">
              {t('hero.ctaRings')}
            </Link>
          </div>
        </section>

        {/* ================================================================
            KHỐI 2: VỀ XƯỞNG KIM HOÀN MLAND (BRAND STORY & COMMITTMENTS)
            ================================================================ */}
        <section className="landing-brand-story" aria-labelledby="story-title">
          <span className="landing-story-eyebrow">{t('story.eyebrow')}</span>
          <h3 id="story-title" className="landing-story-title">
            {t('story.title')}
          </h3>
          <p className="landing-story-desc">
            {t('story.desc')}
          </p>
          <div className="landing-commitments">
            {['story.commitment1', 'story.commitment2', 'story.commitment3'].map((key) => (
              <div key={key} className="landing-commitment-item">
                <span className="landing-check-icon" aria-hidden="true">✓</span>
                <span>{t(key)}</span>
              </div>
            ))}
          </div>
        </section>

        {/* ================================================================
            KHỐI 3: CÁC GÓI WORKSHOP CỐT LÕI (NỐI FEATURE 003)
            ================================================================ */}
        <section className="landing-section" aria-labelledby="workshop-title">
          <h3 id="workshop-title" className="landing-section-title">
            {t('workshops.title')}
          </h3>
          <div className="landing-workshop-grid">
            {workshopPackages.map(item => (item.packageId ? { ...item, offers: offersForPackage(item, workshopPrograms) } : item)).map((pkg) => (
              <article
                key={pkg.id}
                className={`landing-workshop-card ${pkg.isFeatured ? 'featured' : ''}`}
              >
                <div
                  className={`landing-workshop-tag ${pkg.isFeatured ? 'featured-tag' : ''}`}
                >
                  {pkg.tag}
                </div>
                <h4 className="landing-workshop-name">{pkg.title}</h4>
                <p className="landing-workshop-desc">{pkg.description}</p>
                <p className="landing-workshop-meta">{pkg.durationAndMaterial}</p>
                {/* FR-052: price after the best automatic discount from 006's public gate; plain price when none. */}
                <p className="landing-workshop-price">
                  <WorkshopOfferPrice offers={pkg.offers} fallback={pkg.price} />
                  {(pkg.offers?.discounted || pkg.offers?.bestVoucher) ? ` ${t('common.perPerson')}` : null}
                </p>
                <WorkshopOfferBadges offers={pkg.offers} />
                <Link
                  to={pkg.actionUrl}
                  className={`landing-workshop-btn ${pkg.isFeatured ? 'primary-action' : ''}`}
                >
                  {pkg.actionLabel}
                </Link>
              </article>
            ))}
          </div>
        </section>

        {/* ================================================================
            KHỐI 4: BỘ SƯU TẬP READY RINGS BÁN CHẠY (NỐI FEATURE 002)
            ================================================================ */}
        <section className="landing-section" aria-labelledby="ready-rings-title">
          <h3 id="ready-rings-title" className="landing-section-title">
            {t('rings.title')}
          </h3>
          {readyProducts.length > 0 ? <PublicProductGrid products={readyProducts} standalone /> : (
            <div className="landing-rings-grid">
              {readyRings.map((ring) => (
                <article key={ring.code} className="landing-ring-card">
                  {ring.imageUrl ? (
                    <img
                      src={ring.imageUrl}
                      alt={ring.name}
                      className="landing-ring-thumb"
                    />
                  ) : (
                    <span className="landing-ring-code-tag">{ring.code}</span>
                  )}
                  {ring.imageUrl && (
                    <span className="landing-ring-code-tag">{ring.code}</span>
                  )}
                  <h4 className="landing-ring-name">{ring.name}</h4>
                  <p className="landing-ring-material">{ring.material}</p>
                  <p className="landing-ring-price">{ring.price}</p>
                  <Link to={ring.url} className="landing-ring-btn">
                    {t('rings.detail')}
                  </Link>
                </article>
              ))}
            </div>
          )}
        </section>

        {/* ================================================================
            KHỐI 5: ECOSYSTEM HUB — 6 DỊCH VỤ & CỔNG NỘI BỘ (NỐI CÁC TÍNH NĂNG)
            ================================================================ */}
        <section className="landing-ecosystem-card" aria-labelledby="ecosystem-title">
          <h3 id="ecosystem-title" className="landing-section-title">
            {t('ecosystem.title')}
          </h3>
          <div className="landing-services-grid">
            {visibleEcosystemServices.map((srv) => (
              <Link
                key={srv.id}
                to={srv.url}
                className="landing-service-tile"
              >
                <h4 className="landing-service-title">
                  {t(`ecosystem.${srv.key}.title`)}
                  {srv.id === 'orders' && isMember && orderCount > 0 && (
                    <span className="landing-badge-count">{orderCount}</span>
                  )}
                </h4>
                <p className="landing-service-desc">{t(`ecosystem.${srv.key}.desc`)}</p>
              </Link>
            ))}
          </div>

          {/* Internal portals: only for the roles that may open them (route-access-matrix.md). */}
          {hasPortal && (
            <div className="landing-portals-bar" role="region" aria-label={t('portals.label')}>
              <span>🏢 <strong>{t('portals.heading')}</strong></span>
              {isShopStaff && (
                <>
                  <Link
                    to="/operations/sessions"
                    className={`landing-portal-link ${userRole === 'STAFF' ? 'active-role' : ''}`}
                    title={t('portals.staffTitle')}
                  >
                    {t('portals.staff')} {userRole === 'STAFF' && '★'}
                  </Link>
                  <span className="landing-portal-separator">|</span>
                  <Link to="/design-review-requests" className="landing-portal-link active-role" title={t('portals.designTitle')}>
                    {t('portals.design')} ★
                  </Link>
                  <span className="landing-portal-separator">|</span>
                  <Link
                    to="/catalogue-management/products"
                    className={`landing-portal-link ${userRole === 'MANAGER' ? 'active-role' : ''}`}
                    title={t('portals.catalogueTitle')}
                  >
                    {t('portals.catalogue')}
                  </Link>
                </>
              )}
              {userRole === 'MANAGER' && (
                <>
                  <span className="landing-portal-separator">|</span>
                  <Link to="/promotion-management/programs" className="landing-portal-link active-role" title={t('portals.promotionTitle')}>
                    {t('portals.promotion')}
                  </Link>
                </>
              )}
              {userRole === 'ADMIN' && (
                <Link to="/admin/accounts" className="landing-portal-link active-role" title={t('portals.adminTitle')}>
                  {t('portals.admin')} ★
                </Link>
              )}
            </div>
          )}
        </section>

        {/* ================================================================
            KHỐI 6: ĐÁNH GIÁ THỰC TẾ TỪ KHÁCH HÀNG (SOCIAL PROOF)
            ================================================================ */}
        <section className="landing-section" aria-labelledby="reviews-title">
          <div className="landing-section-heading-row">
            <h3 id="reviews-title" className="landing-section-title">
              {t('reviews.title')}
            </h3>
            <span className="landing-section-rating">4.9 / 5.0 ★</span>
          </div>
          <div className="landing-reviews-grid">
            {CUSTOMER_REVIEWS.map((rev) => (
              <article key={rev.n} className="landing-review-card">
                <blockquote className="landing-review-text">
                  {t(`reviews.${rev.n}.text`)}
                </blockquote>
                <cite className="landing-reviewer-name">{rev.reviewer}</cite>
                <span className="landing-review-workshop">{t(`reviews.${rev.n}.workshop`)}</span>
              </article>
            ))}
          </div>
        </section>

        {/* ================================================================
            KHỐI 7: SHOWROOM & CHÂN TRANG FOOTER
            ================================================================ */}
        <footer className="landing-footer" role="contentinfo">
          <h4 className="landing-footer-brand">MLAND OPERATIONS HUB</h4>
          <div className="landing-showrooms">
            <p className="landing-showroom-line">
              {t('footer.hanoi')}
            </p>
            <p className="landing-showroom-line">
              {t('footer.hcm')}
            </p>
            <p className="landing-showroom-line">
              {t('footer.hotline')}
            </p>
          </div>
          <p className="landing-policy-links">
            {t('footer.policies')}
          </p>
          <p className="landing-copyright">
            {t('footer.copyright')}
          </p>
        </footer>
      </div>

      {/* ================================================================
          MOBILE STICKY BOTTOM NAVIGATION (320px - 768px Viewports)
          ================================================================ */}
      <nav className="landing-mobile-bottom-nav" aria-label={t('mobileNav.label')}>
        <Link to="/" className="landing-mobile-nav-item active">
          <span className="landing-mobile-nav-icon" aria-hidden="true">⌂</span>
          <span className="landing-mobile-nav-label">{t('mobileNav.home')}</span>
        </Link>
        <Link to="/workshops" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">◈</span>
          <span className="landing-mobile-nav-label">{t('mobileNav.workshop')}</span>
        </Link>
        <Link to="/products" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">◇</span>
          <span className="landing-mobile-nav-label">{t('mobileNav.rings')}</span>
        </Link>
        <Link to="/workshop-booking/lookup" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">🎫</span>
          <span className="landing-mobile-nav-label">{t('mobileNav.lookup')}</span>
        </Link>
        {/* A Carrier has no profile page; password self-service is their only account page. */}
        <Link to={!isAuthenticated ? '/login' : userRole === 'CARRIER' ? '/security/password' : '/profile'} className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">👤</span>
          <span className="landing-mobile-nav-label">{t(isAuthenticated ? 'mobileNav.account' : 'mobileNav.signIn')}</span>
        </Link>
      </nav>
    </div>
  );
}
