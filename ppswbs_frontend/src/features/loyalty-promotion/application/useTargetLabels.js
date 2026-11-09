import { useEffect, useState } from 'react';
import { productLabel, publicProductLabel, publicSizeLabel, sizeLabel } from '../domain/promotionRules';

const MAX_PAGES = 10;

/**
 * Reads names for product targets. Products are read one by one; a size has no lookup by ID, so pages of the product
 * list are scanned (at most MAX_PAGES). Anything not found, or a failed read, simply keeps showing its ID — a
 * programme never depends on these names.
 */
async function readProductLabels({ productIds, variantIds, getProduct, listPage, productText, sizeText }, found) {
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
}

/**
 * Names for workshop targets, read without sign-in: each package from 003's public package read, and branches from
 * the active branch list (one read). A draft/withdrawn package or an inactive branch is not found there and keeps
 * showing its number.
 */
async function readWorkshopLabels({ packageIds, branchIds }, api, found) {
  await Promise.all(packageIds.map(async packageId => {
    try {
      const pack = await api.getPublicWorkshopPackage(packageId);
      if (pack?.name) found.packageIds[packageId] = pack.name;
    } catch { /* keep the ID */ }
  }));
  if (!branchIds.length) return;
  try {
    const branches = await api.listPublicBranches();
    (branches?.items || []).forEach(branch => {
      if (branchIds.includes(String(branch.id))) found.branchIds[branch.id] = branch.name;
    });
  } catch { /* keep the IDs */ }
}

const unique = (targets, key) => [...new Set(targets.flatMap(item => (item?.[key] || []).map(String)))];

function useLabels(targets, sources, api) {
  const [labels, setLabels] = useState({});
  const ids = {
    productIds: unique(targets, 'productIds'),
    variantIds: unique(targets, 'variantIds'),
    packageIds: unique(targets, 'packageIds'),
    branchIds: unique(targets, 'branchIds'),
  };
  const key = Object.values(ids).map(list => list.join(',')).join('|');

  useEffect(() => {
    if (!Object.values(ids).some(list => list.length)) return undefined;
    let alive = true;
    const found = { productIds: {}, variantIds: {}, packageIds: {}, branchIds: {} };
    Promise.all([
      readProductLabels({ ...ids, ...sources }, found),
      readWorkshopLabels(ids, api, found),
    ]).then(() => { if (alive) setLabels(found); });
    return () => { alive = false; };
  }, [api, key]); // eslint-disable-line react-hooks/exhaustive-deps

  return labels;
}

/**
 * Names of active branches (Session 2026-11-09: "Áp dụng tại" of a product voucher), read once without sign-in when
 * some ID is asked for; a failed read or an inactive branch leaves the name out.
 */
export function usePublicBranchNames(api, branchIds) {
  const [names, setNames] = useState({});
  const key = [...new Set((branchIds || []).map(String))].sort().join(',');
  useEffect(() => {
    if (!key) return undefined;
    let alive = true;
    api.listPublicBranches()
      .then(list => { if (alive) setNames(Object.fromEntries((list?.items || []).map(branch => [String(branch.id), branch.name]))); })
      .catch(() => { /* keep counts only */ });
    return () => { alive = false; };
  }, [api, key]);
  return names;
}

/** Names for a programme's targets on the Manager detail page: products from 002's management reads, workshops public. */
export function useTargetLabels(api, targets) {
  return useLabels([targets], {
    getProduct: api.getCatalogueProduct,
    listPage: page => api.searchCatalogueProducts({ page }),
    productText: productLabel,
    sizeText: sizeLabel,
  }, api);
}

/**
 * Names for the targets of the offers on a public or Member page, read without sign-in. Only published products and
 * packages and active branches are found; anything else keeps showing its number.
 */
export function usePublicTargetLabels(api, promotions) {
  return useLabels((promotions || []).map(item => item?.targets), {
    getProduct: api.getPublicProduct,
    listPage: page => api.listPublicProducts({ page }),
    productText: publicProductLabel,
    sizeText: publicSizeLabel,
  }, api);
}
