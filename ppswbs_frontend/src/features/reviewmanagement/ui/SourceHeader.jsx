import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ProductThumb } from '../../retail-order/ui/ProductThumb';
import { reviewManagementMessages } from '../messages';

export const isRetail = source => source?.targetType === 'RETAIL_PRODUCT';

/**
 * What is being reviewed, shown above the review: the product photo, name and size of a retail line (the product id
 * is the review target), or the workshop name. No internal reference is shown.
 */
export default function SourceHeader({ source, linkProduct = false }) {
  const t = useT(reviewManagementMessages);
  if (!source) return null;
  const retail = isRetail(source);
  const name = source.label || source.targetName || t(retail ? 'sourceRetail' : 'sourceWorkshop');
  const productPath = retail && source.targetReference ? `/products/${encodeURIComponent(source.targetReference)}` : null;
  return (
    <div className="review-source-header">
      {retail
        ? <ProductThumb className="review-source-thumb" productId={source.targetReference} />
        : <span className="review-source-thumb" aria-hidden="true">✦</span>}
      <div className="review-source-text">
        {linkProduct && productPath ? <Link to={productPath}><strong>{name}</strong></Link> : <strong>{name}</strong>}
        <span className="muted">
          {retail
            ? `${t('sizeLabel')} ${source.size ?? '—'}`
            : t('sourceWorkshop')}
        </span>
      </div>
    </div>
  );
}
