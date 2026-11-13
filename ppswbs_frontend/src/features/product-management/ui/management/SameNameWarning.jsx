import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

const SHOWN = 5;

/** Links to the products that already have this name: "Nhẫn bạc trơn (#19), (#21)" (at most SHOWN, then "+N"). */
export function SameNameLinks({ products }) {
  const t = useT(messages);
  return (
    <>
      {products.slice(0, SHOWN).map((product, index) => (
        <React.Fragment key={product.id}>
          {index > 0 && ', '}
          <Link className="text-link" to={`/catalogue-management/products/${encodeURIComponent(product.id)}`} target="_blank" rel="noreferrer">
            {product.name} (#{product.id})
          </Link>
        </React.Fragment>
      ))}
      {products.length > SHOWN && ` ${t('sameName.more', { count: products.length - SHOWN })}`}
    </>
  );
}

/**
 * Warning under the name of the create form: other products already have this name. Product names need not be
 * unique (spec 002, A-003), so this never blocks saving; it lets the user open them before making another one.
 */
export function SameNameWarning({ products }) {
  const t = useT(messages);
  if (!products?.length) return null;
  return (
    <div className="notice same-name" role="status">
      <strong>{t('sameName.title', { count: products.length })}</strong>{' '}
      <SameNameLinks products={products} />
      <span className="muted small same-name-hint">{t('sameName.hint')}</span>
    </div>
  );
}
