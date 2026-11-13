import React from 'react';
import StaffRetailOrdersPage from './StaffRetailOrdersPage';

/** Former pickup-desk address: opens the counter-cash tab of the Staff retail order page (FR-08-051). */
export default function StaffPickupPage() {
  return <StaffRetailOrdersPage initialTab="AWAITING_CASH" />;
}
