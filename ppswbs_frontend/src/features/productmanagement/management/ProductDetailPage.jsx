import React, { useCallback, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../../shared/ui/components';
import { useCatalogueApi } from '../api/useCatalogueApi';
import { messages } from '../messages';
import { ContentSection } from './sections/ContentSection';
import { DeleteSection } from './sections/DeleteSection';
import { ImagesSection } from './sections/ImagesSection';
import { PriceSection } from './sections/PriceSection';
import { PublicationSection } from './sections/PublicationSection';
import { ReviewSection } from './sections/ReviewSection';
import { VariantsSection } from './sections/VariantsSection';
import { PageTop } from './ManagementLayout';
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
  const context = { product: data, api, reload: product.reload, setNotice, deleted };

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={data.name}>
        <Link className="button outline" to="/catalogue-management/products">{t('detail.backToList')}</Link>
      </PageTop>
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      {product.error && <ErrorNotice error={product.error} messages={messages} onRetry={product.reload} />}
      <section className="workspace-card summary">
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
    </>
  );
}

/**
 * One command of a section: runs with a fresh key, reloads the product after success and shows the notice.
 * Returns the command so the section can show its error (Retry = same key, Load latest = reload).
 */
export function useProductCommand({ reload, setNotice }) {
  const command = useCommand();
  const { run: runCommand, retry: retryCommand } = command;
  const success = useRef(null);
  const run = useCallback(async (send, successKey) => {
    setNotice(null);
    success.current = successKey;
    const outcome = await runCommand(send);
    if (outcome.ok) { setNotice(successKey); await reload(); }
    return outcome;
  }, [runCommand, reload, setNotice]);
  const retry = useCallback(async () => {
    const outcome = await retryCommand();
    if (outcome?.ok) { setNotice(success.current); await reload(); }
    return outcome;
  }, [retryCommand, reload, setNotice]);
  return { ...command, run, retry };
}
