import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { publicImageUrl } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { PriceRange } from './PriceRange';
import { CartIcon, QuickAddDialog } from './QuickAddDialog';
import { OfferBadges, OfferPrice, offersForProduct, usePublicProductPrograms } from '../../../loyalty-promotion';
import '../product-management.css';

/**
 * Public product cards, shared by 002's catalogue (/products) and the home page's Ready Rings: price after the best
 * automatic discount (006, original struck through), offer badges, stock label and a cart button that opens the
 * quick add. "Added" is shown above the cards; the shopper stays on the page.
 *
 * @param products public products (GET /api/v1/products items)
 * @param standalone true outside 002's pages (home page): adds the 002 theme and shows the cards as a sideways
 *                   strip (three per view on a laptop, like the workshop cards above it) with ‹ › buttons, swipe and
 *                   a link to the whole catalogue
 */
export function PublicProductGrid({ products, standalone = false }) {
  const t = useT(messages);
  const programs = usePublicProductPrograms();
  const [added, setAdded] = useState(null);
  const notice = added && (
    <p className="notice success cart-added" role="status">
      {t('quickAdd.added', { name: added.name })} <Link className="text-link" to="/member/cart">{t('quickAdd.viewCart')}</Link>
    </p>
  );
  const cards = products.map(product => <PublicProductCard key={product.id} product={product} programs={programs} onAdded={setAdded} />);
  if (!standalone) return <>{notice}<div className="catalogue-grid pm-catalogue-grid">{cards}</div></>;
  return (
    <div className="pm-theme pm-public product-grid-standalone">
      {notice}
      <ProductStrip>{cards}</ProductStrip>
    </div>
  );
}

/** Sideways strip with ‹ › buttons (one view per press), swipe / trackpad scroll and snapping to a card. */
function ProductStrip({ children }) {
  const t = useT(messages);
  const track = useRef(null);
  const [ends, setEnds] = useState({ start: true, end: false });
  const measure = useCallback(() => {
    const element = track.current;
    if (!element) return;
    setEnds({ start: element.scrollLeft <= 2, end: element.scrollLeft + element.clientWidth >= element.scrollWidth - 2 });
  }, []);
  useEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure, children]);
  const move = direction => track.current?.scrollBy({ left: direction * track.current.clientWidth, behavior: 'smooth' });
  return (
    <div className="product-strip">
      <div className="product-strip-tools">
        <Link className="text-link" to="/products">{t('strip.all')}</Link>
        <span>
          <button type="button" className="strip-arrow" onClick={() => move(-1)} disabled={ends.start} aria-label={t('strip.previous')}>‹</button>
          <button type="button" className="strip-arrow" onClick={() => move(1)} disabled={ends.end} aria-label={t('strip.next')}>›</button>
        </span>
      </div>
      <div className="product-strip-track" ref={track} onScroll={measure} role="list" aria-label={t('strip.label')}>
        {React.Children.map(children, child => <div role="listitem" className="product-strip-item">{child}</div>)}
      </div>
    </div>
  );
}

/**
 * A card: follows Figma Card_Product_01 (Node 521:357)
 * Image and name open the product page; the cart button opens the quick add over the page.
 */
export function PublicProductCard({ product, programs, onAdded }) {
  const t = useT(messages);
  const format = useFormat();
  const [adding, setAdding] = useState(false);
  const image = [...(product.images || [])].sort((a, b) => a.position - b.position)[0];
  const offers = offersForProduct(product, programs);
  const to = `/products/${encodeURIComponent(product.id)}`;
  const price = <OfferPrice offers={offers} fallback={<PriceRange range={product.priceRange} format={format} />} />;
  const sku = product.variants?.[0]?.sku || product.code || `RNG-00${product.id}`;

  return (
    <article className="ring-card product-card pm-figma-product-card" data-node-id="521:357">
      <Link className="product-card-link pm-card-link" to={to}>
        <div className="pm-card-thumb-wrap">
          {image ? (
            <img className="product-image pm-card-image" src={publicImageUrl(image.url)} alt={image.altText || product.name} />
          ) : (
            <div className="pm-card-placeholder">
              <span className="pm-card-placeholder-icon">💍</span>
              <span className="pm-card-placeholder-text">img-holder · Product Preview</span>
            </div>
          )}
        </div>
        <div className="pm-card-meta-line">
          <span className="pm-card-sku">SKU: {sku}</span>
          {product.category && <span className="badge pm-card-badge">{product.category.name}</span>}
        </div>
        <strong className="pm-card-title">{product.name}</strong>
      </Link>

      <div className="product-card-info pm-card-pricing">
        <div className="pm-price-wrap">{price}</div>
        <OfferBadges offers={offers} />
        <p className="pm-card-material">Bạc 925 nguyên chất</p>
      </div>

      <div className="product-card-foot pm-card-actions">
        <Link to={to} className="pm-view-detail-btn" data-node-id="521:365">
          Xem Chi Tiết →
        </Link>
        <button
          type="button"
          className="cart-button pm-cart-btn"
          disabled={!product.canBuy}
          onClick={() => setAdding(true)}
          aria-label={t('quickAdd.open', { name: product.name })}
          title={product.canBuy ? t('cart.add') : t('public.cannotBuy')}
        >
          <CartIcon />
        </button>
      </div>

      <div className="pm-card-stock-status">
        <span className={product.canBuy ? 'status ok' : 'status'}>
          {product.canBuy ? t('public.canBuy') : t('public.cannotBuy')}
        </span>
      </div>

      {adding && (
        <QuickAddDialog
          product={product}
          price={price}
          offers={offers}
          onClose={() => setAdding(false)}
          onAdded={() => {
            setAdding(false);
            onAdded?.(product);
          }}
        />
      )}
    </article>
  );
}
