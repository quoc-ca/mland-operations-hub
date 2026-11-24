import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { newCommandId } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import { LIMITS, formatStars, validateBodyText } from '../rules';
import ReviewPhotoPicker from './ReviewPhotoPicker';
import SourceHeader from './SourceHeader';
import StarPicker from './StarPicker';

const sourceKey = source => `${source.sourceType}:${source.sourceReference}`;
const blank = () => ({ rating: 5, bodyText: '', images: [], error: '', failure: null, key: newCommandId() });

/**
 * "Đánh giá sản phẩm" for one retail order (session 2026-11-23, from "Đánh giá" on a delivered order in "Đơn mua"):
 * every product of the order not reviewed yet gets its own block — photo, name and size on top, then stars, text and
 * photos — and one "Hoàn thành" sends them all. Each product is its own review (one per order line, POST /reviews);
 * a product whose review fails stays on the screen with its error and can be sent again with the same key.
 */
export default function OrderReviewPage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const navigate = useNavigate();
  const { orderId } = useParams();

  const resource = useResource(() => api.listEligibleSources(), []);
  const lines = useMemo(() => (resource.data?.items || resource.data?.sources || [])
    .filter(source => source.targetType === 'RETAIL_PRODUCT' && String(source.orderId) === String(orderId)), [resource.data, orderId]);
  const pending = lines.filter(source => !source.alreadyReviewed && !source.reviewId && source.eligible !== false);
  const reviewed = lines.filter(source => source.review);

  const [drafts, setDrafts] = useState({});
  const [anonymous, setAnonymous] = useState(false);
  const [sending, setSending] = useState(false);
  const [partial, setPartial] = useState(false);

  useEffect(() => {
    setDrafts(current => {
      const next = { ...current };
      pending.forEach(source => { if (!next[sourceKey(source)]) next[sourceKey(source)] = blank(); });
      return next;
    });
  }, [resource.data]); // eslint-disable-line react-hooks/exhaustive-deps

  const update = (key, change) => setDrafts(current => ({ ...current, [key]: { ...current[key], ...change } }));

  async function submit(event) {
    event.preventDefault();
    let invalid = false;
    pending.forEach(source => {
      const draft = drafts[sourceKey(source)];
      const error = validateBodyText(draft.bodyText.trim());
      if (error) { invalid = true; update(sourceKey(source), { error: t(error) }); }
    });
    if (invalid) return;

    setSending(true);
    setPartial(false);
    let failed = false;
    for (const source of pending) {
      const key = sourceKey(source);
      const draft = drafts[key];
      try {
        // eslint-disable-next-line no-await-in-loop
        await api.createReview(draft.key, {
          sourceType: source.sourceType,
          sourceReference: source.sourceReference,
          targetType: source.targetType,
          targetReference: source.targetReference,
          rating: Number(draft.rating),
          bodyText: draft.bodyText.trim(),
          imageUrls: draft.images.map(img => img.dataUrl),
          ...(anonymous ? { anonymous: true } : {}),
        }, true);
        update(key, { failure: null, error: '' });
      } catch (failure) {
        failed = true;
        update(key, { failure });
      }
    }
    setSending(false);
    if (failed) {
      setPartial(true);
      resource.reload();
      return;
    }
    navigate('/reviews?tab=reviewed', { state: { notice: t('orderReviewDone') } });
  }

  return (
    <div className="review-container review-theme">
      <header className="page-header">
        <h1>{t('orderReviewTitle')}</h1>
      </header>

      {resource.loading && !resource.data && <Loading />}
      <ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload} />

      {resource.data && pending.length === 0 && (
        <div className="empty-state">
          <p>{t('orderReviewNothing')}</p>
          <Link className="button outline" to="/reviews?tab=reviewed">{t('seeMyReviews')}</Link>
        </div>
      )}

      {pending.length > 0 && (
        <form className="review-order-form" onSubmit={submit}>
          {partial && <div className="notice error" role="alert">{t('orderReviewPartial')}</div>}
          {pending.map(source => {
            const key = sourceKey(source);
            const draft = drafts[key] || blank();
            return (
              <section key={key} className="review-panel review-order-item" aria-label={source.label || t('sourceRetail')}>
                <SourceHeader source={source} />
                <ErrorNotice error={draft.failure} messages={reviewManagementMessages} />
                {draft.error && <div className="notice error" role="alert">{draft.error}</div>}
                <div className="field">
                  <span className="field-label">{t('ratingShort')}</span>
                  <StarPicker value={draft.rating} onChange={value => update(key, { rating: value })} disabled={sending} label={`${t('ratingShort')} — ${source.label || ''}`} />
                </div>
                <div className="field">
                  <textarea
                    value={draft.bodyText}
                    onChange={e => update(key, { bodyText: e.target.value, error: '' })}
                    placeholder={t('bodyPlaceholder')}
                    aria-label={`${t('bodyText')} — ${source.label || ''}`}
                    rows={4}
                    disabled={sending}
                  />
                  <span className="muted small">{draft.bodyText.length}/{LIMITS.bodyTextMax.toLocaleString()}</span>
                </div>
                <ReviewPhotoPicker
                  images={draft.images}
                  onChange={change => setDrafts(current => ({
                    ...current,
                    [key]: { ...current[key], images: typeof change === 'function' ? change(current[key].images) : change },
                  }))}
                  onError={message => update(key, { error: message })}
                  disabled={sending}
                  testId={`review-image-input-${source.sourceReference}`}
                />
              </section>
            );
          })}

          <div className="field">
            <label className="check-row">
              <input type="checkbox" checked={anonymous} onChange={e => setAnonymous(e.target.checked)} disabled={sending} />{' '}
              {t('anonymousLabel')}
            </label>
            <span className="muted small">{t('anonymousHint')}</span>
          </div>

          <div className="review-actions">
            <button type="button" className="button outline" onClick={() => navigate(-1)} disabled={sending}>{t('back')}</button>
            <button type="submit" className="button primary" disabled={sending}>
              {sending ? t('orderReviewSubmitting') : t('orderReviewSubmit')}
            </button>
          </div>
        </form>
      )}

      {reviewed.length > 0 && (
        <div className="review-order-reviewed">
          <h2>{t('tabReviewed')}</h2>
          {reviewed.map(source => (
            <div key={sourceKey(source)} className="review-pending-item">
              <SourceHeader source={source} />
              <span className="review-stars">{formatStars(source.review.rating)}</span>
              <Link className="button outline small" to={`/reviews/${encodeURIComponent(source.review.id)}`}>{t('open')}</Link>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
