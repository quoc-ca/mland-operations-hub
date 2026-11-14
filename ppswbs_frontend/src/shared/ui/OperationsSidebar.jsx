import React, { useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useRole } from '../auth/useRole';
import { useLanguage } from '../i18n/I18nProvider';
import { getFirebaseAuth } from '../auth/firebaseSession';
import '../../ota-connectivity/ota-connectivity.css';

export function OperationsSidebar() {
  const location = useLocation();
  const { pathname, search } = location;
  const searchParams = new URLSearchParams(search);
  const tab = searchParams.get('tab');

  const { status, signOut } = useAuth();
  const { language, setLanguage } = useLanguage();
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState(null);

  const handleSignOut = async () => {
    if (isSigningOut) return;
    setIsSigningOut(true);
    setSignOutError(null);
    try {
      await signOut();
      // Navigation to Home with transient flash notice is handled solely by SessionNavigationBoundary
    } catch (err) {
      setSignOutError('Đăng xuất thất bại');
    } finally {
      setIsSigningOut(false);
    }
  };

  // Staff see their daily work; management areas appear for Manager and Admin only. The role is unknown until the
  // profile arrives, and then only the daily work shows. Navigation only: the backend decides every permission.
  const role = useRole();
  const canManage = role === 'MANAGER' || role === 'ADMIN';
  // Staff and Manager both manage products.
  const canManageProducts = canManage || role === 'STAFF';

  const authInstance = typeof getFirebaseAuth === 'function' ? getFirebaseAuth() : null;
  const currentUser = authInstance?.currentUser;
  const userEmail = currentUser?.email || '';
  const userInitial = (currentUser?.displayName?.[0] || userEmail?.[0] || 'M').toUpperCase();

  // Active state detection
  const isProductsActive =
    pathname === '/catalogue-management/products' ||
    (pathname.startsWith('/catalogue-management/products/') && pathname !== '/catalogue-management/products/new');
  const isNewProductActive = pathname === '/catalogue-management/products/new';
  const isCategoriesActive = pathname.startsWith('/catalogue-management/categories');
  const isImportsActive = pathname.startsWith('/catalogue-management/imports');

  const isProgramsActive =
    pathname === '/promotion-management/programs' ||
    (pathname.startsWith('/promotion-management/programs/') && pathname !== '/promotion-management/programs/new');
  const isNewProgramActive = pathname === '/promotion-management/programs/new';
  const isPointPolicyActive = pathname.startsWith('/promotion-management/point-policy');

  const isSessionBoardActive = pathname.startsWith('/operations/sessions');
  const isLocationsActive =
    pathname.startsWith('/operations/admin') ||
    pathname === '/operations/locations' ||
    pathname === '/operations/slots';
  const isPackagesActive =
    pathname.startsWith('/operations/packages') ||
    pathname === '/workshop-packages';
  const isPackageAddOnsActive = pathname.startsWith('/operations/package-add-ons');

  const isChannelsActive = pathname.startsWith('/ota-connectivity/channels');
  const isReconciliationActive = pathname.startsWith('/ota-connectivity/reconciliation');
  const isFinanceActive = pathname.startsWith('/ota-connectivity/finance');
  const isShopAddOnsActive = pathname.startsWith('/ota-connectivity/add-ons');

  const isBillsActive = pathname === '/settlement';
  const isCasesActive = pathname === '/settlement/cases' && tab !== 'policies';
  const isPoliciesActive = pathname === '/settlement/cases' && tab === 'policies';
  const isNotificationTemplatesActive = pathname.startsWith('/admin/notifications/templates');
  const isNotificationRequestsActive = pathname.startsWith('/admin/notifications/requests');
  const isProfileActive = pathname === '/profile';
  const isRetailOrdersActive = pathname.startsWith('/staff/retail-');
  const isRetailReconciliationActive = pathname.startsWith('/manager/retail-payment-reconciliations');
  const isDashboardActive = pathname === '/manager/dashboard';

  const isVi = language === 'vi';

  return (
    <aside className="ota-sidebar" aria-label="Operations Navigation">
      <div className="ota-brand">
        <span>M</span>
        <strong>MLAND</strong>
        <small>operations hub</small>
      </div>

      <div className="ota-sidebar-nav">
        {/* Own home of the signed-in role (/home sends Staff and Manager to theirs) */}
        <Link className={pathname === '/staff/home' || pathname === '/manager/home' ? 'active' : ''} to="/home">
          {isVi ? 'Trang chủ' : 'Home'}
        </Link>

        {/* REPORTING (FE-10): Manager only; the backend also refuses everyone else */}
        {role === 'MANAGER' && (
        <Link className={isDashboardActive ? 'active' : ''} to="/manager/dashboard">
          {isVi ? 'Dashboard điều hành' : 'Executive dashboard'}
        </Link>
        )}

        {canManageProducts && (<>
        {/* PRODUCT MANAGEMENT */}
        <div className="ota-sidebar-label">
          {isVi ? 'QUẢN LÝ SẢN PHẨM' : 'PRODUCT MANAGEMENT'}
        </div>
        <Link className={isProductsActive ? 'active' : ''} to="/catalogue-management/products">
          {isVi ? 'Danh sách sản phẩm' : 'Product catalogue'}
        </Link>
        <Link className={isNewProductActive ? 'active' : ''} to="/catalogue-management/products/new">
          {isVi ? 'Thêm sản phẩm' : 'New product'}
        </Link>
        <Link className={isCategoriesActive ? 'active' : ''} to="/catalogue-management/categories">
          {isVi ? 'Danh mục' : 'Categories'}
        </Link>
        <Link className={isImportsActive ? 'active' : ''} to="/catalogue-management/imports">
          {isVi ? 'Import Excel' : 'Bulk import'}
        </Link>

        </>)}

        {canManage && (<>
        {/* PROMOTIONS & LOYALTY */}
        <div className="ota-sidebar-label">
          {isVi ? 'QUẢN LÝ ƯU ĐÃI' : 'PROMOTIONS & LOYALTY'}
        </div>
        <Link className={isProgramsActive ? 'active' : ''} to="/promotion-management/programs">
          {isVi ? 'Chương trình ưu đãi' : 'Promotion programs'}
        </Link>
        <Link className={isNewProgramActive ? 'active' : ''} to="/promotion-management/programs/new">
          {isVi ? 'Tạo chương trình' : 'New program'}
        </Link>
        <Link className={isPointPolicyActive ? 'active' : ''} to="/promotion-management/point-policy">
          {isVi ? 'Chính sách điểm' : 'Point policy'}
        </Link>

        </>)}

        {/* RETAIL (FE-08) */}
        <div className="ota-sidebar-label">
          {isVi ? 'BÁN LẺ' : 'RETAIL'}
        </div>
        <Link className={isRetailOrdersActive ? 'active' : ''} to="/staff/retail-orders">
          {isVi ? 'Đơn bán lẻ chi nhánh' : 'Branch retail orders'}
        </Link>
        {canManage && (
        <Link className={isRetailReconciliationActive ? 'active' : ''} to="/manager/retail-payment-reconciliations">
          {isVi ? 'Đối soát thanh toán bán lẻ' : 'Retail payment reconciliation'}
        </Link>
        )}

        {/* WORKSHOP OPERATIONS */}
        <div className="ota-sidebar-label">
          {isVi ? 'VẬN HÀNH WORKSHOP' : 'WORKSHOP OPERATIONS'}
        </div>
        <Link className={isSessionBoardActive ? 'active' : ''} to="/operations/sessions">
          {role === 'STAFF' ? (isVi ? 'Ca của tôi' : 'My sessions') : (isVi ? 'Bảng điều phối ca' : 'Session board')}
        </Link>
        {canManage && (<>
        <Link className={isLocationsActive ? 'active' : ''} to="/operations/admin?tab=locations">
          {isVi ? 'Chi nhánh & khung giờ' : 'Locations & slots'}
        </Link>
        <Link className={isPackagesActive ? 'active' : ''} to="/operations/packages">
          {isVi ? 'Gói workshop' : 'Package management'}
        </Link>
        <Link className={isPackageAddOnsActive ? 'active' : ''} to="/operations/package-add-ons">
          {isVi ? 'Add-on gói' : 'Package add-ons'}
        </Link>
        </>)}

        {/* OTA CONNECTIVITY */}
        <div className="ota-sidebar-label">
          {isVi ? 'KẾT NỐI OTA' : 'OTA CONNECTIVITY'}
        </div>
        {canManage && (<>
        <Link className={isChannelsActive ? 'active' : ''} to="/ota-connectivity/channels">
          {isVi ? 'Kênh đối tác (Channels)' : 'Channel mappings'}
        </Link>
        <Link className={isReconciliationActive ? 'active' : ''} to="/ota-connectivity/reconciliation">
          {isVi ? 'Đối soát đồng bộ (Queue)' : 'Reconciliation'}
        </Link>
        <Link className={isFinanceActive ? 'active' : ''} to="/ota-connectivity/finance">
          {isVi ? 'Bằng chứng tài chính (Finance)' : 'Finance evidence'}
        </Link>
        </>)}
        <Link className={isShopAddOnsActive ? 'active' : ''} to="/ota-connectivity/add-ons">
          {isVi ? 'Add-on tại xưởng (Shop)' : 'Shop add-ons'}
        </Link>

        {/* WORKSHOP SETTLEMENT */}
        <div className="ota-sidebar-label">
          {isVi ? 'QUYẾT TOÁN & BỒI HOÀN' : 'WORKSHOP SETTLEMENT'}
        </div>
        <Link className={isBillsActive ? 'active' : ''} to="/settlement">
          {isVi ? 'Hóa đơn quyết toán (Bills)' : 'Final bills'}
        </Link>
        {canManage && (<>
        <Link className={isCasesActive ? 'active' : ''} to="/settlement/cases?tab=cases">
          {isVi ? 'Sự vụ & bồi hoàn (Cases)' : 'Settlement cases'}
        </Link>
        <Link className={isPoliciesActive ? 'active' : ''} to="/settlement/cases?tab=policies">
          {isVi ? 'Chính sách hoàn tiền' : 'Refund policies'}
        </Link>
        </>)}

        {canManage && (<>
        {/* NOTIFICATION MANAGEMENT */}
        <div className="ota-sidebar-label">
          {isVi ? 'QUẢN LÝ THÔNG BÁO' : 'NOTIFICATION MANAGEMENT'}
        </div>
        <Link className={isNotificationTemplatesActive ? 'active' : ''} to="/admin/notifications/templates">
          {isVi ? 'Mẫu thông báo email' : 'Email templates'}
        </Link>
        <Link className={isNotificationRequestsActive ? 'active' : ''} to="/admin/notifications/requests">
          {isVi ? 'Nhật ký gửi thư' : 'Delivery logs'}
        </Link>
        </>)}
      </div>

      <div className="ota-sidebar-footer">
        {status === 'signed-in' && (
          <Link
            to="/profile"
            className={`ota-user-card ${isProfileActive ? 'active' : ''}`}
            title={userEmail || (isVi ? 'Hồ sơ cá nhân' : 'Personal Profile')}
          >
            <div className="ota-user-avatar">{userInitial}</div>
            <div className="ota-user-info">
              <span className="ota-user-name">
                {isVi ? 'Hồ sơ cá nhân' : 'Personal Profile'}
              </span>
              {userEmail ? (
                <small className="ota-user-email">{userEmail}</small>
              ) : (
                <small className="ota-user-email">Manager / Staff</small>
              )}
            </div>
            <span className="ota-user-arrow">→</span>
          </Link>
        )}

        <div className="ota-footer-actions">
          <div className="ota-lang-toggle" role="group" aria-label="Language">
            <button
              type="button"
              className={`ota-lang-btn ${language === 'vi' ? 'active' : ''}`}
              onClick={() => setLanguage('vi')}
              aria-pressed={language === 'vi'}
            >
              VI
            </button>
            <button
              type="button"
              className={`ota-lang-btn ${language === 'en' ? 'active' : ''}`}
              onClick={() => setLanguage('en')}
              aria-pressed={language === 'en'}
            >
              EN
            </button>
          </div>

          {status === 'signed-in' ? (
            <div>
              <button
                type="button"
                className="ota-signout-btn"
                onClick={handleSignOut}
                disabled={isSigningOut}
                aria-busy={isSigningOut}
                title={isVi ? 'Đăng xuất khỏi hệ thống' : 'Sign out'}
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                  <polyline points="16 17 21 12 16 7" />
                  <line x1="21" y1="12" x2="9" y2="12" />
                </svg>
                <span>{isSigningOut ? (isVi ? 'Đang đăng xuất…' : 'Signing out…') : (isVi ? 'Đăng xuất' : 'Sign out')}</span>
              </button>
              {signOutError && (
                <div style={{ color: '#dc2626', fontSize: '0.75rem', marginTop: '0.25rem', padding: '0 0.5rem' }} role="alert">
                  {signOutError}
                </div>
              )}
            </div>
          ) : (
            <Link
              to="/login"
              className="ota-signin-btn"
              aria-label={isVi ? 'Đăng nhập tài khoản' : 'Sign in account'}
            >
              {isVi ? 'Đăng nhập' : 'Sign in'}
            </Link>
          )}
        </div>
      </div>
    </aside>
  );
}

export default OperationsSidebar;
