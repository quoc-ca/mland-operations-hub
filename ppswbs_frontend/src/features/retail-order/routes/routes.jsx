import React from 'react';
import MemberCartPage from '../ui/cart/MemberCartPage';
import MemberCheckoutPage from '../ui/checkout/MemberCheckoutPage';
import MemberOrdersPage from '../ui/orders/MemberOrdersPage';
import ManagerPaymentReconciliationPage from '../ui/manager/ManagerPaymentReconciliationPage';
import StaffShipmentPage from '../ui/manager/StaffShipmentPage';
import '../ui/manager/payment-reconciliation.css';
import '../ui/readyringsales.css';
import '../ui/checkout-layout.css';
import '../ui/shopping.css';
import '../ui/checkout-bottom.css';

// FE-08 routes live inside AppLayout, like 002 and 006.
export const readyRingSalesRoutes = [
  { path: '/member/cart', element: <MemberCartPage /> },
  { path: '/member/checkout', element: <MemberCheckoutPage /> },
  { path: '/member/orders', element: <MemberOrdersPage /> },
  { path: '/manager/retail-payment-reconciliations', element: <ManagerPaymentReconciliationPage /> },
  { path: '/staff/retail-shipments', element: <StaffShipmentPage /> },
];
