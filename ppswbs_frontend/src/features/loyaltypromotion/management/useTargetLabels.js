import { useEffect, useState } from 'react';
import { productLabel, sizeLabel } from './ProductTargetPicker';

const MAX_PAGES = 10;

/**
 * Names for a product-scope programme's targets on the Manager detail page, read from 002's management reads.
 * Products are read one by one; a size has no lookup by ID, so pages of the product list are scanned (at most
 * MAX_PAGES). Anything not found, or a failed read, simply keeps showing its ID — the programme itself never depends
 * on these names.
 */
export function useTargetLabels(api, targets) {
  const [labels, setLabels] = useState({});
  const productIds = targets?.productIds || [];
  const variantIds = targets?.variantIds || [];
  const key = `${productIds.join(',')}|${variantIds.join(',')}`;

  useEffect(() => {
    let alive = true;
    (async () => {
      const found = { productIds: {}, variantIds: {} };
      await Promise.all(productIds.map(async productId => {
        try { found.productIds[productId] = productLabel(await api.getCatalogueProduct(productId)); } catch { /* keep the ID */ }
      }));
      const missing = new Set(variantIds);
      for (let page = 0; missing.size > 0 && page < MAX_PAGES; page += 1) {
        let result;
        try { result = await api.searchCatalogueProducts({ page }); } catch { break; }
        result.items.forEach(product => (product.variants || []).forEach(variant => {
          if (missing.delete(variant.id)) found.variantIds[variant.id] = sizeLabel(product, variant);
        }));
        if ((page + 1) * Number(result.pageSize) >= Number(result.totalElements)) break;
      }
      if (alive) setLabels(found);
    })();
    return () => { alive = false; };
  }, [api, key]); // eslint-disable-line react-hooks/exhaustive-deps

  return labels;
}
