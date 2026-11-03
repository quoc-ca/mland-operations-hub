// Response shapes copied from the backend DTOs (ManagementProductDtos, PublicCatalogueDtos, CategoryDtos).
export const category = { id: '7', name: 'Nhẫn cưới', description: null, active: true, version: 0 };

export function managementProduct(overrides = {}) {
  return {
    id: '42', name: 'Nhẫn bạc', description: 'Bạc 925', category, published: false, firstPublishedAt: null, deletedAt: null,
    contentVersion: 1, version: 3,
    images: [{ mediaId: '11111111-1111-1111-1111-111111111111', altText: 'Mặt trước', position: 0 }],
    variants: [{
      id: '101', size: 12, sku: 'P42-S12', remainingUnsold: '5', validHeldQuantity: '2', availableQuantity: '3', saleEnabled: true,
      appliedSequence: 0, version: 4,
      proposals: [{ id: '501', variantId: '101', revision: 1, version: 0, state: 'PENDING', effective: true, proposedPrice: '1250000', baseAppliedSequence: 0, applied: false }],
    }],
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'PENDING', effective: true }],
    ...overrides,
  };
}

export const categoryPage = { items: [category], page: 0, pageSize: 20, totalElements: '1' };

export function publicProduct(overrides = {}) {
  return {
    id: '42', name: 'Nhẫn bạc', description: 'Bạc 925', category: { id: '7', name: 'Nhẫn cưới' },
    images: [{ id: '9', url: '/api/v1/product-media/9', altText: 'Mặt trước', position: 0 }],
    variants: [
      { id: '101', size: 12, sku: 'P42-S12', appliedPrice: '1250000', currency: 'VND', saleEnabled: true, canBuy: true },
      { id: '102', size: 14, sku: 'P42-S14', appliedPrice: '1300000', currency: 'VND', saleEnabled: false, canBuy: false },
    ],
    priceRange: { minimum: '1250000', maximum: '1300000', currency: 'VND' }, canBuy: true, createdAt: '2026-10-30T00:00:00Z',
    ...overrides,
  };
}

export const actionResult = (resourceId, action) => ({ commandId: 'c', resourceId, resourceType: 'PRODUCT', action, status: 'COMMITTED', version: 4, changed: true });
