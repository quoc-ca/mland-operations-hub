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
import ProductReviewSection from './ProductReviewSection';

/**
 * 03 · Product Details (/products/:id)
 * Follows Figma Page 01 · Public & Guest (Node 521:481 Laptop & 521:546 Phone)
 */
export default function PublicProductDetailPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useCatalogueApi();
  const { productId } = useParams();
  const product = useResource(() => api.getPublicProduct(productId), [productId]);
  const [imageIndex, setImageIndex] = useState(0);
  const programs = usePublicProductPrograms();

  if (product.loading) {
    return (
      <main className="container public-page pm-theme pm-public">
        <Loading />
      </main>
    );
  }

  if (product.error) {
    const missing = product.error.status === 404;
    return (
      <main className="container public-page pm-theme pm-public">
        <h1>{missing ? t('public.notFound') : t('public.loadFailed')}</h1>
        {!missing && <ErrorNotice error={product.error} messages={messages} />}
        <div className="actions">
          {!missing && (
            <button className="button primary" type="button" onClick={product.reload}>
              {t('retry')}
            </button>
          )}
          <Link className="button outline" to="/products">
            {t('public.backToList')}
          </Link>
        </div>
      </main>
    );
  }

  const data = product.data;
  const images = [...(data.images || [])].sort((a, b) => a.position - b.position);
  const image = images[Math.min(imageIndex, images.length - 1)];
  const offers = offersForProduct(data, programs);
  const primarySku = data.variants?.[0]?.sku || `RNG-${data.id}`;

  return (
    <main className="container public-page pm-theme pm-public pm-detail-page" data-node-id="521:481">
      {/* Breadcrumb Navigation (Node 521:496) */}
      <nav className="pm-detail-breadcrumb" aria-label="Đường dẫn trang" data-node-id="521:496">
        <Link to="/">Trang chủ</Link>
        <span className="breadcrumb-separator">/</span>
        <Link to="/products">{t('public.eyebrow')}</Link>
        {data.category && (
          <>
            <span className="breadcrumb-separator">/</span>
            <span>{data.category.name}</span>
          </>
        )}
        <span className="breadcrumb-separator">/</span>
        <span className="breadcrumb-current">{data.name}</span>
      </nav>

      {/* Main Product Overview Grid (Node 539:1701) */}
      <div className="detail-grid pm-detail-grid" data-node-id="539:1701" data-name="Section_Product_Overview">
        {/* Left: Gallery Box (Node 539:1702) */}
        <div className="pm-gallery-box" data-node-id="539:1702" data-name="Box_Product_Gallery">
          <div className="pm-main-image-wrap" data-node-id="521:497">
            {image ? (
              <img
                className="detail-image pm-main-image"
                src={publicImageUrl(image.url)}
                alt={image.altText || data.name}
              />
            ) : (
              <div className="pm-detail-image-placeholder" data-node-id="521:498">
                <span className="placeholder-icon">💍</span>
                <p className="placeholder-label">img-holder · Main Gallery Hi-res (Bạc 925)</p>
              </div>
            )}
          </div>

          {images.length > 1 && (
            <div className="thumbs pm-detail-thumbs" role="group" aria-label={t('public.images')} data-node-id="539:1703">
              {images.map((item, index) => (
                <button
                  key={item.id}
                  type="button"
                  className={index === imageIndex ? 'thumb active pm-thumb-btn' : 'thumb pm-thumb-btn'}
                  aria-pressed={index === imageIndex}
                  onClick={() => setImageIndex(index)}
                >
                  <img src={publicImageUrl(item.url)} alt={item.altText || `Mẫu ảnh ${index + 1}`} />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Right: Info & Actions Box (Node 539:1704) */}
        <div className="pm-info-box" data-node-id="539:1704" data-name="Box_Product_Info">
          <p className="eyebrow pm-material-eyebrow" data-node-id="521:507">
            {data.category?.name ? `${data.category.name.toUpperCase()} · THỦ CÔNG` : 'BẠC 925 NGUYÊN CHẤT · THỦ CÔNG'}
          </p>

          <h1 className="pm-detail-title" data-node-id="521:508">{data.name}</h1>

          <div className="pm-detail-rating-line" data-node-id="521:509">
            <span className="pm-sku-label">SKU: {primarySku}</span>
            <span className="pm-rating-stars">★★★★★ 4.9 / 5.0 (38 đánh giá)</span>
          </div>

          <div className="pm-detail-price-line" data-node-id="521:510">
            <OfferPrice offers={offers} fallback={<PriceRange range={data.priceRange} format={format} />} />
            <OfferBadges offers={offers} />
          </div>

          <p className="pre-line pm-detail-desc" data-node-id="521:511">{data.description}</p>

          <div className="pm-buy-status-wrap">
            <span className={data.canBuy ? 'status ok' : 'status'}>
              {data.canBuy ? t('public.canBuy') : t('public.cannotBuy')}
            </span>
          </div>

          <ProductOfferList offers={offers} />

          {/* Add to Cart & Buy Now Selection (Node 539:1705 / 539:1708) */}
          <AddToCartPanel
            product={data}
            sizePrice={variant => (
              <SizeOfferPrice size={offers.sizes[variant.id]} fallback={<span>{format.money(variant.appliedPrice)}</span>} />
            )}
          />

          <p className="muted small">{t('public.priceNotice')}</p>

          {/* Trust & Warranty Box (Node 521:530) */}
          <div className="pm-trust-warranty-box" data-node-id="521:530" data-name="Box_Trust_Warranty">
            <p className="trust-line" data-node-id="521:531">
              ✨ <strong>Bảo hành phôi bạc 925 trọn đời</strong> &nbsp;|&nbsp; 🚚 <strong>Miễn phí ship toàn quốc</strong>
            </p>
            <p className="trust-line">
              💎 <strong>Đánh bóng làm mới miễn phí</strong> tại xưởng Đà Nẵng &amp; TP.HCM
            </p>
          </div>
        </div>
      </div>

      {/* Specifications Section (Node 539:1709 / 521:532) */}
      <section className="pm-product-specs-section" data-node-id="539:1709" aria-labelledby="specs-title">
        <div className="pm-specs-card" data-node-id="521:532" data-name="Box_Specifications">
          <h2 id="specs-title" className="pm-specs-heading" data-node-id="521:533">
            THÔNG SỐ KỸ THUẬT &amp; CHẾ TÁC
          </h2>
          <ul className="pm-specs-list" data-node-id="521:534">
            <li>• <strong>Chất liệu:</strong> Bạc 925 tiêu chuẩn quốc tế (không xi mạ gây kích ứng da)</li>
            <li>• <strong>Bản nhẫn:</strong> Rộng 4.2mm, dày 1.5mm (tùy chỉnh theo dáng tay)</li>
            <li>• <strong>Hoàn thiện:</strong> Gò nhám mờ Sandblasted kết hợp vát cạnh High-polish</li>
            <li>• <strong>Khắc laser:</strong> Hỗ trợ khắc tên, ngày kỷ niệm hoặc ký tự riêng miễn phí</li>
            <li>• <strong>Xuất xứ:</strong> Xưởng kim hoàn M-Land Đà Nẵng</li>
          </ul>
        </div>
      </section>

      {/* Product Reviews Section */}
      <ProductReviewSection productId={data.id} productName={data.name} />
    </main>
  );
}
