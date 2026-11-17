import React from 'react';
import StaffRetailOrdersPage from './StaffRetailOrdersPage';

export { shipmentLabels, nextShipmentStatus } from './StaffRetailOrdersPage';

/** Former shipment address: opens the "to hand to GHTK" tab of the Staff retail order page (FR-08-051). */
export default function StaffShipmentPage() {
  return <StaffRetailOrdersPage initialTab="TO_SHIP" />;
}
