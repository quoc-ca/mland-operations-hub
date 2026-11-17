import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useFormat } from '../../shared/i18n/I18nProvider';
import { useCurrentAccount } from '../accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../accountmanagement/auth/FirebaseAuthClient';
import { listWorkshopPackages, listMemberBookingGroups } from '../../api/workshops';
import { listPublicProducts, publicMediaUrl } from '../../api/catalogue';
import MainTopbar from '../../shared/ui/MainTopbar';
import MainFooter from '../../shared/ui/MainFooter';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../loyalty-promotion/publicOffers';
import './landing.css';

/**
 * 3 Gói Workshop mặc định từ thiết kế Figma (Page 01 · Node 521:187 / 521:191 / 521:195)
 */
const DEFAULT_WORKSHOP_PACKAGES = [
  {
    id: 'basic-silver',
    packageId: 'PKG-BASIC-SILVER',
    title: 'Workshop Làm Nhẫn Bạc Cơ Bản · 2 Giờ (Phôi bạc 925 & hướng dẫn 1-1)',
    mobileTitle: 'Nhẫn Bạc Cơ Bản (2 Giờ)',
    description: 'Phôi bạc 925, thợ kim hoàn kèm 1-1.',
    durationAndMaterial: '2 Giờ | Phôi bạc 925',
    price: '490,000 đ',
    actionLabel: 'Xem chi tiết & Đặt slot →',
    actionUrl: '/workshops',
    thumbnailType: 'silver-ring',
    thumbnailAlign: 'left',
  },
  {
    id: 'couple-ring',
    packageId: 'PKG-COUPLE-SILVER',
    title: 'Workshop Nhẫn Cặp Đính Đá Phong Thuỷ · 3.5 Giờ (Khắc laser miễn phí)',
    mobileTitle: 'Nhẫn Cặp Phong Thuỷ (3.5 Giờ)',
    description: 'Đính đá hộ mệnh, khắc tên laser miễn phí.',
    durationAndMaterial: '3.5 Giờ | Bạc 925 & Đá',
    price: '890,000 đ',
    actionLabel: 'Xem chi tiết & Đặt slot →',
    actionUrl: '/workshops',
    thumbnailType: 'couple-ring',
    thumbnailAlign: 'right',
  },
  {
    id: 'masterclass',
    packageId: 'PKG-MASTERCLASS',
    title: 'Khoá Masterclass Nghệ Nhân & Chế Tác Riêng (Đặt slot trước 24h)',
    mobileTitle: 'Masterclass Nghệ Nhân (Riêng)',
    description: 'Tạo hình nhẫn độc bản theo ý tưởng.',
    durationAndMaterial: 'Theo yêu cầu | Độc bản',
    price: '1,500,000 đ',
    actionLabel: 'Xem chi tiết & Đặt slot →',
    actionUrl: '/workshops',
    thumbnailType: 'masterclass',
    thumbnailAlign: 'left',
  },
];

/**
 * 3 Đánh giá tiêu biểu từ Figma (Page 01 · Node 521:208 / 521:215 / 521:222)
 */
const FEATURED_REVIEWS = [
  {
    quote:
      '"Lần đầu tự tay gò bạc và hàn nhẫn, nghệ nhân kèm 1-1 cực kỳ tận tâm. Chiếc nhẫn hoàn thiện sáng bóng vượt mong đợi!"',
    rating: '5.0 / 5.0',
    author: 'M*** A*** (Đà Nẵng)',
    experience: 'Khách trải nghiệm · Nhẫn Bạc Cơ Bản',
  },
  {
    quote:
      '"Đặt làm nhẫn cặp đính đá nhân dịp kỷ niệm. Trải nghiệm rất ý nghĩa, khắc tên laser sắc nét và tư vấn chọn size tuyệt vời."',
    rating: '5.0 / 5.0',
    author: 'T*** H*** (TP.HCM)',
    experience: 'Khách trải nghiệm · Nhẫn Cặp Phong Thuỷ',
  },
  {
    quote:
      '"Wonderful bespoke jewelry experience! The staff spoke fluent English and guided me through traditional goldsmith techniques."',
    rating: '5.0 / 5.0',
    author: 'D*** L*** (Du khách Anh)',
    experience: 'Khách trải nghiệm · Masterclass Nghệ Nhân',
  },
];

