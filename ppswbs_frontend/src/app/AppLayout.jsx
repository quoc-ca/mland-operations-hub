import React from 'react';
import { Link, Outlet } from 'react-router';
import { useAuth } from '../shared/auth/AuthProvider';
import { useT } from '../shared/i18n/I18nProvider';
import { LanguageSwitch } from '../shared/ui/components';
import { sharedMessages } from '../shared/ui/messages';

const appMessages = {
  vi: {
    home: 'Trang chủ',
    products: 'Sản phẩm',
    workshops: 'Workshop',
    promotions: 'Ưu đãi',
    loyalty: 'Điểm của tôi',
    management: 'Quản trị sản phẩm',
    promotionManagement: 'Quản lý ưu đãi',
    aiSupport: 'Trợ lý AI',
    designWorkspace: 'Thiết kế AI',
    nav: 'Điều hướng chính',
  },
  en: {
    home: 'Home',
    products: 'Products',
    workshops: 'Workshops',
    promotions: 'Offers',
    loyalty: 'My points',
    management: 'Catalogue management',
    promotionManagement: 'Offer management',
    aiSupport: 'AI Support',
    designWorkspace: 'AI Design',
    nav: 'Main navigation',
  },
};

/** Header and footer for the routed feature screens; prototype screens keep their own (src/main.jsx). */
export default function AppLayout() {
  const t = useT(appMessages);
  const tShared = useT(sharedMessages);
  const { status, signOut } = useAuth();
  return (
    <>
      <header className="app-header">
        <a className="brand" href="/">MLAND</a>
        <nav aria-label={t('nav')}>
          <Link to="/products">{t('products')}</Link>
          <a href="/#/workshops">{t('workshops')}</a>
          <Link to="/promotions">{t('promotions')}</Link>
          <Link to="/me/loyalty">{t('loyalty')}</Link>
          <Link to="/ai-support">{t('aiSupport')}</Link>
          <Link to="/design-requests/new">{t('designWorkspace')}</Link>
          <Link to="/catalogue-management/products">{t('management')}</Link>
          <Link to="/promotion-management/programs">{t('promotionManagement')}</Link>
        </nav>
        <div className="header-tools">
          <LanguageSwitch />
          {status === 'signed-in'
            ? <button type="button" className="button outline small" onClick={signOut}>{tShared('session.signOut')}</button>
            : <a className="header-login" href="/#/login">{tShared('session.signIn')}</a>}
        </div>
      </header>
      <Outlet />
      <footer><div className="container footer-inner"><strong>MLAND</strong><span>© MLAND</span></div></footer>
    </>
  );
}
