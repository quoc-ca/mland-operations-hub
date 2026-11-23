import React from 'react';
import { Link } from 'react-router';
import { useT } from '../i18n/I18nProvider';
import { sharedMessages } from './messages';
import { useCartCounts } from './useCartCounts';
import './cart-tabs.css';

/** One cart entry, two carts: products (shipping/pickup) and workshops (seat hold, one payment). */
export default function CartTabs({ active }) {
  const t = useT(sharedMessages);
  const { product, workshop } = useCartCounts();
  const tabs = [
    { key: 'products', to: '/member/cart', label: t('cart.products'), count: product, hint: t('cart.productsHint') },
    { key: 'workshop', to: '/workshop-booking/cart', label: t('cart.workshop'), count: workshop, hint: t('cart.workshopHint') },
  ];
  return (
    <nav className="cart-tabs" aria-label={t('cart.aria')}>
      {tabs.map(tab => (
        <Link
          key={tab.key}
          to={tab.to}
          className={`cart-tab${active === tab.key ? ' active' : ''}`}
          aria-current={active === tab.key ? 'page' : undefined}
        >
          <span className="cart-tab-label">{tab.label} ({tab.count})</span>
          <span className="cart-tab-hint">{tab.hint}</span>
        </Link>
      ))}
    </nav>
  );
}
