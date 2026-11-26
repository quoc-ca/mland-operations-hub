import React from 'react';
import { Navigate, useSearchParams } from 'react-router';
import MemberCartPage from '../ui/cart/MemberCartPage';
import MemberCheckoutPage from '../ui/checkout/MemberCheckoutPage';
import MemberOrdersPage from '../ui/orders/MemberOrdersPage';
import ManagerPaymentReconciliationPage from '../ui/manager/ManagerPaymentReconciliationPage';
import StaffShipmentPage from '../ui/manager/StaffShipmentPage';
import StaffPickupPage from '../ui/manager/StaffPickupPage';
import StaffRetailOrdersPage from '../ui/manager/StaffRetailOrdersPage';
import { CarrierOrdersPage, CarrierStatsPage } from '../ui/carrier/CarrierPages';
import '../ui/manager/payment-reconciliation.css';
import '../ui/readyringsales.css';
import '../ui/checkout-layout.css';
import '../ui/shopping.css';
import '../ui/checkout-bottom.css';
import '../ui/cart-shopee.css';
import '../ui/retail-cash.css';
import '../ui/retail-orders.css';
import { MaskAdmin, RoleArea } from '../../../shared/auth/RoleArea';

/** The topbar cart icon: one entry, two carts. `?tab=workshop` opens the workshop cart, anything else the product cart. */
function CartEntry() {
  const [params] = useSearchParams();
  return <Navigate replace to={params.get('tab') === 'workshop' ? '/workshop-booking/cart' : '/member/cart'} />;
}

// FE-08 routes live inside AppLayout, like 002 and 006.
export const readyRingSalesRoutes = [
  { path: '/cart', element: <CartEntry /> },
  { path: '/member/cart', element: <MaskAdmin><MemberCartPage /></MaskAdmin> },
  { path: '/member/checkout', element: <MaskAdmin><MemberCheckoutPage /></MaskAdmin> },
  { path: '/member/orders', element: <MaskAdmin><MemberOrdersPage /></MaskAdmin> },
  { path: '/manager/retail-payment-reconciliations', element: <RoleArea roles={['MANAGER']}><ManagerPaymentReconciliationPage /></RoleArea> },
  { path: '/staff/retail-orders', element: <RoleArea roles={['STAFF', 'MANAGER']}><StaffRetailOrdersPage /></RoleArea> },
  // Former addresses open the matching tab of the Staff retail order page (FR-08-051).
  { path: '/staff/retail-shipments', element: <RoleArea roles={['STAFF', 'MANAGER']}><StaffShipmentPage /></RoleArea> },
  { path: '/staff/retail-pickups', element: <RoleArea roles={['STAFF', 'MANAGER']}><StaffPickupPage /></RoleArea> },
  // Simulated GHTK partner portal, role CARRIER (Session 2026-11-20).
  // Only CARRIER (spec 008 route allowlist): another role or a suspended account is replaced to /404 before any API call.
  { path: '/carrier/orders', element: <RoleArea roles={['CARRIER']}><CarrierOrdersPage /></RoleArea> },
  { path: '/carrier/stats', element: <RoleArea roles={['CARRIER']}><CarrierStatsPage /></RoleArea> },
];
