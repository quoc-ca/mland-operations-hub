import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useHttp } from '../api/useHttp';
import { useCurrentAccount } from '../../features/accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../../features/accountmanagement/auth/FirebaseAuthClient';
import { LanguageSwitch } from './components';
import { useCartCounts } from './useCartCounts';
import { PendingPaymentNotice } from '../../workshops/PendingPaymentNotice';
import './topbar.css';

/**
 * Shared Topbar Navigation (Atelier Light Theme)
 * Follows Figma Page 01 · Public (Node 402:2821 / 402:2980)
 * Reusable across Landing Page and all application feature pages.
 */
export default function MainTopbar() {
  const { status, signOut, getIdToken } = useAuth();
  const http = useHttp();
  const location = useLocation();
  const { account } = useCurrentAccount();

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [profileData, setProfileData] = useState(null);
  const [loyaltyPoints, setLoyaltyPoints] = useState(null);
  const [fbUser, setFbUser] = useState(() => firebaseAuthClient.getCurrentUser());
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState(null);
  const dropdownRef = useRef(null);
  const cartCounts = useCartCounts();

  const handleSignOut = async () => {
    if (isSigningOut) return;
    setIsSigningOut(true);
    setSignOutError(null);
    try {
      await signOut();
      setIsDropdownOpen(false);
      // Navigation to Home with transient flash notice is handled solely by SessionNavigationBoundary
    } catch (err) {
      setSignOutError('Đăng xuất thất bại. Vui lòng thử lại.');
    } finally {
      setIsSigningOut(false);
    }
  };

  const isAuthenticated = status === 'signed-in';

  // Lắng nghe trạng thái thay đổi của Firebase Auth để cập nhật thông tin tài khoản tức thời
  useEffect(() => {
    const unsub = firebaseAuthClient.onAuthStateChanged((u) => {
      setFbUser(u);
    });
    return () => unsub();
  }, []);

  const currentUser = fbUser || firebaseAuthClient.getCurrentUser();

  // Đóng dropdown khi click ra ngoài hoặc khi chuyển route
  useEffect(() => {
    setIsDropdownOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Lấy dữ liệu hồ sơ và điểm thưởng thực tế từ backend khi đã đăng nhập
  useEffect(() => {
    if (!isAuthenticated) {
      setProfileData(null);
      setLoyaltyPoints(null);
      return;
    }
    let active = true;

    // Lấy thông tin hồ sơ tài khoản (fullName, email), rồi mới lấy điểm.
    // Điểm tích lũy chỉ có ở Member: gọi API Member với Staff/Manager/Admin sẽ bị 403,
    // mà mọi 403 đều đưa người dùng về trang chủ (SessionNavigationBoundary).
    http('/api/v1/accounts/me/profile')
      .then((p) => {
        if (!active || !p) return;
        setProfileData(p);
        if (p.role !== 'MEMBER') {
          setLoyaltyPoints(null);
          return;
        }
        http('/api/v1/members/me/loyalty-accounts/current')
          .then((acc) => {
            if (active && acc) {
              const pts = Number(acc.availablePoints ?? acc.balancePoints ?? 0);
              setLoyaltyPoints(pts);
            }
          })
          .catch(() => {});
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [isAuthenticated, http]);

  // Quy tắc ưu tiên tên hiển thị:
  // 1. Họ và tên từ Profile Backend (profileData.fullName)
  // 2. Họ và tên từ Account Backend (account.fullName)
  // 3. Tên hiển thị từ Firebase (currentUser.displayName)
  // 4. Phần username từ Email (email.split('@')[0])
  // 5. Fallback 'Tài khoản'
  const resolvedFullName =
    profileData?.fullName?.trim() ||
    account?.fullName?.trim() ||
    currentUser?.displayName?.trim() ||
    '';

  const userEmail =
    profileData?.email ||
    account?.email ||
    currentUser?.email ||
    '';

  const resolvedName =
    resolvedFullName ||
    (userEmail ? userEmail.split('@')[0] : 'Tài khoản');

  const userRole = profileData?.role || account?.role || 'MEMBER';
  const isCarrier = userRole === 'CARRIER';
  const isInternal = ['STAFF', 'MANAGER', 'ADMIN'].includes(userRole);
  const isStaffOrAdmin = isAuthenticated && (userRole === 'STAFF' || userRole === 'ADMIN');

  const isRingDesignActive =
    (location.pathname.includes('/design') &&
      !location.pathname.startsWith('/design-requests') &&
      !location.pathname.startsWith('/design-review-requests')) ||
    location.pathname.startsWith('/ring-design') ||
    location.pathname === '/my/designs';

  const isWorkshopActive =
    (location.pathname === '/workshops' || location.pathname.startsWith('/workshops/')) &&
    !location.pathname.includes('/design');

  return (
    <div className="main-topbar-wrapper">
      <header className="main-topbar" role="banner">
        <Link to="/" className="main-topbar-brand">
          MLAND
        </Link>

        <nav className="main-topbar-nav" aria-label="Khám phá">
          {isCarrier ? (
            <Link
              to="/carrier"
              className={`main-topbar-link ${location.pathname.startsWith('/carrier') ? 'active' : ''}`}
            >
              Cổng vận chuyển
            </Link>
          ) : (
            <>
              <Link
                to="/workshops"
                className={`main-topbar-link ${isWorkshopActive ? 'active' : ''}`}
              >
                Workshop
              </Link>
              {!isStaffOrAdmin && (
                <Link
                  to="/workshops/PKG-COUPLE-SILVER/design"
                  className={`main-topbar-link ${isRingDesignActive ? 'active' : ''}`}
                  title="Tự tay phối dáng nhẫn, chất liệu, kích thước và khắc chữ"
                >
                  Cấu hình nhẫn
                </Link>
              )}
              <Link
                to="/products"
                className={`main-topbar-link ${location.pathname.startsWith('/products') ? 'active' : ''}`}
              >
                Ready Rings
              </Link>
              {!isStaffOrAdmin && (
                <Link
                  to="/design-requests/new"
                  className={`main-topbar-link ${location.pathname.startsWith('/design-requests') ? 'active' : ''}`}
                >
                  Custom
                </Link>
              )}
              <Link
                to="/promotions"
                className={`main-topbar-link ${location.pathname.startsWith('/promotions') ? 'active' : ''}`}
              >
                Kho Voucher
              </Link>
              <Link
                to="/workshop-booking/lookup"
                className={`main-topbar-link ${location.pathname.startsWith('/workshop-booking') && location.pathname !== '/workshop-booking/cart' ? 'active' : ''}`}
              >
                Tra cứu
              </Link>
              {!isStaffOrAdmin && (
                <Link
                  to="/ai-support"
                  className={`main-topbar-link ${location.pathname.startsWith('/ai-support') ? 'active' : ''}`}
                >
                  Trợ lý AI
                </Link>
              )}
              <Link
                to="/reviews"
                className={`main-topbar-link ${location.pathname.startsWith('/reviews') ? 'active' : ''}`}
              >
                Đánh giá
              </Link>
              {userRole === 'MEMBER' && (
                <Link
                  to="/member/consultations"
                  className={`main-topbar-link ${location.pathname.startsWith('/member/consultations') ? 'active' : ''}`}
                >
                  Tư vấn
                </Link>
              )}
            </>
          )}
        </nav>

        <div className="main-topbar-tools">
          {!isCarrier && (
            <Link
              to="/cart"
              className="main-topbar-cart"
              aria-label={`Giỏ hàng (${cartCounts.product} sản phẩm, ${cartCounts.workshop} workshop)`}
              title={`Giỏ hàng: ${cartCounts.product} sản phẩm · ${cartCounts.workshop} workshop`}
            >
              <span aria-hidden="true">🛒</span>
              {cartCounts.total > 0 && <span className="main-topbar-cart-badge">{cartCounts.total}</span>}
            </Link>
          )}
          <LanguageSwitch />

          {isAuthenticated ? (
            <div className="main-topbar-user-menu-container" ref={dropdownRef}>
              <button
                type="button"
                className="main-topbar-user-chip"
                onClick={() => setIsDropdownOpen((prev) => !prev)}
                aria-expanded={isDropdownOpen}
                aria-haspopup="true"
                aria-label={`Tùy chọn tài khoản: ${resolvedName}`}
                title="Tùy chọn tài khoản"
              >
                <span>👤</span>
                <span className="main-topbar-chip-name">{resolvedName}</span>
                {loyaltyPoints !== null && (
                  <span className="main-topbar-chip-points">({loyaltyPoints} pts)</span>
                )}
                {(isInternal || isCarrier) && (
                  <span className="main-topbar-role-badge">{userRole}</span>
                )}
                <span className="main-topbar-caret" aria-hidden="true">▼</span>
              </button>

              {isDropdownOpen && (
                <div className="main-topbar-dropdown" role="menu">
                  <div className="main-topbar-dropdown-header">
                    <span className="main-topbar-dropdown-name">
                      {resolvedFullName || resolvedName}
                    </span>
                    {userEmail && (
                      <span className="main-topbar-dropdown-email">{userEmail}</span>
                    )}
                  </div>

                  {isCarrier ? (
                    <>
                      <Link to="/carrier" className="main-topbar-dropdown-item" role="menuitem">
                        <span>🚚</span> Cổng đối tác vận chuyển
                      </Link>
                      <Link to="/security/password" className="main-topbar-dropdown-item" role="menuitem">
                        <span>🔑</span> Đổi mật khẩu
                      </Link>
                    </>
                  ) : (
                    <>
                      <Link to="/profile" className="main-topbar-dropdown-item" role="menuitem">
                        <span>👤</span> Hồ sơ cá nhân
                      </Link>
                      {!isStaffOrAdmin && (
                        <Link to="/my/designs" className="main-topbar-dropdown-item" role="menuitem">
                          <span>💍</span> Mẫu nhẫn đã lưu
                        </Link>
                      )}
                      {userRole === 'MEMBER' && (
                        <Link to="/member/consultations" className="main-topbar-dropdown-item" role="menuitem">
                          <span>💬</span> Tư vấn thiết kế
                        </Link>
                      )}
                      <Link to="/member/orders" className="main-topbar-dropdown-item" role="menuitem">
                        <span>📦</span> Đơn hàng của tôi
                      </Link>
                      <Link to="/me/loyalty" className="main-topbar-dropdown-item" role="menuitem">
                        <span>💎</span> Điểm thưởng tích lũy
                      </Link>
                      <Link to="/me/saved-vouchers" className="main-topbar-dropdown-item" role="menuitem">
                        <span>🎟️</span> Voucher của tôi
                      </Link>
                      <Link
                        to="/workshop-booking/lookup"
                        className="main-topbar-dropdown-item"
                        role="menuitem"
                      >
                        <span>🎫</span> Tra cứu vé & Booking
                      </Link>

                      {/* Lối tắt quản trị cho nhân sự nội bộ */}
                      {isInternal && (
                        <>
                          <div className="main-topbar-dropdown-divider" />
                          <span className="main-topbar-dropdown-section-title">
                            Cổng công việc
                          </span>
                          {userRole === 'STAFF' && (
                            <>
                              <Link
                                to="/operations/sessions"
                                className="main-topbar-dropdown-item"
                                role="menuitem"
                              >
                                <span>🏢</span> Staff Vận hành
                              </Link>
                              <Link
                                to="/staff/retail-orders"
                                className="main-topbar-dropdown-item"
                                role="menuitem"
                              >
                                <span>🏪</span> Đơn bán lẻ chi nhánh
                              </Link>
                            </>
                          )}
                          {(userRole === 'MANAGER' || userRole === 'ADMIN') && (
                            <>
                              <Link
                                to="/catalogue-management/products"
                                className="main-topbar-dropdown-item"
                                role="menuitem"
                              >
                                <span>🛍️</span> Quản trị Sản phẩm
                              </Link>
                              {userRole === 'MANAGER' && (
                                <Link
                                  to="/promotion-management/programs"
                                  className="main-topbar-dropdown-item"
                                  role="menuitem"
                                >
                                  <span>🏷️</span> Quản lý Ưu đãi
                                </Link>
                              )}
                              {userRole === 'MANAGER' && (
                                <Link
                                  to="/manager/retail-payment-reconciliations"
                                  className="main-topbar-dropdown-item"
                                  role="menuitem"
                                >
                                  <span>💳</span> Đối soát thanh toán
                                </Link>
                              )}
                            </>
                          )}
                          {(userRole === 'STAFF' || userRole === 'MANAGER' || userRole === 'ADMIN') && (
                            <Link
                              to="/review-moderation"
                              className="main-topbar-dropdown-item"
                              role="menuitem"
                            >
                              <span>⭐</span> Kiểm duyệt Đánh giá
                            </Link>
                          )}
                          {userRole === 'ADMIN' && (
                            <Link
                              to="/admin/accounts"
                              className="main-topbar-dropdown-item"
                              role="menuitem"
                            >
                              <span>👥</span> Admin Tài khoản
                            </Link>
                          )}
                        </>
                      )}
                    </>
                  )}

                  <div className="main-topbar-dropdown-divider" />
                  <button
                    type="button"
                    className="main-topbar-dropdown-item main-topbar-dropdown-signout"
                    onClick={handleSignOut}
                    disabled={isSigningOut}
                    aria-busy={isSigningOut}
                    role="menuitem"
                  >
                    <span>🚪</span> {isSigningOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
                  </button>
                  {signOutError && (
                    <div style={{ color: '#dc2626', fontSize: '0.75rem', padding: '0.25rem 1rem' }} role="alert">
                      {signOutError}
                    </div>
                  )}
                </div>
              )}
            </div>
          ) : (
            <Link to="/login" className="main-topbar-login-btn">
              Đăng nhập
            </Link>
          )}
        </div>
      </header>
      <PendingPaymentNotice enabled={isAuthenticated && account?.role === 'MEMBER'} userKey={currentUser?.uid || null} getIdToken={getIdToken} />
    </div>
  );
}
