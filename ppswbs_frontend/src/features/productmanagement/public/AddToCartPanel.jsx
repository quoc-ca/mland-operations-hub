import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useErrorText, useResource } from '../../../shared/ui/components';
import { useCatalogueApi } from '../api/useCatalogueApi';
import { messages } from '../messages';

const QUANTITY = /^[1-9][0-9]?$/;

/**
 * T072 hand-off to FE-08 (agreed with Bùi Trọng Thịnh, 2026-11-01): the Member picks a size, a branch and a quantity
 * (1–99) and "Add to cart" calls FE-08's POST /api/v1/members/me/ready-ring-cart/lines with { variantId, locationId,
 * quantity } only — no price, no product id; FE-08 re-checks price and availability. Success opens /member/cart. A
 * Guest is sent to sign-in with returnTo carrying the choice so it is restored on return. Browsing creates nothing;
 * only this button writes. Errors show FE-08's message. Branches come from GET /api/v1/product-selling-locations; if
 * that source is unavailable the button stays disabled with a retry (no guessed default branch).
 */
export function AddToCartPanel({ product }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  const api = useCatalogueApi();
  const { status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const locations = useResource(() => api.listSellingLocations(), []);
  const buyable = (product.variants || []).filter(variant => variant.saleEnabled && variant.canBuy);
  const [choice, setChoice] = useState(() => ({
    variantId: buyable.some(v => v.id === params.get('variantId')) ? params.get('variantId') : buyable[0]?.id || '',
    locationId: params.get('locationId') || '',
    quantity: QUANTITY.test(params.get('quantity') || '') ? params.get('quantity') : '1',
  }));
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);

  // A branch from returnTo is kept only if it is still an active selling location.
  useEffect(() => {
    const items = locations.data?.items;
    if (items && choice.locationId && !items.some(item => item.id === choice.locationId)) {
      setChoice(current => ({ ...current, locationId: '' }));
    }
  }, [locations.data]); // eslint-disable-line react-hooks/exhaustive-deps

  if (buyable.length === 0) return <p className="status">{t('cart.noBuyableSize')}</p>;

  const set = name => event => { setError(null); setChoice(current => ({ ...current, [name]: event.target.value })); };
  const quantityOk = QUANTITY.test(choice.quantity);
  const ready = Boolean(choice.variantId && choice.locationId && quantityOk && locations.data && !pending);

  const back = `${location.pathname}?${new URLSearchParams({ variantId: choice.variantId, locationId: choice.locationId, quantity: choice.quantity })}`;
  const add = async () => {
    setPending(true);
    setError(null);
    try {
      await api.addCartLine({ variantId: Number(choice.variantId), locationId: Number(choice.locationId), quantity: Number(choice.quantity) });
      navigate('/member/cart');
    } catch (failure) {
      setError(failure);
      setPending(false);
    }
  };

  return (
    <div className="add-to-cart">
      <div className="form-grid">
        <Field label={t('cart.size')}>
          <select value={choice.variantId} onChange={set('variantId')}>
            {buyable.map(variant => <option key={variant.id} value={variant.id}>{t('size', { size: variant.size })}</option>)}
          </select>
        </Field>
        <Field label={t('cart.branch')} error={locations.data && !choice.locationId ? t('cart.chooseBranchHint') : null}>
          <select value={choice.locationId} onChange={set('locationId')} disabled={!locations.data}>
            <option value="">{t('cart.chooseBranch')}</option>
            {(locations.data?.items || []).map(item => <option key={item.id} value={item.id}>{item.name}{item.address ? ` — ${item.address}` : ''}</option>)}
          </select>
        </Field>
        <Field label={t('cart.quantity')} error={quantityOk ? null : t('cart.quantityHint')}>
          <input inputMode="numeric" value={choice.quantity} onChange={set('quantity')} />
        </Field>
      </div>
      {locations.error && <ErrorNotice error={locations.error} messages={messages} onRetry={locations.reload} />}
      {locations.data && locations.data.items.length === 0 && <p className="status">{t('cart.noBranch')}</p>}
      {error && (
        <div className="notice error" role="alert">
          <p>{error.message || errorText(error)}</p>
          {error.correlationId && <p className="muted small">{t('cart.reference', { id: error.correlationId })}</p>}
        </div>
      )}
      {status === 'signed-in'
        ? <button type="button" className="button primary" disabled={!ready} onClick={add}>{pending ? t('cart.adding') : t('cart.add')}</button>
        : <a className="button primary" href={`/#/login?returnTo=${encodeURIComponent(back)}`}>{t('cart.signInToAdd')}</a>}
    </div>
  );
}
