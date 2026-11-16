import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

vi.mock('./useCartCounts', () => ({ useCartCounts: () => ({ product: 2, workshop: 3, total: 5 }) }));

import CartTabs from './CartTabs';

function renderTabs(active) {
  return render(<MemoryRouter><CartTabs active={active} /></MemoryRouter>);
}

describe('CartTabs', () => {
  it('shows both carts with their line counts and links', () => {
    renderTabs('products');
    expect(screen.getByRole('link', { name: /Sản phẩm \(2\)/ })).toHaveAttribute('href', '/member/cart');
    expect(screen.getByRole('link', { name: /Workshop \(3\)/ })).toHaveAttribute('href', '/workshop-booking/cart');
  });

  it('marks only the active tab as the current page', () => {
    renderTabs('workshop');
    expect(screen.getByRole('link', { name: /Workshop/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /Sản phẩm/ })).not.toHaveAttribute('aria-current');
  });
});
