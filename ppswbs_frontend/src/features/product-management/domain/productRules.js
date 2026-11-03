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

/** Field errors of an ApiError as { field: message }; backend messages are already safe text. */
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
