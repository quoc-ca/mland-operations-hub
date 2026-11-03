import React from 'react';
import MemberCartPage from './cart/MemberCartPage';
import MemberCheckoutPage from './checkout/MemberCheckoutPage';
import MemberOrdersPage from './orders/MemberOrdersPage';
import './readyringsales.css';

// FE-08 routes live inside AppLayout, like 002 and 006.
export const readyRingSalesRoutes = [
  { path: '/member/cart', element: <MemberCartPage /> },
  { path: '/member/checkout', element: <MemberCheckoutPage /> },
  { path: '/member/orders', element: <MemberOrdersPage /> },
];
