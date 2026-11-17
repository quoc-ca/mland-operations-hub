import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useHttp } from '../api/useHttp';
import { useCurrentAccount } from '../../features/accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../../features/accountmanagement/auth/FirebaseAuthClient';
import { LanguageSwitch } from './components';
import './topbar.css';

/**
 * Shared Topbar Navigation
 * Follows Figma Page 01 · Public & Guest (Node 521:171 Laptop & 521:241 Phone)
 * Reusable across Landing Page and all application feature pages.
 */
export default function MainTopbar() {
  const { status, signOut } = useAuth();
  const http = useHttp();
  const location = useLocation();
  const { account } = useCurrentAccount();

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);
  const [profileData, setProfileData] = useState(null);
  const [loyaltyPoints, setLoyaltyPoints] = useState(null);
  const [fbUser, setFbUser] = useState(() => firebaseAuthClient.getCurrentUser());
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState(null);
  const dropdownRef = useRef(null);

  const handleSignOut = async () => {
    if (isSigningOut) return;
    setIsSigningOut(true);
    setSignOutError(null);
    try {
      await signOut();
      setIsDropdownOpen(false);
      setIsMobileDrawerOpen(false);
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

  // Đóng dropdown và mobile drawer khi chuyển route
  useEffect(() => {
    setIsDropdownOpen(false);
    setIsMobileDrawerOpen(false);
  }, [location.pathname]);

  // Click outside listener
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
  const isInternal = ['STAFF', 'MANAGER', 'ADMIN'].includes(userRole);

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
    <>
      <div className="main-topbar-wrapper" data-node-id="521:171" data-name="Top_Header_Bar">
        <header className="main-topbar" role="banner">
          {/* Mobile Hamburger Button */}
          <button
            type="button"
            className="main-topbar-hamburger"
            onClick={() => setIsMobileDrawerOpen((prev) => !prev)}
            aria-label="Mở thực đơn điều hướng"
            aria-expanded={isMobileDrawerOpen}
            data-node-id="521:242"
          >
            <span>Menu</span>
          </button>

          {/* Brand Logo */}
          <Link to="/" className="main-topbar-brand" data-node-id="533:1471">
            MLAND
          </Link>

          {/* Desktop Navigator Menu */}
          <nav className="main-topbar-nav" aria-label="Khám phá" data-node-id="521:172">
            <Link
              to="/workshops"
              className={`main-topbar-link ${isWorkshopActive ? 'active' : ''}`}
              data-node-id="521:174"
            >
              Workshops
            </Link>
            <Link
              to="/products"
              className={`main-topbar-link ${location.pathname.startsWith('/products') ? 'active' : ''}`}
              data-node-id="521:175"
            >
              Trang sức
            </Link>
            <Link
              to="/workshops/PKG-COUPLE-SILVER/design"
              className={`main-topbar-link ${isRingDesignActive ? 'active' : ''}`}
              title="Tự tay phối dáng nhẫn, chất liệu, kích thước và khắc chữ"
              data-node-id="521:176"
            >
              Đặt thiết kế
            </Link>
            <Link
              to="/reviews"
              className={`main-topbar-link ${location.pathname.startsWith('/reviews') ? 'active' : ''}`}
              data-node-id="521:173"
            >
              Đánh giá
            </Link>
          </nav>

          {/* Desktop & Mobile Header Utility */}
          <div className="main-topbar-utility" data-node-id="521:177">
            {/* Lang Switcher */}
            <div className="main-topbar-lang-box" data-node-id="521:178">
              <span className="lang-icon" aria-hidden="true" />
              <LanguageSwitch />
            </div>

            {/* Member Profile or Login */}
            {isAuthenticated ? (
              <div className="main-topbar-user-menu-container" ref={dropdownRef}>
                <button
                  type="button"
                  className="main-topbar-member-pill"
                  onClick={() => setIsDropdownOpen((prev) => !prev)}
                  aria-expanded={isDropdownOpen}
                  aria-haspopup="true"
                  aria-label={`Tùy chọn tài khoản: ${resolvedName}`}
                  title="Tùy chọn tài khoản"
                  data-node-id="521:180"
                >
                  <span className="member-name">{resolvedName}...</span>
                  {loyaltyPoints !== null && (
                    <span className="member-points">({loyaltyPoints} pts)</span>
                  )}
                  {isInternal && (
                    <span className="member-role-badge">{userRole}</span>
                  )}
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

                    <Link to="/profile" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Hồ sơ cá nhân
                    </Link>
                    <Link to="/my/designs" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Mẫu nhẫn đã lưu
                    </Link>
                    <Link to="/member/orders" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Đơn hàng của tôi
                    </Link>
                    <Link to="/member/cart" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Giỏ hàng
                    </Link>
                    <Link to="/me/loyalty" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Điểm thưởng tích lũy
                    </Link>
                    <Link to="/me/saved-vouchers" className="main-topbar-dropdown-item" role="menuitem">
                      <span aria-hidden="true" /> Voucher của tôi
                    </Link>
                    <Link
                      to="/workshop-booking/lookup"
                      className="main-topbar-dropdown-item"
                      role="menuitem"
                    >
                      <span aria-hidden="true" /> Tra cứu vé &amp; Booking
                    </Link>

                    {/* Lối tắt quản trị nội bộ */}
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
                              <span aria-hidden="true" /> Staff Vận hành
                            </Link>
                            <Link
                              to="/staff/retail-orders"
                              className="main-topbar-dropdown-item"
                              role="menuitem"
                            >
                              <span aria-hidden="true" /> Đơn bán lẻ chi nhánh
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
                              <span aria-hidden="true" /> Quản trị Sản phẩm
                            </Link>
                            <Link
                              to="/promotion-management/programs"
                              className="main-topbar-dropdown-item"
                              role="menuitem"
                            >
                              <span aria-hidden="true" /> Quản lý Ưu đãi
                            </Link>
                            {userRole === 'MANAGER' && (
                              <Link
                                to="/manager/retail-payment-reconciliations"
                                className="main-topbar-dropdown-item"
                                role="menuitem"
                              >
                                <span aria-hidden="true" /> Đối soát thanh toán
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
                            <span aria-hidden="true" /> Kiểm duyệt Đánh giá
                          </Link>
                        )}
                        {userRole === 'ADMIN' && (
                          <Link
                            to="/admin/accounts"
                            className="main-topbar-dropdown-item"
                            role="menuitem"
                          >
                            <span aria-hidden="true" /> Admin Tài khoản
                          </Link>
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
                      <span aria-hidden="true" /> {isSigningOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
                    </button>
                    {signOutError && (
                      <div className="main-topbar-error-alert" role="alert">
                        {signOutError}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ) : (
              <Link to="/login" className="main-topbar-login-pill">
                Đăng nhập
              </Link>
            )}

            {/* Tra Cứu Link */}
            <Link
              to="/workshop-booking/lookup"
              className="main-topbar-text-btn"
              data-node-id="533:1472"
            >
              Tra cứu
            </Link>

            {/* Trợ lý AI Link */}
            <Link
              to="/ai-support"
              className="main-topbar-text-btn"
              data-node-id="533:1473"
            >
              Trợ lý AI
            </Link>
          </div>
        </header>
      </div>

      {/* Mobile Drawer Backdrop & Drawer (Node 521:247) */}
      {isMobileDrawerOpen && (
        <div
          className="main-mobile-drawer-backdrop"
          onClick={() => setIsMobileDrawerOpen(false)}
        />
      )}
      <div
        className={`main-mobile-drawer ${isMobileDrawerOpen ? 'open' : ''}`}
        data-node-id="521:247"
        data-name="Left Navigation Drawer (Slide-in Menu)"
      >
        <div className="main-mobile-drawer-header">
          <h4 className="drawer-brand" data-node-id="521:248">MLAND JEWELRY</h4>
          <button
            type="button"
            className="drawer-close-btn"
            onClick={() => setIsMobileDrawerOpen(false)}
            aria-label="Đóng menu"
          >
            Đóng
          </button>
        </div>

        <div className="drawer-user-info" data-node-id="521:249">
          {isAuthenticated ? (
            <p className="drawer-greeting">
              Xin chào: <strong>{resolvedName}</strong>
              {loyaltyPoints !== null && ` (${loyaltyPoints} pts)`}
            </p>
          ) : (
            <Link
              to="/login"
              className="drawer-login-link"
              onClick={() => setIsMobileDrawerOpen(false)}
            >
              Đăng nhập / Đăng ký
            </Link>
          )}
        </div>

        <nav className="drawer-menu-list">
          <Link
            to="/workshops"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
            data-node-id="521:250"
          >
            Workshops Trải Nghiệm
          </Link>
          <Link
            to="/products"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
            data-node-id="521:252"
          >
            Trang Sức Chế Tác Sẵn
          </Link>
          <Link
            to="/workshops/PKG-COUPLE-SILVER/design"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
            data-node-id="521:254"
          >
            Đặt Thiết Kế Nhẫn Riêng
          </Link>
          <Link
            to="/workshop-booking/lookup"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
            data-node-id="521:256"
          >
            Tra Cứu Lịch Hẹn Đặt
          </Link>
          <Link
            to="/ai-support"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
            data-node-id="521:258"
          >
            Tư Vấn Thiết Kế AI
          </Link>
          <Link
            to="/reviews"
            className="drawer-menu-item"
            onClick={() => setIsMobileDrawerOpen(false)}
          >
            Đánh Giá Khách Hàng
          </Link>
        </nav>
      </div>

      {/* Floating Action Button (FAB) AI for Mobile (Node 533:1538) */}
      <Link
        to="/ai-support"
        className="main-fab-ai"
        aria-label="Trợ lý AI tư vấn thiết kế"
        title="Trợ lý AI tư vấn thiết kế"
        data-node-id="533:1538"
      >
        <span className="fab-ai-text" data-node-id="533:1539">AI</span>
      </Link>
    </>
  );
}
