import React from 'react';
import { Link, Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useLanguage } from '../../shared/i18n/I18nProvider';
import { roleHomePath } from '../../shared/auth/roleHome';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { operatorSections } from './operatorSections';
import '../../ota-connectivity/ota-connectivity.css';
import './operatorHome.css';

/** Home page of Staff and Manager: the work each role does, in one place. Navigation only; the backend decides every permission. */
export default function OperatorHomePage({ role }) {
  const { status } = useAuth();
  const http = useHttp();
  const { language } = useLanguage();
  const vi = language === 'vi';
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / HOME"
        message={vi ? 'Đăng nhập bằng tài khoản Staff hoặc Manager để vào trang chủ.' : 'Sign in with a Staff or Manager account to open your home page.'}
      />
    );
  }
  if (profile.loading) return <Loading />;
  if (profile.error) return <ErrorNotice error={profile.error} onRetry={profile.reload} />;

  const actualRole = profile.data?.role;
  // Someone who is not Staff or Manager has no internal home; a Staff or Manager opening the other role's home goes to their own.
  if (actualRole !== 'STAFF' && actualRole !== 'MANAGER') return <Navigate to="/" replace />;
  if (actualRole !== role) return <Navigate to={roleHomePath(actualRole)} replace />;

  const sections = operatorSections(role);
  const name = profile.data?.fullName || profile.data?.email || '';
  const title = vi
    ? (role === 'MANAGER' ? 'Trang chủ Manager' : 'Trang chủ nhân viên')
    : (role === 'MANAGER' ? 'Manager home' : 'Staff home');
  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="ota-topline">MLAND / HOME <span>● Backend-authorized</span></div>
        <header className="ota-page-heading">
          <div>
            <p className="operator-home-hello">{vi ? 'Xin chào' : 'Hello'}{name ? `, ${name}` : ''}</p>
            <h1>{title}<span className="operator-home-role">{role}</span></h1>
            <p>{vi ? 'Các việc bạn làm hằng ngày, gom một chỗ.' : 'The work you do every day, in one place.'}</p>
          </div>
        </header>
        {sections.map(section => (
          <section key={section.id} aria-labelledby={`operator-home-${section.id}`}>
            <h2 id={`operator-home-${section.id}`} className="operator-home-section">{vi ? section.vi : section.en}</h2>
            <div className="operator-home-grid">
              {section.items.map(item => (
                <Link key={item.to} className="operator-home-card" to={item.to}>
                  <strong>{vi ? item.vi : item.en}</strong>
                  <span>{vi ? item.descVi : item.descEn}</span>
                </Link>
              ))}
            </div>
          </section>
        ))}
      </main>
    </div>
  );
}
