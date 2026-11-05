import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useOutletContext, useParams } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useCatalogueCommand } from '../../application/useCatalogueCommand';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { missingRequirements, REQUIREMENT_SECTIONS } from '../../domain/productRules';
import { ContentSection } from './sections/ContentSection';
import { DeleteSection } from './sections/DeleteSection';
import { ImagesSection } from './sections/ImagesSection';
import { PriceSection } from './sections/PriceSection';
import { PublicationSection } from './sections/PublicationSection';
import { ReviewSection } from './sections/ReviewSection';
import { VariantsSection } from './sections/VariantsSection';
import { PageTop } from './ManagementLayout';
import { NoticeContext, PageNotice } from './notices';
import { ProductStateBadge } from './ProductListPage';

/**
 * Product detail of the management area. Every section sends its own command with the version shown here;
 * after a write (or a 409) the product is read again, while the inputs of the section keep what was typed.
 */
export default function ProductDetailPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useCatalogueApi();
  const { productId } = useParams();
  const location = useLocation();
  const product = useResource(() => api.getProduct(productId), [productId]);
  const [notice, setNotice] = useState(location.state?.notice || null);
  // Set once a submit or publish was refused for missing requirements: from then on each lacking section is marked
  // red, and the mark goes away by itself as soon as the reloaded product has what was missing.
  const [checkMissing, setCheckMissing] = useState(false);
  const setCrumb = useOutletContext()?.setCrumb;
  const name = product.data?.name;
  useEffect(() => {
    if (!setCrumb) return undefined;
    setCrumb(name || null);
    return () => setCrumb(null);
  }, [setCrumb, name]);

  if (product.loading && !product.data) return <Loading />;
  if (!product.data) {
    return (
      <div className="workspace-card">
        <ErrorNotice error={product.error} messages={messages} />
        <div className="actions">
          {product.error?.status !== 404 && <button type="button" className="button primary" onClick={product.reload}>{t('retry')}</button>}
          <Link className="button outline" to="/catalogue-management/products">{t('detail.backToList')}</Link>
        </div>
      </div>
    );
  }

  const data = product.data;
  const deleted = Boolean(data.deletedAt);
  const missing = checkMissing ? missingRequirements(data) : {};
  const flagMissing = () => {
    setCheckMissing(true);
    const first = Object.keys(REQUIREMENT_SECTIONS).find(section => missingRequirements(data)[section]);
    if (first) document.getElementById(REQUIREMENT_SECTIONS[first])?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  };
  const context = { product: data, api, reload: product.reload, setNotice, deleted, missing, flagMissing };

  return (
    <NoticeContext.Provider value={notice}>
      <PageTop eyebrow={t('nav.group')} title={data.name}>
        <Link className="button outline" to="/catalogue-management/products">{t('detail.backToList')}</Link>
      </PageTop>
      <PageNotice notice={notice} />
      {product.error && <ErrorNotice error={product.error} messages={messages} onRetry={product.reload} />}
      <section className="workspace-card summary" aria-labelledby="summary-title">
        <h2 id="summary-title">{t('detail.state')}</h2>
        <dl className="facts">
          <div><dt>{t('detail.state')}</dt><dd><ProductStateBadge product={data} /></dd></div>
          <div><dt>{t('field.category')}</dt><dd>{data.category ? `${data.category.name}${data.category.active ? '' : ` (${t('category.inactive')})`}` : '—'}</dd></div>
          <div><dt>{t('detail.firstPublishedAt')}</dt><dd>{format.dateTime(data.firstPublishedAt)}</dd></div>
          <div><dt>{t('detail.version')}</dt><dd>{data.version} / {t('detail.contentVersion', { version: data.contentVersion })}</dd></div>
        </dl>
        {deleted && <p className="notice error">{t('detail.deletedNotice', { at: format.dateTime(data.deletedAt) })}</p>}
      </section>
      <ContentSection {...context} />
      <ImagesSection {...context} />
      <VariantsSection {...context} />
      <PriceSection {...context} />
      {!data.firstPublishedAt && <ReviewSection {...context} />}
      <PublicationSection {...context} />
      <DeleteSection {...context} />
    </NoticeContext.Provider>
  );
}

/** Red line under a section heading saying what this section still lacks (see missingRequirements). */
export function MissingNote({ messageKey }) {
  const t = useT(messages);
  if (!messageKey) return null;
  return <p className="field-error missing-note" role="alert">{t(messageKey)}</p>;
}

export const sectionClass = flagged => `workspace-card${flagged ? ' missing' : ''}`;

const noticeFor = (value, result) => (typeof value === 'function' ? value(result) : value);

/**
 * One command of a section: runs with a fresh key, reloads the product after success and then shows the notice. The
 * notice is a message key, a notice object (notices.jsx) or a function of the committed result returning one. A lost
 * answer is checked against its receipt before anything is resent (useCatalogueCommand, FR-028).
 * Returns the command so the section can show its error (Retry = same key, Load latest = reload).
 */
export function useProductCommand({ reload, setNotice }) {
  const command = useCatalogueCommand();
  const { run: runCommand, retry: retryCommand } = command;
  const success = useRef(null);
  const run = useCallback(async (send, notice, action) => {
    setNotice(null);
    success.current = notice;
    const outcome = await runCommand(send, action);
    if (outcome.ok) { await reload(); setNotice(noticeFor(notice, outcome.result)); }
    return outcome;
  }, [runCommand, reload, setNotice]);
  const retry = useCallback(async () => {
    const outcome = await retryCommand();
    if (outcome?.ok) { await reload(); setNotice(noticeFor(success.current, outcome.result)); }
    return outcome;
  }, [retryCommand, reload, setNotice]);
  return { ...command, run, retry };
}
