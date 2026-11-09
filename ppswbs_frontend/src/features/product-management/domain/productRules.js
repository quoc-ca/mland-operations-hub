// Client-side hints that mirror the backend rules (CatalogueValues); the backend stays the authority.
export const LIMITS = {
  minSize: 5,
  maxSize: 29,
  maxSizes: 25,
  maxImages: 10,
  nameMax: 200,
  descriptionMax: 5000,
  altTextMax: 200,
  reasonMax: 2000,
  searchMax: 200,
  mediaBytes: 5_000_000,
  importBytes: 5_000_000,
};

const AMOUNT = /^[1-9][0-9]{0,18}$/;
const QUANTITY = /^(0|[1-9][0-9]{0,18})$/;

const length = text => [...(text || '')].length;

export function isAmount(value) { return AMOUNT.test(String(value ?? '')); }
export function isQuantity(value) { return QUANTITY.test(String(value ?? '')); }

/** Returns i18n keys per field, e.g. { name: 'validation.required' }. Empty object means no hint. */
export function checkProductContent({ name, description, categoryId }) {
  const errors = {};
  if (!name?.trim()) errors.name = 'validation.required';
  else if (length(name.trim()) > LIMITS.nameMax) errors.name = 'validation.nameTooLong';
  if (!description?.trim()) errors.description = 'validation.required';
  else if (length(description.trim()) > LIMITS.descriptionMax) errors.description = 'validation.descriptionTooLong';
  if (!categoryId) errors.categoryId = 'validation.required';
  return errors;
}

export function checkVariants(variants) {
  const errors = {};
  if (variants.length === 0) errors.variants = 'validation.sizesRequired';
  if (variants.length > LIMITS.maxSizes) errors.variants = 'validation.tooManySizes';
  const seen = new Set();
  variants.forEach((variant, index) => {
    const size = Number(variant.size);
    if (!/^\d+$/.test(String(variant.size)) || size < LIMITS.minSize || size > LIMITS.maxSize) errors[`variants[${index}].size`] = 'validation.size';
    else if (seen.has(size)) errors[`variants[${index}].size`] = 'validation.duplicateSize';
    seen.add(size);
    if (!isAmount(variant.proposedPrice)) errors[`variants[${index}].proposedPrice`] = 'validation.amount';
    if (!isQuantity(variant.quantity)) errors[`variants[${index}].quantity`] = 'validation.quantity';
  });
  return errors;
}

/**
 * US10: quantity of every chosen size at every chosen branch (missing = 0). Keys are
 * `variants[i].quantities.<branchId>` so the size table can show the message under the right cell.
 */
export function checkBranchQuantities(variants, branchIds) {
  const errors = {};
  if (!branchIds.length) errors.branches = 'branch.required';
  variants.forEach((variant, index) => {
    branchIds.forEach(branchId => {
      const value = String(variant.quantities?.[branchId] ?? '0').trim();
      if (!isQuantity(value)) errors[`variants[${index}].quantities.${branchId}`] = 'validation.quantity';
    });
  });
  return errors;
}

/** US10: the `branches` field of a create command from the size table. */
export function branchesPayload(variants, branchIds) {
  return branchIds.map(branchId => ({
    locationId: String(branchId),
    quantities: variants.map(variant => ({ size: Number(variant.size), quantity: String(variant.quantities?.[branchId] ?? '0').trim() || '0' })),
  }));
}

export function checkImages(images) {
  const errors = {};
  if (images.length > LIMITS.maxImages) errors.images = 'validation.tooManyImages';
  images.forEach((image, index) => {
    if (!image.altText?.trim()) errors[`images[${index}].altText`] = 'validation.required';
    else if (length(image.altText.trim()) > LIMITS.altTextMax) errors[`images[${index}].altText`] = 'validation.altTooLong';
  });
  return errors;
}

export function checkReason(reason) {
  if (!reason?.trim()) return 'validation.reasonRequired';
  if (length(reason.trim()) > LIMITS.reasonMax) return 'validation.reasonTooLong';
  return null;
}

/** Product number as staff search it (FR-006): "SP" + at least 6 digits, e.g. 42 → "SP000042"; longer IDs stay whole. */
export function productCode(id) {
  return `SP${String(id ?? '').padStart(6, '0')}`;
}

