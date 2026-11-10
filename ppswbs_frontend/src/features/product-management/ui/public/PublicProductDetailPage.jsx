import React, { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { publicImageUrl } from '../../application/useCatalogueApi';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { AddToCartPanel } from './AddToCartPanel';
import { PriceRange } from './PriceRange';
import { OfferBadges, OfferPrice, offersForProduct, ProductOfferList, SizeOfferPrice, usePublicProductPrograms } from '../../../loyalty-promotion';

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
          <OfferPrice offers={offers} fallback={<PriceRange range={data.priceRange} format={format} />} />
          <OfferBadges offers={offers} />
          <p className="pre-line">{data.description}</p>
          <span className={data.canBuy ? 'status ok' : 'status'}>{data.canBuy ? t('public.canBuy') : t('public.cannotBuy')}</span>
          <h2 className="section-title">{t('public.sizes')}</h2>
          <ul className="size-list">
            {(data.variants || []).map(variant => (
              <li key={variant.id} className={variant.saleEnabled && variant.canBuy ? '' : 'unavailable'}>
                <strong>{t('size', { size: variant.size })}</strong>
                <SizeOfferPrice size={offers.sizes[variant.id]} fallback={<span>{format.money(variant.appliedPrice)}</span>} />
                <span className="muted">{!variant.saleEnabled ? t('public.sizeDisabled') : variant.canBuy ? t('public.canBuy') : t('public.soldOut')}</span>
              </li>
            ))}
          </ul>
          <ProductOfferList offers={offers} />
          <AddToCartPanel product={data} />
          <p className="muted small">{t('public.priceNotice')}</p>
        </div>
      </div>
    </main>
  );
}
