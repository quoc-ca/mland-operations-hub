import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { publicImageUrl } from '../../application/useCatalogueApi';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { AddToCartPanel } from './AddToCartPanel';
import { PriceRange } from './PriceRange';
import { OfferBadges, OfferPrice, offersForProduct, ProductOfferList, SizeOfferPrice, usePublicProductPrograms } from '../../../loyalty-promotion';
import ProductReviewSection from './ProductReviewSection';

/**
 * T071: public detail. Reading creates no cart, hold or order; only "Add to cart" (T072, {@link AddToCartPanel})
 * writes, through FE-08's cart API.
 */
export default function PublicProductDetailPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useCatalogueApi();
  const { productId } = useParams();
  const product = useResource(() => api.getPublicProduct(productId), [productId]);
  const [imageIndex, setImageIndex] = useState(0);
  const programs = usePublicProductPrograms();
  const [chosenVariant, setChosenVariant] = useState(null);

  // Opening a product (from the list, a card or another product) starts at the top of the page, not at the scroll
  // position the previous page was left at (which landed on the reviews).
  useEffect(() => {
    setChosenVariant(null);
    if (typeof window.scrollTo === 'function') window.scrollTo(0, 0);
  }, [productId]);

  if (product.loading) return <main className="container public-page pm-theme pm-public"><Loading /></main>;
  if (product.error) {
    const missing = product.error.status === 404;
    return (
      <main className="container public-page pm-theme pm-public">
        <h1>{missing ? t('public.notFound') : t('public.loadFailed')}</h1>
        {!missing && <ErrorNotice error={product.error} messages={messages} />}
        <div className="actions">
          {!missing && <button className="button primary" type="button" onClick={product.reload}>{t('retry')}</button>}
          <Link className="button outline" to="/products">{t('public.backToList')}</Link>
        </div>
      </main>
    );
  }
  const data = product.data;
  const images = [...(data.images || [])].sort((a, b) => a.position - b.position);
  const image = images[Math.min(imageIndex, images.length - 1)];
  const offers = offersForProduct(data, programs);

  return (
    <main className="container public-page pm-theme pm-public">
      <p className="eyebrow"><Link to="/products">{t('public.eyebrow')}</Link>{data.category && ` / ${data.category.name}`}</p>
      <h1>{data.name}</h1>
      <div className="detail-grid">
        <div>
          {image ? <img className="detail-image" src={publicImageUrl(image.url)} alt={image.altText} /> : <div className="ring-visual silver" aria-hidden="true"><span /></div>}
          {images.length > 1 && (
            <div className="thumbs" role="group" aria-label={t('public.images')}>
              {images.map((item, index) => (
                <button key={item.id} type="button" className={index === imageIndex ? 'thumb active' : 'thumb'} aria-pressed={index === imageIndex} onClick={() => setImageIndex(index)}>
                  <img src={publicImageUrl(item.url)} alt={item.altText} />
                </button>
              ))}
            </div>
          )}
        </div>
        <div>
          {/* The big price is the chosen size's price (after offers); before a size is chosen it is the product's range. */}
          <div className="detail-price">
            {chosenVariant
              ? <SizeOfferPrice size={offers.sizes[chosenVariant.id]} fallback={<span className="price">{format.money(chosenVariant.appliedPrice)}</span>} />
              : <OfferPrice offers={offers} fallback={<PriceRange range={data.priceRange} format={format} />} />}
          </div>
          <OfferBadges offers={offers} />
          <p className="pre-line">{data.description}</p>
          <span className={data.canBuy ? 'status ok' : 'status'}>{data.canBuy ? t('public.canBuy') : t('public.cannotBuy')}</span>
          <ProductOfferList offers={offers} />
          {/* Sizes are boxes in the cart panel: in stock can be chosen, sold out or not on sale is faded. */}
          <AddToCartPanel product={data} onSizeChange={setChosenVariant} />
          <p className="muted small">{t('public.priceNotice')}</p>
        </div>
      </div>
      <ProductReviewSection productId={data.id} productName={data.name} />
    </main>
  );
}
