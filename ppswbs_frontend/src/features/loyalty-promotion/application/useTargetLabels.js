import { useEffect, useState } from 'react';
import { productLabel, publicProductLabel, publicSizeLabel, sizeLabel } from '../domain/promotionRules';

const MAX_PAGES = 10;

/**
 * Reads names for product targets. Products are read one by one; a size has no lookup by ID, so pages of the product
 * list are scanned (at most MAX_PAGES). Anything not found, or a failed read, simply keeps showing its ID — a
 * programme never depends on these names.
 */
async function readLabels({ productIds, variantIds, getProduct, listPage, productText, sizeText }) {
  const found = { productIds: {}, variantIds: {} };
  await Promise.all(productIds.map(async productId => {
    try { found.productIds[productId] = productText(await getProduct(productId)); } catch { /* keep the ID */ }
  }));
  const missing = new Set(variantIds);
  for (let page = 0; missing.size > 0 && page < MAX_PAGES; page += 1) {
    let result;
    try { result = await listPage(page); } catch { break; }
    result.items.forEach(product => (product.variants || []).forEach(variant => {
      if (missing.delete(variant.id)) found.variantIds[variant.id] = sizeText(product, variant);
    }));
    if ((page + 1) * Number(result.pageSize) >= Number(result.totalElements)) break;
  }
  return found;
}

function useLabels(targets, sources, api) {
  const [labels, setLabels] = useState({});
  const productIds = [...new Set(targets.flatMap(item => item?.productIds || []))];
  const variantIds = [...new Set(targets.flatMap(item => item?.variantIds || []))];
  const key = `${productIds.join(',')}|${variantIds.join(',')}`;

  useEffect(() => {
    if (!productIds.length && !variantIds.length) return undefined;
    let alive = true;
    readLabels({ productIds, variantIds, ...sources }).then(found => { if (alive) setLabels(found); });
    return () => { alive = false; };
  }, [api, key]); // eslint-disable-line react-hooks/exhaustive-deps

  return labels;
}

/** Names for a product-scope programme's targets on the Manager detail page, read from 002's management reads. */
export function useTargetLabels(api, targets) {
  return useLabels([targets], {
    getProduct: api.getCatalogueProduct,
    listPage: page => api.searchCatalogueProducts({ page }),
    productText: productLabel,
    sizeText: sizeLabel,
  }, api);
}

/**
 * Names for the targets of the offers on a public or Member page, read from 002's public catalogue (no sign-in).
 * Only published products are found there; a hidden or deleted target keeps showing its ID.
 */
export function usePublicTargetLabels(api, promotions) {
  return useLabels((promotions || []).filter(item => item?.scope === 'PRODUCT').map(item => item.targets), {
    getProduct: api.getPublicProduct,
    listPage: page => api.listPublicProducts({ page }),
    productText: publicProductLabel,
    sizeText: publicSizeLabel,
  }, api);
}
