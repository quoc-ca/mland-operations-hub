import React from 'react';
import { Link } from 'react-router';
import { useCartCounts } from './useCartCounts';
import './cart-tabs.css';

/** One cart entry, two carts: products (shipping/pickup) and workshops (seat hold, one payment). */
export default function CartTabs({ active }) {
  const { product, workshop } = useCartCounts();
  const tabs = [
    { key: 'products', to: '/member/cart', label: 'Sản phẩm', count: product, hint: 'Nhận tại cửa hàng hoặc giao hàng' },
    { key: 'workshop', to: '/workshop-booking/cart', label: 'Workshop', count: workshop, hint: 'Giữ chỗ và thanh toán một lần khi đặt' },
  ];
  return (
    <nav className="cart-tabs" aria-label="Giỏ hàng">
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
