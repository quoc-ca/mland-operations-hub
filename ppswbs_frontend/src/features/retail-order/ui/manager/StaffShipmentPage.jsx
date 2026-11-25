import React from 'react';
import StaffRetailOrdersPage from './StaffRetailOrdersPage';

export { nextShipmentStatus } from './StaffRetailOrdersPage';

/** Former shipment address: opens the "Đang chuẩn bị hàng" tab of the Staff retail order page (FR-08-051). */
export default function StaffShipmentPage() {
  return <StaffRetailOrdersPage initialTab="PREPARING" />;
}
