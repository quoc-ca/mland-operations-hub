import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../../../shared/auth/AuthProvider';
import { I18nProvider, useLanguage } from '../../../shared/i18n/I18nProvider';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { designReviewMessages } from '../messages';
import { designReviewRoutes } from '../routes';

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <>
    <button type="button" onClick={() => setLanguage('en')}>to-en</button>
    <button type="button" onClick={() => setLanguage('vi')}>to-vi</button>
  </>;
}

const orderList = {
  items: [{
    customOrderReference: 'CO-I18N01',
    status: 'FULL_PAYMENT_PENDING',
    createdAt: '2026-11-20T03:00:00Z',
    quote: { quoteReference: 'QUO-I18N', quoteGeneration: 1, totalAmount: '15000000', status: 'QUOTE_READY', expiresAt: new Date(Date.now() + 86400000).toISOString() },
  }],
};

describe('Design review i18n', () => {
  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    expect(Object.keys(designReviewMessages.en).sort()).toEqual(Object.keys(designReviewMessages.vi).sort());
    const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
    Object.keys(designReviewMessages.vi).forEach(key =>
      expect(placeholders(designReviewMessages.en[key]), key).toEqual(placeholders(designReviewMessages.vi[key])));
  });

  it('shows the custom orders page in English with en money format', async () => {
    mockFetch({ 'GET /api/v1/members/me/custom-orders': json(200, orderList) });
    renderRoutes(designReviewRoutes, { path: '/account/my-custom-orders', session: fakeSession('signed-in'), language: 'en' });

    expect(await screen.findByText('CO-I18N01')).toBeInTheDocument();
    expect(screen.getByText('My custom orders')).toBeInTheDocument();
    expect(screen.getAllByText('Awaiting payment').length).toBeGreaterThan(0);
    expect(screen.getByText('Quote version: #1')).toBeInTheDocument();
    expect(screen.getByText(/^15,000,000\s₫$/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Pay in full/ })).toBeInTheDocument();
    expect(screen.queryAllByText('Chờ thanh toán')).toHaveLength(0);
  });

  it('switches the custom orders page between vi and en without reloading data', async () => {
    window.localStorage.clear();
    mockFetch({ 'GET /api/v1/members/me/custom-orders': json(200, orderList) });
    const router = createMemoryRouter(designReviewRoutes, { initialEntries: ['/account/my-custom-orders'] });
    render(
      <I18nProvider initialLanguage="vi">
        <AuthProvider source={fakeSession('signed-in')}>
          <LanguageToggle />
          <RouterProvider router={router} />
        </AuthProvider>
      </I18nProvider>,
    );

    expect(await screen.findByText('CO-I18N01')).toBeInTheDocument();
    expect(screen.getAllByText('Chờ thanh toán').length).toBeGreaterThan(0);
    expect(screen.getByText(/^15\.000\.000\s₫$/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getAllByText('Awaiting payment').length).toBeGreaterThan(0);
    expect(screen.getByText(/^15,000,000\s₫$/)).toBeInTheDocument();
    expect(screen.queryAllByText('Chờ thanh toán')).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'to-vi' }));
    expect(screen.getAllByText('Chờ thanh toán').length).toBeGreaterThan(0);
    window.localStorage.clear();
  });

  it('shows the review queue and workspace in English', async () => {
    mockFetch({
      'GET /api/v1/design-review-requests': json(200, [{ id: 'rev-9', requestReference: 'DR-9', description: 'Ring', ownershipType: 'GUEST', createdAt: '2026-11-20T03:00:00Z', images: [{}, {}] }]),
    });
    renderRoutes(designReviewRoutes, { path: '/design-review-requests', session: fakeSession('signed-in'), language: 'en' });
    expect(await screen.findByText('DR-9')).toBeInTheDocument();
    expect(screen.getByText('Submitted at')).toBeInTheDocument();
    expect(screen.getByText('Guest')).toBeInTheDocument();
    expect(screen.getByText('📷 2 images')).toBeInTheDocument();
  });
});
