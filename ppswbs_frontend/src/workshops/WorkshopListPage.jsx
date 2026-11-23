import React, { useEffect, useState } from 'react';
import './workshopList.css';
import { listWorkshopPackages } from '../api/workshops';
import { useFormat, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { Paginator, usePaged } from '../shared/ui/Paginator';
import PackageReviewSummary from '../features/reviewmanagement/ui/PackageReviewSummary';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../features/loyalty-promotion/publicOffers';

export function WorkshopListPage() {
  const t = useT(messages);
  const format = useFormat();
  const programs = usePublicWorkshopPrograms();
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const paged = usePaged(packages, 9, packages);

  const fetchPackages = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listWorkshopPackages();
      setPackages(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.detail || err?.message || t('list.loadError'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPackages();
  }, []);

  return (
    <main className="container public-page">
      <p className="eyebrow">{t('list.eyebrow')}</p>
      <h1>{t('list.title')}</h1>
      <p className="lead">
        {t('list.lead')}
      </p>

      <div
        style={{
          margin: '20px 0 28px',
          padding: '14px 20px',
          backgroundColor: '#fefce8',
          border: '1px solid #fde047',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '1.4rem' }}>💍</span>
          <span style={{ fontSize: '14px', color: '#1e293b' }}>
            {t('list.designPrompt')}
          </span>
        </div>
        <a
          href="/workshops/PKG-COUPLE-SILVER/design"
          className="button outline"
          style={{ height: '36px', fontSize: '13px', whiteSpace: 'nowrap' }}
        >
          {t('list.designCta')}
        </a>
      </div>

      {loading && (
        <div className="empty-state">
          <p>{t('list.loading')}</p>
        </div>
      )}

      {error && !loading && (
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>
            {error}
          </p>
          <div style={{ marginTop: '16px' }}>
            <button type="button" className="button outline" onClick={fetchPackages}>
              {t('list.retry')}
            </button>
          </div>
        </div>
      )}

      {!loading && !error && packages.length === 0 && (
        <div className="empty-state">
          <p>{t('list.empty')}</p>
        </div>
      )}

      {!loading && !error && packages.length > 0 && (
        <div className="wk-grid">
          {paged.pageItems.map((pkg, idx) => {
            const sessionCount = pkg.requiredSessionCount || 1;
            const offers = offersForPackage(pkg, programs);
            return (
              <article className="wk-card" key={pkg.packageId}>
                <div className={`ring-visual ${idx % 2 === 0 ? 'gold' : 'pair'}`} aria-label={t('list.visualAria')}>
                  <span />
                </div>
                <div className="wk-body">
                  <h2 className="wk-name">{pkg.name}</h2>
                  <p className="wk-desc">{pkg.description}</p>
                  <div className="wk-chips">
                    <span className="wk-chip">{t('list.sessions', { count: sessionCount })}</span>
                    <span className="wk-chip">👥 {pkg.minParticipants === pkg.maxParticipants ? t('list.tickets', { count: pkg.maxParticipants }) : t('list.ticketsRange', { min: pkg.minParticipants, max: pkg.maxParticipants })}</span>
                    {/* Only worth saying on the card when the package spans several sessions; the package page has the full rules. */}
                    {sessionCount > 1 && pkg.sameLocationRequired && <span className="wk-chip">{t('list.sameBranch')}</span>}
                  </div>
                  {/* FR-052: price after the best automatic discount, from 006's public gate; plain price when none. */}
                  <p className="wk-price">
                    <WorkshopOfferPrice
                      offers={offers}
                      fallback={format.money(String(Math.round(Number(pkg.publicPrice || 0))))}
                    /> <small>{t('list.perPerson')}</small>
                  </p>
                  <WorkshopOfferBadges offers={offers} />
                  <div className="wk-actions">
                    <a className="button primary" href={`/workshops/${pkg.packageId}?focus=schedule`}>{t('list.choose')}</a>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
      {!loading && !error && <Paginator page={paged.page} pageCount={paged.pageCount} onChange={paged.setPage} label={t('list.pagerLabel')} />}
      <PackageReviewSummary allPackages />
    </main>
  );
}

