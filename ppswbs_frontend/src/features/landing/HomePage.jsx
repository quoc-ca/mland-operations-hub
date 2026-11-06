import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useFormat } from '../../shared/i18n/I18nProvider';
import { useCurrentAccount } from '../accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../accountmanagement/auth/FirebaseAuthClient';
import { listWorkshopPackages, listMemberBookings } from '../../api/workshops';
import { listPublicProducts, publicMediaUrl } from '../../api/catalogue';
import MainTopbar from '../../shared/ui/MainTopbar';
import './landing.css';

/**
 * 3 Gói Workshop cốt lõi từ thiết kế Figma (Node 402:2862)
 * Dùng làm baseline chuẩn và fallback khi chưa có kết nối backend
 */
const DEFAULT_WORKSHOP_PACKAGES = [
  {
    id: 'signature',
    tag: 'Gói 01 · Truyền thống',
    isFeatured: false,
    title: 'Signature Workshop',
    description:
      'Uốn bạc thanh, hàn lửa, tạo vân búa và khắc chữ cá nhân hóa. Lựa chọn hoàn hảo cho các cặp đôi.',
    durationAndMaterial: '⏱️ 2.5 - 3h | 💎 Bạc 925',
    price: '490,000 đ / người',
    actionLabel: 'Chi tiết & Đặt chỗ',
    actionUrl: '/workshops',
  },
  {
    id: 'silver-clay',
    tag: 'Được yêu thích nhất',
    isFeatured: true,
    title: 'Silver Clay Workshop',
    description:
      'Đất sét bạc 999 nặn hình tự do, đính đá CZ chịu nhiệt và nung lò tạo thành bạc khối sáng bóng.',
    durationAndMaterial: '⏱️ 3 - 3.5h | ✨ Bạc 999 nung lò',
    price: '690,000 đ / người',
    actionLabel: 'Đặt lịch giữ chỗ ngay',
    actionUrl: '/workshops',
  },
  {
    id: 'wax-carving',
    tag: 'Gói 03 · Chuyên sâu',
    isFeatured: false,
    title: 'Wax Carving Workshop',
    description:
      'Kỹ thuật điêu khắc sáp cứng 3D phức tạp, đúc sáp chảy và hoàn thiện bề mặt theo chuẩn kim hoàn.',
    durationAndMaterial: '⏱️ 3.5 - 4h | 🪐 Đúc sáp chảy',
    price: '890,000 đ / người',
    actionLabel: 'Chi tiết & Đặt chỗ',
    actionUrl: '/workshops',
  },
];

/**
 * Bộ sưu tập Ready Rings bán chạy từ thiết kế Figma (Node 402:2892)
 * Dùng làm baseline chuẩn và fallback khi chưa có dữ liệu sản phẩm
 */
const DEFAULT_READY_RINGS = [
  {
    code: 'ML-R01',
    name: 'Nhẫn Bạc Twilight Twist',
    material: 'Bạc 925 xoắn',
    price: '450,000 đ',
    url: '/products',
  },
  {
    code: 'ML-R02',
    name: 'Nhẫn Đính Đá Topaz Xanh',
    material: 'Bạc 925 & Đá tự nhiên',
    price: '620,000 đ',
    url: '/products',
  },
  {
    code: 'ML-R03',
    name: 'Cặp Nhẫn Tối Giản Aurora',
    material: 'Bộ đôi bạc xước',
    price: '850,000 đ',
    url: '/products',
  },
  {
    code: 'ML-R04',
    name: 'Nhẫn Vân Búa Hammered',
    material: 'Bạc bản rộng cá tính',
    price: '380,000 đ',
    url: '/products',
  },
];

/**
 * 6 Thẻ dịch vụ Ecosystem Hub (Node 402:2927)
 */