/** The size whose SKU is exactly the keyword (trimmed, any case), so the list can point at it (FR-003); else null. */
export function skuMatch(keyword, variants) {
  const wanted = String(keyword || '').trim().toUpperCase();
  if (!wanted) return null;
  return (variants || []).find(variant => String(variant.sku || '').toUpperCase() === wanted) || null;
}

/** Field errors of an ApiError as { field: message }; backend messages are already safe text. */
/**
 * What a product still lacks before it can be submitted or published, by section: 'content' (active category),
 * 'images' (at least one image) and 'prices' (a proposal for every size). Each value is a message key. The backend
 * checks again (image READY state included); this only points the user at the section to fix.
 */
export function missingRequirements(product) {
  const missing = {};
  if (product.category && product.category.active === false) missing.content = 'require.category';
  if (!(product.images || []).length) missing.images = 'require.images';
  if ((product.variants || []).some(variant => !effectiveProposal(variant))) missing.prices = 'require.prices';
  return missing;
}

/** Section ids of missingRequirements, in page order, for scrolling to the first one. */
export const REQUIREMENT_SECTIONS = { content: 'content-title', images: 'images-title', prices: 'price-title' };

export function serverFieldErrors(error) {
  return Object.fromEntries((error?.fieldErrors || []).map(item => [item.field, item.message]));
}

/** The effective revision of each size's proposal history (the one the backend acts on). */
/**
 * The size's current proposal: the effective one with the highest revision. An applied proposal stays effective
 * (it is the record of the applied price), so after a new proposal is made there are two effective ones.
 */
export function effectiveProposal(variant) {
  return latestEffective(variant.proposals);
}

export function currentReview(product) {
  return latestEffective(product.reviews);
}

function latestEffective(items) {
  return (items || []).filter(item => item.effective).reduce((latest, item) => (!latest || Number(item.revision) > Number(latest.revision) ? item : latest), null);
}

/** Lower case, no Vietnamese marks, words joined by "-": "Nhẫn bạc Sóng nước" → "nhan-bac-song-nuoc". */
export function slugOf(text) {
  return String(text || '').normalize('NFD').replace(/\p{Mn}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/**
 * Product an image file is meant for, from its name: the file name (without extension) contains the product's group
 * key or name as whole words, e.g. "SONG-NUOC-2.jpg" or "nhan bac song nuoc.png". The longest match wins so that
 * "nhan-bac" does not take a file of "nhan-bac-song-nuoc"; no match gives null and the image stays unassigned.
 */
export function matchProductForFile(fileName, products) {
  const base = `-${slugOf(String(fileName).replace(/\.[^.]*$/, ''))}-`;
  let best = null;
  products.forEach(product => {
    [product.groupKey, product.name].map(slugOf).filter(Boolean).forEach(key => {
      if (base.includes(`-${key}-`) && (!best || key.length > best.length)) best = { id: product.id, length: key.length };
    });
  });
  return best ? best.id : null;
}

/**
 * Key two product names are compared by for the same-name warning: trimmed, inner spaces collapsed, one Unicode form
 * (a name typed with combined or separate Vietnamese marks is the same), any case. Marks still count: "Nhẫn" ≠ "Nhan".
 */
export function productNameKey(name) {
  return String(name ?? '').normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('vi');
}

/**
 * Products of a list that are not deleted and have the same name (productNameKey), except the one being edited.
 * Product names need not be unique (spec 002, A-003); this only feeds a warning.
 */
export function sameNameProducts(items, name, exceptId) {
  const key = productNameKey(name);
  if (!key) return [];
  return (items || []).filter(item => !item.deletedAt && String(item.id) !== String(exceptId ?? '') && productNameKey(item.name) === key);
}

/** Sizes in ascending order with consecutive runs folded, as an import file may write them: [12,5,6,7,14] → "5–7, 12, 14". */
export function compactSizes(sizes) {
  const sorted = [...new Set((sizes || []).map(Number))].sort((a, b) => a - b);
  const parts = [];
  for (let i = 0; i < sorted.length; i += 1) {
    let end = i;
    while (end + 1 < sorted.length && sorted[end + 1] === sorted[end] + 1) end += 1;
    parts.push(end - i >= 2 ? `${sorted[i]}–${sorted[end]}` : sorted.slice(i, end + 1).join(', '));
    i = end;
  }
  return parts.join(', ');
}
