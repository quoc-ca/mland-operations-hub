import React, { useEffect, useId, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { signInPath } from '../../../../shared/auth/SignInLink';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, useErrorText, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { CartIcon } from './QuickAddDialog';
import { SizeGuideDialog } from './SizeGuideDialog';

/** FE-08 takes 1–99 per cart line (AddCartLineRequest). */
const MAX_LINE = 99;

const count = value => {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
};

/**
 * How many of a size can be bought now (public `availableQuantity`, approved 2026-11-14): at the chosen branch when the
 * size is sold at branches, otherwise the size's own figure (no branch chosen yet, or a size not assigned to branches,
 * which sells at every active branch from its total). A size not on sale has none.
 */
export function stockOf(variant, locationId) {
  if (!variant?.saleEnabled) return 0;
  if (!locationId || !variant.assignedToBranches) return count(variant.availableQuantity);
  const atBranch = (variant.branches || []).find(branch => branch.locationId === locationId);
  return atBranch ? count(atBranch.availableQuantity) : 0;
}

/**
 * T072 hand-off to FE-08 (agreed with Bùi Trọng Thịnh, 2026-11-01): the Member picks a size, a branch and a quantity
 * and "Thêm Vào Giỏ Hàng" calls FE-08's POST /api/v1/members/me/ready-ring-cart/lines with { variantId, locationId,
 * quantity } only — no price, no product id; FE-08 re-checks price and availability. Success opens /member/cart.
 * "Mua Ngay" makes the same call, then opens FE-08's checkout for that one cart line (`state.cartLineIds`), or the cart
 * when the line cannot be found in FE-08's answer. Without a size or a branch, a click only asks for it; nothing is
 * sent. A Guest's click goes to sign-in with returnTo carrying the choice, restored on return. Branches come from
 * GET /api/v1/product-selling-locations; if that source is unavailable the buttons stay disabled with a retry.
 *
 * Layout (2026-11-14): sizes are a 5-per-row grid of boxes. Whether a size can be chosen follows its stock at the
 * chosen branch (or anywhere, before a branch is chosen); one that cannot is faded and disabled. Changing to a branch
 * where the chosen size has none clears the size. Quantity is a − / + stepper from 1 to the stock (at most 99), next to
 * "N sản phẩm có sẵn"; it is clamped when the size or branch changes.
 *
 * @param returnPath where a Guest comes back after sign-in (default: this page); the list's quick add uses the
 *                   product page, which restores the choice from the query
 * @param onAdded    called after a successful add instead of opening the cart (the list's quick add)
 * @param sizePrice  optional render of the chosen size's price under the size boxes (the detail page passes the
 *                   price after offers)
 */
export function AddToCartPanel({ product, returnPath, onAdded, sizePrice }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  const api = useCatalogueApi();
  const { status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const branchId = useId();
  const locations = useResource(() => api.listSellingLocations(), []);
  const variants = product.variants || [];
  const buyable = variants.filter(variant => variant.saleEnabled && variant.canBuy);
  const [choice, setChoice] = useState(() => {
    const locationId = params.get('locationId') || '';
    const fromQuery = variants.find(variant => variant.id === params.get('variantId'));
    return {
      variantId: fromQuery && stockOf(fromQuery, locationId) > 0 ? fromQuery.id : '',
      locationId,
      quantity: Math.max(1, Math.min(count(params.get('quantity')), MAX_LINE)),
    };
  });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [prompt, setPrompt] = useState(null);
  const [guideOpen, setGuideOpen] = useState(false);

  // Branches that sell this product: every active selling location when a size is not yet assigned to branches,
  // otherwise those listed for its sizes. A branch where no size can be bought now is shown as sold out.
  const anyUnassigned = variants.some(variant => variant.saleEnabled && !variant.assignedToBranches);
  const branchOptions = (locations.data?.items || [])
    .filter(item => anyUnassigned || variants.some(variant => (variant.branches || []).some(branch => branch.locationId === item.id)))
    .map(item => ({ ...item, canBuy: variants.some(variant => stockOf(variant, item.id) > 0) }));
  const branchKey = branchOptions.map(item => `${item.id}:${item.canBuy}`).join(',');

  const chosenVariant = variants.find(variant => variant.id === choice.variantId);
  // Only the chosen size's stock is shown (2026-11-14): a total over every size read as the stock of one size. Before a
  // size is chosen the quantity stays at 1 and the line asks for a size.
  const stock = chosenVariant ? stockOf(chosenVariant, choice.locationId) : 0;
  const maxQuantity = chosenVariant ? Math.min(stock, MAX_LINE) : 1;

  // A branch from returnTo is kept only if something can be bought there.
  useEffect(() => {
    if (locations.data && choice.locationId && !branchOptions.some(item => item.id === choice.locationId && item.canBuy)) {
      setChoice(current => ({ ...current, locationId: '' }));
    }
  }, [locations.data, branchKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // The quantity never goes above what the chosen size (and branch) has.
  useEffect(() => {
    if (maxQuantity >= 1 && choice.quantity > maxQuantity) setChoice(current => ({ ...current, quantity: maxQuantity }));
  }, [maxQuantity]); // eslint-disable-line react-hooks/exhaustive-deps

  const pickSize = variantId => { setError(null); setPrompt(null); setChoice(current => ({ ...current, variantId })); };
  const pickBranch = event => {
    const locationId = event.target.value;
    setError(null);
    setPrompt(null);
    // The chosen size is kept only if that branch has it.
    setChoice(current => ({
      ...current, locationId,
      variantId: current.variantId && stockOf(variants.find(v => v.id === current.variantId), locationId) > 0 ? current.variantId : '',
    }));
  };
  const step = delta => setChoice(current => ({ ...current, quantity: Math.max(1, Math.min(current.quantity + delta, maxQuantity)) }));

  const sizes = (
    <div className="buy-row buy-row-top">
      <span className="buy-label">{t('cart.size')}</span>
      <div className="buy-sizes">
        <div className="buy-size-list" role="group" aria-label={t('cart.size')}>
          {variants.map(variant => {
            const canPick = stockOf(variant, choice.locationId) > 0;
            const state = canPick ? null : t(variant.saleEnabled ? 'public.soldOut' : 'public.sizeDisabled');
            const label = t('size', { size: variant.size });
            const active = variant.id === choice.variantId;
            return (
              <button key={variant.id} type="button" className={active ? 'buy-size active' : 'buy-size'}
                disabled={!canPick} aria-pressed={active}
                aria-label={state ? `${label} (${state})` : label} title={state || undefined} onClick={() => pickSize(variant.id)}>
                {variant.size}
              </button>
            );
          })}
        </div>
        {chosenVariant && sizePrice && <div className="buy-sizes-price">{sizePrice(chosenVariant)}</div>}
        <button type="button" className="size-guide-link" onClick={() => setGuideOpen(true)}>
          {t('sizeGuide.open')} <span aria-hidden="true">›</span>
        </button>
      </div>
      {guideOpen && <SizeGuideDialog onClose={() => setGuideOpen(false)} />}
    </div>
  );

  if (buyable.length === 0) return <div className="add-to-cart">{sizes}<p className="status">{t('cart.noBuyableSize')}</p></div>;

  const back = `${returnPath || location.pathname}?${new URLSearchParams({ variantId: choice.variantId, locationId: choice.locationId, quantity: String(choice.quantity) })}`;
  const go = async buyNow => {
    setError(null);
    // Nothing is sent without a size that has stock and a branch: the click only asks for what is missing.
    if (!chosenVariant || stock < 1) { setPrompt(t('cart.pickSize')); return; }
    if (!choice.locationId) { setPrompt(t('cart.pickBranch')); return; }
    setPrompt(null);
    if (status !== 'signed-in') { navigate(signInPath(back)); return; }
    setPending(true);
    try {
      const cart = await api.addCartLine({ variantId: Number(choice.variantId), locationId: Number(choice.locationId), quantity: choice.quantity });
      if (buyNow) {
        const line = (cart?.lines || []).find(item => String(item.variantId) === choice.variantId && String(item.locationId) === choice.locationId);
        if (line) navigate('/member/checkout', { state: { cartLineIds: [String(line.id)] } });
        else navigate('/member/cart');
        return;
      }
      // The quick add of the list stays on the list (onAdded); the detail page opens the cart as before.
      if (onAdded) { setPending(false); onAdded(); } else navigate('/member/cart');
    } catch (failure) {
      setError(failure);
      setPending(false);
    }
  };
  const blocked = pending || !locations.data;

  return (
    <div className="add-to-cart">
      {sizes}
      <div className="buy-row">
        <label className="buy-label" htmlFor={branchId}>{t('cart.branch')}</label>
        {/* "Chọn chi nhánh" is only the placeholder: hidden from the open list, it cannot be chosen back. */}
        <select id={branchId} className="buy-branch" value={choice.locationId} onChange={pickBranch} disabled={!locations.data}>
          <option value="" disabled hidden>{t('cart.chooseBranch')}</option>
          {branchOptions.map(item => (
            <option key={item.id} value={item.id} disabled={!item.canBuy}>
              {item.name}{item.address ? ` — ${item.address}` : ''}{item.canBuy ? '' : ` (${t('cart.soldOutAtBranch')})`}
            </option>
          ))}
        </select>
      </div>
      <div className="buy-row">
        <span className="buy-label">{t('cart.quantity')}</span>
        <div className="buy-quantity">
          <div className="qty-stepper">
            <button type="button" aria-label={t('cart.decrease')} disabled={choice.quantity <= 1} onClick={() => step(-1)}>−</button>
            <input readOnly tabIndex={-1} aria-label={t('cart.quantity')} value={String(choice.quantity)} />
            <button type="button" aria-label={t('cart.increase')} disabled={choice.quantity >= maxQuantity} onClick={() => step(1)}>+</button>
          </div>
          <span className="qty-stock">{chosenVariant ? t('cart.inStock', { count: stock }) : t('cart.pickSizeForStock')}</span>
        </div>
      </div>
      {locations.error && <ErrorNotice error={locations.error} messages={messages} onRetry={locations.reload} />}
      {locations.data && locations.data.items.length === 0 && <p className="status">{t('cart.noBranch')}</p>}
      {prompt && <p className="buy-prompt" role="alert">{prompt}</p>}
      {error && (
        <div className="notice error" role="alert">
          <p>{error.message || errorText(error)}</p>
          {error.correlationId && <p className="muted small">{t('cart.reference', { id: error.correlationId })}</p>}
        </div>
      )}
      <div className="buy-actions">
        <button type="button" className="buy-add" disabled={blocked} onClick={() => go(false)}>
          <CartIcon />{pending ? t('cart.adding') : t('cart.add')}
        </button>
        <button type="button" className="buy-now" disabled={blocked} onClick={() => go(true)}>{t('cart.buyNow')}</button>
      </div>
    </div>
  );
}
