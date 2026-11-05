import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useHttp } from '../api/useHttp';
import { useCurrentAccount } from '../../features/accountmanagement/hooks/useCurrentAccount';
import { firebaseAuthClient } from '../../features/accountmanagement/auth/FirebaseAuthClient';
import { LanguageSwitch } from './components';
import './topbar.css';

/**
 * Shared Topbar Navigation (Atelier Light Theme)
 * Follows Figma Page 01 · Public (Node 402:2821 / 402:2980)
 * Reusable across Landing Page and all application feature pages.
 */
export default function MainTopbar() {
  const { status, signOut } = useAuth();
  const http = useHttp();
  const location = useLocation();
  const { account } = useCurrentAccount();

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [profileData, setProfileData] = useState(null);
  const [loyaltyPoints, setLoyaltyPoints] = useState(null);
  const [fbUser, setFbUser] = useState(() => firebaseAuthClient.getCurrentUser());
  const dropdownRef = useRef(null);

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

    // Lấy thông tin hồ sơ tài khoản (fullName, email)
    http('/api/v1/accounts/me/profile')
      .then((p) => {
        if (active && p) setProfileData(p);
      })
      .catch(() => {});

    // Lấy số điểm tích lũy thực tế
    http('/api/v1/members/me/loyalty-accounts/current')
      .then((acc) => {
        if (active && acc) {
          const pts = Number(acc.availablePoints ?? acc.balancePoints ?? 0);
          setLoyaltyPoints(pts);
        }
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
  const isInternal = ['STAFF', 'MANAGER', 'ADMIN'].includes(userRole);

  return (
    <div className="main-topbar-wrapper">
      <header className="main-topbar" role="banner">
        <Link to="/" className="main-topbar-brand">
          MLAND
        </Link>

        <nav className="main-topbar-nav" aria-label="Khám phá">
          <Link
            to="/workshops"
            className={`main-topbar-link ${location.pathname.startsWith('/workshops') ? 'active' : ''}`}
          >
            Workshop
          </Link>
          <Link
            to="/products"
            className={`main-topbar-link ${location.pathname.startsWith('/products') ? 'active' : ''}`}
          >
            Ready Rings
          </Link>
          <Link
            to="/design-requests/new"
            className={`main-topbar-link ${location.pathname.startsWith('/design-requests') ? 'active' : ''}`}
          >
            Custom
          </Link>
          <Link
            to="/workshop-booking/lookup"
            className={`main-topbar-link ${location.pathname.startsWith('/workshop-booking') ? 'active' : ''}`}
          >
            Tra cứu
          </Link>
          <Link
            to="/ai-support"
            className={`main-topbar-link ${location.pathname.startsWith('/ai-support') ? 'active' : ''}`}
          >
            Trợ lý AI
          </Link>
        </nav>

        <div className="main-topbar-tools">
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
                {isInternal && (
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

                  <Link to="/profile" className="main-topbar-dropdown-item" role="menuitem">
                    <span>👤</span> Hồ sơ cá nhân
                  </Link>
                  <Link to="/member/orders" className="main-topbar-dropdown-item" role="menuitem">
                    <span>📦</span> Đơn hàng của tôi
                  </Link>
                  <Link to="/me/loyalty" className="main-topbar-dropdown-item" role="menuitem">
                    <span>💎</span> Điểm thưởng tích lũy
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
                        <Link
                          to="/operations/sessions"
                          className="main-topbar-dropdown-item"
                          role="menuitem"
                        >
                          <span>🏢</span> Staff Vận hành
                        </Link>
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
                          <Link
                            to="/promotion-management/programs"
                            className="main-topbar-dropdown-item"
                            role="menuitem"
                          >
                            <span>🏷️</span> Quản lý Ưu đãi
                          </Link>
                        </>
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

                  <div className="main-topbar-dropdown-divider" />
                  <button
                    type="button"
                    className="main-topbar-dropdown-item main-topbar-dropdown-signout"
                    onClick={async () => {
                      setIsDropdownOpen(false);
                      await signOut();
                    }}
                    role="menuitem"
                  >
                    <span>🚪</span> Đăng xuất
                  </button>
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
    </div>
  );
}