export default function HomePage() {
  const location = useLocation();
  const [dismissedNotice, setDismissedNotice] = useState(false);
  const sessionNotice = (!dismissedNotice && location.state?.sessionNotice) || null;
  const { status, getIdToken } = useAuth();
  const http = useHttp();
  const format = useFormat();
  const { account } = useCurrentAccount();

  const [workshopPackages, setWorkshopPackages] = useState(DEFAULT_WORKSHOP_PACKAGES);
  const workshopPrograms = usePublicWorkshopPrograms();
  const [showcaseProducts, setShowcaseProducts] = useState([]);
  const [activeSlide, setActiveSlide] = useState(0);
  const [orderCount, setOrderCount] = useState(0);
  const [liveFullName, setLiveFullName] = useState('');

  const isAuthenticated = status === 'signed-in';
  const [nextTicket, setNextTicket] = useState(null);
  const [ticketLookupDone, setTicketLookupDone] = useState(false);
  const currentUser = firebaseAuthClient.getCurrentUser();

  const memberDisplayName =
    liveFullName ||
    account?.fullName?.trim() ||
    currentUser?.displayName?.trim() ||
    (account?.email ? account.email.split('@')[0] : '') ||
    'Thành viên';

  const userRole = account?.role || 'MEMBER';

  // Lấy vé workshop sắp tới khi thành viên đã đăng nhập
  useEffect(() => {
    if (!isAuthenticated || (account?.role && account.role !== 'MEMBER')) {
      setNextTicket(null);
      setTicketLookupDone(false);
      return undefined;
    }
    let active = true;
    (async () => {
      try {
        const token = await getIdToken();
        if (!token) return;
        const groups = await listMemberBookingGroups({ authorization: `Bearer ${token}` });
        const now = Date.now();
        const upcoming = [];
        for (const group of groups || []) {
          for (const ticket of group.tickets || []) {
            if (ticket.ticketStatus !== 'CONFIRMED') continue;
            for (const session of ticket.sessions || []) {
              const start = new Date(session.scheduledStart).getTime();
              if (!Number.isNaN(start) && start >= now) {
                upcoming.push({ bookingCode: group.groupCode, start });
              }
            }
          }
        }
        upcoming.sort((a, b) => a.start - b.start);
        if (active) setNextTicket(upcoming[0] || null);
      } catch {
        if (active) setNextTicket(null);
      } finally {
        if (active) setTicketLookupDone(true);
      }
    })();
    return () => {
      active = false;
    };
  }, [isAuthenticated, account?.role, getIdToken]);

  // Nạp dữ liệu gói Workshop & Sản phẩm showcase từ API backend
  useEffect(() => {
    let active = true;

    // Lấy gói workshop thực tế
    listWorkshopPackages()
      .then((data) => {
        if (!active || !Array.isArray(data) || data.length === 0) return;
        const mapped = data.slice(0, 3).map((pkg, idx) => ({
          id: pkg.packageId || String(idx),
          packageId: pkg.packageId,
          publicPrice: pkg.publicPrice,
          title: pkg.name || DEFAULT_WORKSHOP_PACKAGES[idx]?.title || 'Workshop',
          mobileTitle: pkg.name || DEFAULT_WORKSHOP_PACKAGES[idx]?.mobileTitle || 'Workshop',
          description: pkg.description || DEFAULT_WORKSHOP_PACKAGES[idx]?.description || '',
          durationAndMaterial: `${pkg.requiredSessionCount || 2} Giờ | ${pkg.minParticipants || 1}-${pkg.maxParticipants || 4} người`,
          price: pkg.publicPrice ? `${format.money(pkg.publicPrice)}` : DEFAULT_WORKSHOP_PACKAGES[idx]?.price || '490,000 đ',
          actionLabel: 'Xem chi tiết & Đặt slot →',
          actionUrl: `/workshops/${encodeURIComponent(pkg.packageId || '')}`,
          imageUrl: pkg.imageUrl ? publicMediaUrl(pkg.imageUrl) : null,
          thumbnailType: DEFAULT_WORKSHOP_PACKAGES[idx]?.thumbnailType || 'silver-ring',
          thumbnailAlign: idx % 2 === 1 ? 'right' : 'left',
        }));
        setWorkshopPackages(mapped);
      })
      .catch(() => {});

    // Lấy danh sách sản phẩm showcase
    listPublicProducts({ page: 0, sort: 'newest' })
      .then((res) => {
        if (!active) return;
        const items = Array.isArray(res) ? res : res?.items || [];
        if (items.length > 0) {
          setShowcaseProducts(items.slice(0, 5));
        }
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [format]);

  // Lấy thông tin tài khoản thành viên
  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;

    http('/api/v1/members/me/ready-ring-orders')
      .then((data) => {
        if (!active) return;
        const orders = Array.isArray(data) ? data : data?.items || [];
        setOrderCount(orders.length);
      })
      .catch(() => {});

    http('/api/v1/accounts/me/profile')
      .then((prof) => {
        if (active && prof?.fullName?.trim()) {
          setLiveFullName(prof.fullName.trim());
        }
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [isAuthenticated, http]);

  return (
    <div className="landing-page-root" data-node-id="521:169" data-name="Homepage - Laptop">
      {/* Top Header Bar dùng chung */}
      <MainTopbar />

      <main className="landing-main-content">
        {/* Session Notice nếu có */}
        {sessionNotice && (
          <div className="landing-session-notice" role="status" aria-live="polite">
            <span>{sessionNotice}</span>
            <button
              type="button"
              onClick={() => setDismissedNotice(true)}
              aria-label="Đóng thông báo"
              className="landing-notice-close"
            >
              ×
            </button>
          </div>
        )}

        {/* Member Quick-Action Bar khi đăng nhập */}
        {isAuthenticated && (
          <section className="landing-member-quick-bar" aria-label="Thanh tác vụ nhanh thành viên">
            <div className="landing-member-info">
              <span className="landing-member-badge">ACTIVE · MEMBER</span>
              <p className="landing-member-greeting">
                Xin chào, {memberDisplayName}!
                {nextTicket ? (
                  <>
                    {' '}Buổi workshop sắp tới:{' '}
                    <strong>
                      {new Date(nextTicket.start).toLocaleString('vi-VN', {
                        weekday: 'long',
                        day: '2-digit',
                        month: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </strong>
                  </>
                ) : (
                  ticketLookupDone ? ' Bạn chưa có buổi workshop nào sắp tới.' : ''
                )}
              </p>
            </div>
            <div className="landing-member-actions">
              {nextTicket && (
                <Link
                  to={`/workshop-booking/lookup?code=${encodeURIComponent(nextTicket.bookingCode)}`}
                  className="landing-quick-btn"
                >
                  Xem vé &amp; QR
                </Link>
              )}
              <Link to="/member/orders" className="landing-quick-btn">
                Đơn hàng ({orderCount})
              </Link>
            </div>
          </section>
        )}

        {/* ================================================================
            SECTION 1: HERO BANNER (Node 521:170 Laptop & 521:260 Phone)
            ================================================================ */}
        <section
          className="landing-hero-banner"
          aria-labelledby="hero-title"
          data-node-id="521:170"
          data-name="Section_1_Hero_Banner"
        >
          <div className="hero-content-box" data-node-id="521:182">
            <div className="hero-text-col">
              <h1 id="hero-title" className="hero-main-title" data-node-id="521:183">
                Tự tay chế tác nhẫn bạc &amp; Trang sức thiết kế cá nhân hoá
              </h1>
              <p className="hero-sub-title" data-node-id="521:262">
                Không gian kim hoàn thủ công giữa phố. Kết hợp kỹ thuật truyền thống &amp; cá nhân hoá.
              </p>
              <div className="hero-actions">
                <Link
                  to="/workshops"
                  className="hero-cta-btn"
                  data-node-id="521:264"
                >
                  Khám phá Workshop →
                </Link>
                <Link to="/products" className="hero-secondary-btn">
                  Xem Trang Sức
                </Link>
              </div>
            </div>

            <div className="hero-brand-box" data-node-id="521:184">
              <div className="hero-brand-holder" data-node-id="521:185">
                <span className="holder-icon">MLAND</span>
                <p className="holder-text">img-holder · Brand Logo</p>
              </div>
            </div>
          </div>
        </section>

        {/* ================================================================
            SECTION 2: WORKSHOP PACKAGES (Node 521:186 Laptop & 521:267 Phone)
            ================================================================ */}
        <section
          className="landing-workshops-section"
          aria-labelledby="workshops-title"
          data-node-id="521:186"
          data-name="Section_2_Workshop_Packages"
        >
          <div className="section-header-row">
            <div>
              <span className="section-eyebrow" data-node-id="521:268">GÓI WORKSHOP NỔI BẬT</span>
              <h2 id="workshops-title" className="section-main-title" data-node-id="521:269">
                Trải nghiệm tự làm trang sức
              </h2>
            </div>
            <Link to="/workshops" className="section-see-all-link">
              Xem tất cả gói →
            </Link>
          </div>

          <div className="workshop-cards-list">
            {workshopPackages.map((item) => (item.packageId ? { ...item, offers: offersForPackage(item, workshopPrograms) } : item)).map((pkg) => (
              <article
                key={pkg.id}
                className={`workshop-card-item ${pkg.thumbnailAlign === 'right' ? 'thumb-right' : 'thumb-left'}`}
                data-name={pkg.title}
              >
                {/* Thumbnail */}
                <div className="workshop-card-thumb">
                  {pkg.imageUrl ? (
                    <img src={pkg.imageUrl} alt={pkg.title} className="thumb-image" />
                  ) : (
                    <div className="thumb-placeholder">
                      <span className="placeholder-icon">Workshop</span>
                      <span className="placeholder-label">
                        img-holder · {pkg.thumbnailType || 'Workshop'}
                      </span>
                    </div>
                  )}
                </div>

                {/* Content */}
                <div className="workshop-card-body">
                  <h3 className="workshop-card-title">{pkg.title}</h3>
                  <p className="workshop-card-desc">{pkg.description}</p>
                  <p className="workshop-card-meta">{pkg.durationAndMaterial}</p>
                  <div className="workshop-card-price-row">
                    <span className="workshop-card-price">
                      <WorkshopOfferPrice offers={pkg.offers} fallback={pkg.price} />
                      {pkg.offers?.discounted ? ' / người' : ''}
                    </span>
                    <WorkshopOfferBadges offers={pkg.offers} />
                  </div>
                  <Link to={pkg.actionUrl} className="workshop-card-action">
                    {pkg.actionLabel}
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </section>

        {/* ================================================================
            SECTION 3: ABOUT SHOWCASE (Node 521:199 Laptop & 521:288 Phone)
            ================================================================ */}
        <section
          className="landing-about-section"
          aria-labelledby="about-title"
          data-node-id="521:199"
          data-name="Section_3_About_Showcase"
        >
          <div className="about-header">
            <span className="section-eyebrow" data-node-id="521:289">VỀ M-LAND ATELIER</span>
            <h2 id="about-title" className="about-title" data-node-id="521:200">
              Về M-Land
            </h2>
            <p className="about-story" data-node-id="521:201">
              Không gian trải nghiệm kim hoàn thủ công giữa lòng phố. Chúng tôi kết hợp kỹ thuật chế tác
              truyền thống với công nghệ cá nhân hoá hiện đại.
            </p>
          </div>

          <div className="about-showcase-box" data-node-id="521:202">
            <div className="showcase-slider-viewport">
              {showcaseProducts.length > 0 && showcaseProducts[activeSlide] ? (
                <div className="showcase-slide-item">
                  {showcaseProducts[activeSlide].images?.[0]?.url ? (
                    <img
                      src={publicMediaUrl(showcaseProducts[activeSlide].images[0].url)}
                      alt={showcaseProducts[activeSlide].name}
                      className="showcase-img"
                    />
                  ) : (
                    <div className="showcase-placeholder">
                      <span className="placeholder-icon">Sản phẩm</span>
                      <p className="placeholder-label">img-holder · Product Slider</p>
                    </div>
                  )}
                  <div className="showcase-overlay-caption">
                    <strong>{showcaseProducts[activeSlide].name}</strong>
                    <span>
                      {showcaseProducts[activeSlide].priceRange?.minimum
                        ? `${format.money(showcaseProducts[activeSlide].priceRange.minimum)}`
                        : 'Signature Collection'}
                    </span>
                  </div>
                </div>
              ) : (
                <div className="showcase-placeholder" data-node-id="521:204">
                  <span className="placeholder-icon">Sản phẩm</span>
                  <p className="placeholder-label">img-holder · Product Slider</p>
                  <div className="showcase-overlay-caption">
                    <strong>Signature Collection · Rings</strong>
                  </div>
                </div>
              )}
            </div>

            {/* Carousel Pagination Dots (5 items) */}
            <div
              className="carousel-pagination-dots"
              data-node-id="521:203"
              role="tablist"
              aria-label="Chọn mẫu sản phẩm trưng bày"
            >
              {[0, 1, 2, 3, 4].map((dotIdx) => (
                <button
                  key={dotIdx}
                  type="button"
                  className={`dot-indicator ${activeSlide === dotIdx ? 'active' : ''}`}
                  onClick={() => setActiveSlide(dotIdx)}
                  aria-label={`Trưng bày mẫu ${dotIdx + 1}`}
                  role="tab"
                  aria-selected={activeSlide === dotIdx}
                />
              ))}
            </div>
          </div>
        </section>

        {/* ================================================================
            SECTION 4: REVIEWS (Node 521:205 Laptop & 521:297 Phone)
            ================================================================ */}
        <section
          className="landing-reviews-section"
          aria-labelledby="reviews-title"
          data-node-id="521:205"
          data-name="Section_4_Reviews"
        >
          <div className="reviews-header-row">
            <div>
              <span className="section-eyebrow" data-node-id="521:298">ĐÁNH GIÁ TỪ KHÁCH HÀNG</span>
              <h2 id="reviews-title" className="reviews-section-title" data-node-id="521:206">
                Khách hàng nói gì về M-Land
              </h2>
            </div>
            <Link to="/reviews" className="section-see-all-link">
              Xem tất cả đánh giá →
            </Link>
          </div>

          <div className="reviews-cards-grid" data-node-id="521:207">
            {FEATURED_REVIEWS.map((rev, idx) => (
              <article
                key={idx}
                className="review-card-item"
                data-node-id={`review-${idx + 1}`}
              >
                <div className="review-quote-box">
                  <blockquote className="review-quote-text">{rev.quote}</blockquote>
                </div>
                <div className="review-rating-row">
                  <span className="star-rating">{rev.rating}</span>
                </div>
                <h4 className="reviewer-name">{rev.author}</h4>
                <p className="reviewer-experience">{rev.experience}</p>
              </article>
            ))}
          </div>
        </section>
      </main>

      {/* Chân trang Footer chuẩn Figma */}
      <MainFooter />
    </div>
  );
}