const ECOSYSTEM_SERVICES = [
  {
    id: 'workshops',
    title: '🔨 Đặt lịch Workshop',
    desc: 'Chọn gói, chi nhánh, ngày giờ và nhận vé QR check-in',
    url: '/workshops',
  },
  {
    id: 'products',
    title: '💍 Mua nhẫn Ready Rings',
    desc: 'Catalogue nhẫn bạc có sẵn giao hàng toàn quốc qua GHTK',
    url: '/products',
  },
  {
    id: 'custom',
    title: '🎨 Chế tác theo yêu cầu',
    desc: 'Gửi ý tưởng phác thảo để xưởng báo giá và gia công độc bản',
    url: '/design-requests/new',
  },
  {
    id: 'ai-support',
    title: '🤖 Trợ lý AI tư vấn',
    desc: 'Tải ảnh mẫu nhẫn để phân tích độ khó kỹ thuật & chi phí ước tính',
    url: '/ai-support',
  },
  {
    id: 'booking-lookup',
    title: '🎫 Tra cứu vé & Booking',
    desc: 'Xem lại lịch hẹn workshop và quét mã QR check-in tại quầy',
    url: '/workshop-booking/lookup',
  },
  {
    id: 'orders',
    title: '🚚 Tra cứu đơn hàng',
    desc: 'Theo dõi tiến độ đóng gói và hành trình vận đơn GHTK',
    url: '/member/orders',
  },
];

/**
 * Đánh giá khách hàng thực tế (Node 402:2952)
 */
const CUSTOMER_REVIEWS = [
  {
    testimonial:
      '“Buổi workshop Silver Clay cực kỳ thư giãn! Nghệ nhân chỉ dẫn kiên nhẫn từng bước, nhẫn nung xong đeo vừa khít.”',
    reviewer: 'Hà My & Quốc Trung',
    workshop: 'Silver Clay Workshop · Chi nhánh HN',
  },
  {
    testimonial:
      '“Tự tay gõ từng vân búa làm nhẫn kỷ niệm 2 năm cho người yêu là trải nghiệm vô giá. 10/10 điểm!”',
    reviewer: 'Trần Hoàng Long',
    workshop: 'Signature Workshop · Chi nhánh TP.HCM',
  },
  {
    testimonial:
      '“Tính năng AI phân tích ảnh nhẫn Pinterest rất tiện, giúp mình ước tính được độ khó trước khi đăng ký.”',
    reviewer: 'Lê Thuỳ Dung',
    workshop: 'Wax Carving Workshop',
  },
];

