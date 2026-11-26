import React, { useEffect, useRef, useState } from 'react';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { publicImageUrl } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { AddToCartPanel } from './AddToCartPanel';
import { PriceRange } from './PriceRange';
import { OfferPrice, offersForProduct, SizeOfferPrice } from '../../../loyalty-promotion';

/**
 * Quick add from the public list (TikTok/Shopee-like cart button on a card): the same size / branch / quantity choice
 * and FE-08 call as the product page ({@link AddToCartPanel}), in a dialog over the list. Success closes it and the
 * list shows "added"; a Guest signs in and comes back to the product page with the choice kept. As on the product page,
 * the big price at the top is the chosen size's price after FE-06's offers; before a size is chosen it is the product's
 * price. Offers are priced for the chosen branch like the product page (006 FR-048), so a voucher limited to other
 * branches drops out once a branch is chosen (request 2026-11-26).
 */
export function QuickAddDialog({ product, programs, onClose, onAdded }) {
  const t = useT(messages);
  const format = useFormat();
  const closeRef = useRef(null);
  const [chosenVariant, setChosenVariant] = useState(null);
  const [chosenBranch, setChosenBranch] = useState('');
  const offers = offersForProduct(product, programs, { branchId: chosenBranch });
  useEffect(() => { closeRef.current?.focus(); }, []);
  const image = [...(product.images || [])].sort((a, b) => a.position - b.position)[0];
  return (
    <div className="dialog-backdrop quick-add-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="dialog quick-add" role="dialog" aria-modal="true" aria-label={t('quickAdd.title', { name: product.name })}
        onKeyDown={event => { if (event.key === 'Escape') onClose(); }}>
        <div className="quick-add-head">
          {image ? <img src={publicImageUrl(image.url)} alt="" /> : <div className="ring-visual silver" aria-hidden="true" />}
          <div>
            <strong>{product.name}</strong>
            {chosenVariant
              ? <SizeOfferPrice size={offers?.sizes?.[chosenVariant.id]} fallback={<span className="price">{format.money(chosenVariant.appliedPrice)}</span>} />
              : <OfferPrice offers={offers} fallback={<PriceRange range={product.priceRange} format={format} />} />}
          </div>
          <button type="button" ref={closeRef} className="quick-add-close" onClick={onClose} aria-label={t('quickAdd.close')}>×</button>
        </div>
        <AddToCartPanel product={product} returnPath={`/products/${encodeURIComponent(product.id)}`} onAdded={onAdded}
          onSizeChange={setChosenVariant} onBranchChange={setChosenBranch} />
      </div>
    </div>
  );
}

/** Cart icon (stroke follows the text colour). */
export function CartIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" />
      <path d="M2.5 3.5h2.6l2.4 11.2a1.6 1.6 0 0 0 1.6 1.3h8.3a1.6 1.6 0 0 0 1.6-1.2l1.6-6.8H6.2" />
      <path d="M13.5 8.5v4M11.5 10.5h4" />
    </svg>
  );
}