export default function HomePage() {
  const { status, getIdToken } = useAuth();
  const http = useHttp();
  const format = useFormat();
  const { account } = useCurrentAccount();

  // Live state kết nối từ các features khác
  const [workshopPackages, setWorkshopPackages] = useState(DEFAULT_WORKSHOP_PACKAGES);
  const [readyRings, setReadyRings] = useState(DEFAULT_READY_RINGS);
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
    'Thành viên';

  const userRole = account?.role || 'MEMBER';

  useEffect(() => {
    if (!isAuthenticated || (account?.role && account.role !== 'MEMBER')) { setNextTicket(null); setTicketLookupDone(false); return undefined; }
    let active = true;
    (async () => {
      try {
        const token = await getIdToken();
        if (!token) return;
        const bookings = await listMemberBookings({ authorization: `Bearer ${token}` });
        const now = Date.now();
        const upcoming = [];
        for (const booking of bookings) {
          if (booking.bookingStatus !== 'CONFIRMED') continue;
          for (const session of booking.sessions || []) {
            const start = new Date(session.scheduledStart).getTime();
            if (!Number.isNaN(start) && start >= now) upcoming.push({ bookingCode: booking.bookingCode, start });
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
        const mapped = data.slice(0, 3).map((pkg, idx) => ({
          id: pkg.packageId || String(idx),
          tag: idx === 1 ? 'Được yêu thích nhất' : `Gói 0${idx + 1} · Workshop`,
          isFeatured: idx === 1,
          title: pkg.name || 'Workshop',
          description: pkg.description || 'Trải nghiệm tự tay làm nhẫn bạc thủ công tại xưởng.',
          durationAndMaterial: `⏱️ ${pkg.requiredSessionCount || 1} buổi | 💎 ${pkg.minParticipants || 1}-${pkg.maxParticipants || 4} người`,
          price: pkg.publicPrice
            ? `${format.money(pkg.publicPrice)} / người`
            : '490,000 đ / người',
          actionLabel: idx === 1 ? 'Đặt lịch giữ chỗ ngay' : 'Chi tiết & Đặt chỗ',
          actionUrl: `/workshops/${encodeURIComponent(pkg.packageId || '')}`,
        }));
        setWorkshopPackages(mapped);
      })
      .catch(() => {});

    // Lấy danh mục sản phẩm nhẫn chế tác sẵn thực tế từ Feature 002
    listPublicProducts({ page: 0, sort: 'newest' })
      .then((res) => {
        if (!active) return;
        const items = Array.isArray(res) ? res : res?.items || [];
        if (items.length === 0) return;
        const mapped = items.slice(0, 4).map((p, idx) => {
          const code = p.variants?.[0]?.sku || `ML-R0${idx + 1}`;
          const minPrice = p.priceRange?.minimum || p.variants?.[0]?.appliedPrice;
          const img = p.images?.[0];
          return {
            code,
            name: p.name,
            material: p.category?.name || p.description || 'Bạc 925 cao cấp',
            price: minPrice ? format.money(minPrice) : '450,000 đ',
            imageUrl: img?.url ? publicMediaUrl(img.url) : null,
            url: `/products/${encodeURIComponent(p.id)}`,
          };
        });
        setReadyRings(mapped);
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [format]);

  // ==========================================================================
  // 2. Tích hợp dữ liệu thành viên khi đăng nhập: Orders (008), Profile (001)
  // ==========================================================================
  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;

    // Lấy số lượng đơn hàng thực tế từ Feature 008 (Ready Ring Sales & Orders)
    http('/api/v1/members/me/ready-ring-orders')
      .then((data) => {
        if (!active) return;
        const orders = Array.isArray(data) ? data : data?.items || [];
        setOrderCount(orders.length);
      })
      .catch(() => {});

    // Lấy họ và tên hồ sơ thực tế từ Feature 001 (Account Management)
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
    <div className="landing-page-root">
      {/* Topbar điều hướng dùng chung chuẩn Atelier Light */}
      <MainTopbar />

      <div className="landing-container" style={{ marginTop: '16px' }}>
        {/* ================================================================
            KHỐI 0: MEMBER QUICK-ACTION BAR (KHI ĐÃ ĐĂNG NHẬP)
            ================================================================ */}
        {isAuthenticated && (
          <section
            className="landing-member-quick-bar"
            aria-label="Thanh tác vụ nhanh thành viên"
          >
            <div className="landing-member-info">
              <span className="landing-member-badge">ACTIVE · MEMBER</span>
              <p className="landing-member-greeting">
                Xin chào, {memberDisplayName}!
                {nextTicket ? (
                  <>
                    {' '}Buổi workshop sắp tới:{' '}
                    <strong>{new Date(nextTicket.start).toLocaleString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</strong>
                  </>
                ) : (ticketLookupDone ? ' Bạn chưa có buổi workshop nào sắp tới.' : '')}
              </p>
            </div>
            <div className="landing-member-actions">
              {nextTicket && (
                <Link
                  to={`/workshop-booking/lookup?code=${encodeURIComponent(nextTicket.bookingCode)}`}
                  className="landing-quick-btn"
                >
                  🎟️ Xem vé &amp; mã QR
                </Link>
              )}
              <Link to="/member/orders" className="landing-quick-btn">
                📦 Đơn hàng ({orderCount})
              </Link>
            </div>
          </section>
        )}

        {/* ================================================================
            KHỐI 1: HERO BANNER (TRỌNG TÂM CHUYỂN ĐỔI KÉP)
            ================================================================ */}
        <section className="landing-hero" aria-labelledby="hero-title">
          <div className="landing-eyebrow">
            TRẢI NGHIỆM CHẾ TÁC CỦA RIÊNG BẠN
          </div>
          <h2 id="hero-title" className="landing-hero-title">
            MỘT CHIẾC NHẪN,
            <br />
            MỘT CÂU CHUYỆN RIÊNG.
          </h2>
          <p className="landing-hero-desc">
            Chào mừng bạn đến với Mland Operations Hub. Nơi bạn có thể tự tay
            tạo tác những chiếc nhẫn bạc ý nghĩa trong các buổi workshop ấm cúng,
            chọn mua nhẫn chế tác sẵn tinh xảo, hoặc hiện thực hóa mẫu thiết kế
            độc bản cùng nghệ nhân kim hoàn.
          </p>
          <div className="landing-hero-actions">
            <Link to="/workshops" className="landing-btn-primary">
              🔨 ĐẶT WORKSHOP NGAY
            </Link>
            <Link to="/products" className="landing-btn-secondary">
              💍 KHÁM PHÁ READY RINGS
            </Link>
          </div>
        </section>

        {/* ================================================================
            KHỐI 2: VỀ XƯỞNG KIM HOÀN MLAND (BRAND STORY & COMMITTMENTS)
            ================================================================ */}
        <section className="landing-brand-story" aria-labelledby="story-title">
          <span className="landing-story-eyebrow">VỀ XƯỞNG KIM HOÀN MLAND</span>
          <h3 id="story-title" className="landing-story-title">
            Không gian sáng tạo kim hoàn thủ công giữa lòng đô thị
          </h3>
          <p className="landing-story-desc">
            Tại Mland, chúng tôi tin rằng mỗi món trang sức đều mang linh hồn và
            ký ức của người sở hữu. Dù bạn chưa từng cầm giũa hay cưa bạc, các
            nghệ nhân Mland sẽ đồng hành 1-kèm-1 để bạn tự tay hoàn thiện chiếc
            nhẫn bạc 925 nguyên chất mang dấu ấn cá nhân trọn vẹn nhất.
          </p>
          <div className="landing-commitments">
            <div className="landing-commitment-item">
              <span className="landing-check-icon" aria-hidden="true">✓</span>
              <span>Bạc 925 tiêu chuẩn cao cấp kèm bảo hành đánh bóng trọn đời.</span>
            </div>
            <div className="landing-commitment-item">
              <span className="landing-check-icon" aria-hidden="true">✓</span>
              <span>Nghệ nhân hướng dẫn 1-kèm-1, cam kết 100% tự tay làm ra sản phẩm ưng ý.</span>
            </div>
            <div className="landing-commitment-item">
              <span className="landing-check-icon" aria-hidden="true">✓</span>
              <span>Đầy đủ thiết bị đúc nung hiện đại, đồ uống miễn phí và không gian thư giãn ấm cúng.</span>
            </div>
          </div>
        </section>

        {/* ================================================================
            KHỐI 3: CÁC GÓI WORKSHOP CỐT LÕI (NỐI FEATURE 003)
            ================================================================ */}
        <section className="landing-section" aria-labelledby="workshop-title">
          <h3 id="workshop-title" className="landing-section-title">
            CÁC GÓI WORKSHOP CHẾ TÁC NỔI BẬT
          </h3>
          <div className="landing-workshop-grid">
            {workshopPackages.map((pkg) => (
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
                <p className="landing-workshop-price">{pkg.price}</p>
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
            READY RINGS — NHẪN CHẾ TÁC THỦ CÔNG SẴN SÀNG GIAO
          </h3>
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
                  Chi tiết →
                </Link>
              </article>
            ))}
          </div>
        </section>

        {/* ================================================================
            KHỐI 5: ECOSYSTEM HUB — 6 DỊCH VỤ & CỔNG NỘI BỘ (NỐI CÁC TÍNH NĂNG)
            ================================================================ */}
        <section className="landing-ecosystem-card" aria-labelledby="ecosystem-title">
          <h3 id="ecosystem-title" className="landing-section-title">
            HỆ SINH THÁI DỊCH VỤ & TIỆN ÍCH HỆ THỐNG
          </h3>
          <div className="landing-services-grid">
            {ECOSYSTEM_SERVICES.map((srv) => (
              <Link
                key={srv.id}
                to={srv.url}
                className="landing-service-tile"
              >
                <h4 className="landing-service-title">
                  {srv.title}
                  {srv.id === 'orders' && isAuthenticated && orderCount > 0 && (
                    <span className="landing-badge-count">{orderCount}</span>
                  )}
                </h4>
                <p className="landing-service-desc">{srv.desc}</p>
              </Link>
            ))}
          </div>

          <div className="landing-portals-bar" role="region" aria-label="Cổng nội bộ">
            <span>🏢 <strong>Cổng nội bộ:</strong></span>
            <Link
              to="/operations/sessions"
              className={`landing-portal-link ${userRole === 'STAFF' ? 'active-role' : ''}`}
              title="Dành cho nhân viên check-in và điều phối ca"
            >
              Staff Vận hành {userRole === 'STAFF' && '★'}
            </Link>
            <span className="landing-portal-separator">|</span>
            <Link
              to="/catalogue-management/products"
              className={`landing-portal-link ${userRole === 'MANAGER' ? 'active-role' : ''}`}
              title="Quản lý danh mục và giá sản phẩm"
            >
              Quản trị Sản phẩm
            </Link>
            <span className="landing-portal-separator">|</span>
            <Link
              to="/promotion-management/programs"
              className={`landing-portal-link ${userRole === 'MANAGER' ? 'active-role' : ''}`}
              title="Quản lý voucher và chính sách ưu đãi"
            >
              Quản lý Ưu đãi
            </Link>
            <span className="landing-portal-separator">|</span>
            <Link
              to="/admin/accounts"
              className={`landing-portal-link ${userRole === 'ADMIN' ? 'active-role' : ''}`}
              title="Quản trị người dùng và phân quyền"
            >
              Admin Tài khoản {userRole === 'ADMIN' && '★'}
            </Link>
          </div>
        </section>

        {/* ================================================================
            KHỐI 6: ĐÁNH GIÁ THỰC TẾ TỪ KHÁCH HÀNG (SOCIAL PROOF)
            ================================================================ */}
        <section className="landing-section" aria-labelledby="reviews-title">
          <div className="landing-section-heading-row">
            <h3 id="reviews-title" className="landing-section-title">
              KHÁCH HÀNG NÓI GÌ VỀ TRẢI NGHIỆM TẠI MLAND
            </h3>
            <span className="landing-section-rating">4.9 / 5.0 ★</span>
          </div>
          <div className="landing-reviews-grid">
            {CUSTOMER_REVIEWS.map((rev, idx) => (
              <article key={idx} className="landing-review-card">
                <blockquote className="landing-review-text">
                  {rev.testimonial}
                </blockquote>
                <cite className="landing-reviewer-name">{rev.reviewer}</cite>
                <span className="landing-review-workshop">{rev.workshop}</span>
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
              📍 Hà Nội: 45 Tràng Tiền, Quận Hoàn Kiếm.
            </p>
            <p className="landing-showroom-line">
              📍 TP. Hồ Chí Minh: 128 Nguyễn Đình Chiểu, Quận 3.
            </p>
            <p className="landing-showroom-line">
              📞 Hotline: 1900 6868 · ⏰ Mở cửa: 09:00 - 21:00 hàng ngày.
            </p>
          </div>
          <p className="landing-policy-links">
            Điều khoản dịch vụ · Chính sách bảo mật · Cam kết bảo hành bạc trọn đời.
          </p>
          <p className="landing-copyright">
            © 2026 Mland Operations Hub. Thiết kế chuẩn Atelier Light.
          </p>
        </footer>
      </div>

      {/* ================================================================
          MOBILE STICKY BOTTOM NAVIGATION (320px - 768px Viewports)
          ================================================================ */}
      <nav className="landing-mobile-bottom-nav" aria-label="Điều hướng di động">
        <Link to="/" className="landing-mobile-nav-item active">
          <span className="landing-mobile-nav-icon" aria-hidden="true">⌂</span>
          <span className="landing-mobile-nav-label">Trang chủ</span>
        </Link>
        <Link to="/workshops" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">◈</span>
          <span className="landing-mobile-nav-label">Workshop</span>
        </Link>
        <Link to="/products" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">◇</span>
          <span className="landing-mobile-nav-label">Nhẫn</span>
        </Link>
        <Link to="/workshop-booking/lookup" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">🎫</span>
          <span className="landing-mobile-nav-label">Vé QR</span>
        </Link>
        <Link to="/profile" className="landing-mobile-nav-item">
          <span className="landing-mobile-nav-icon" aria-hidden="true">👤</span>
          <span className="landing-mobile-nav-label">Tài khoản</span>
        </Link>
      </nav>
    </div>
  );
}
