/** Which Staff tab a branch order belongs to. Pure functions, shared by the Staff order page, the Staff home and the sidebar badge. */
export const OPEN_TASK_TABS = ['AWAITING_CASH', 'PREPARING', 'TO_HAND_OVER'];

export function pickupTab(row) {
  if (row.order.paymentState === 'ACCEPTED_UNPAID') return 'AWAITING_CASH';
  if (row.fulfilmentState === 'PICKED_UP') return 'HISTORY';
  return row.pickupReadyConfirmed ? 'TO_HAND_OVER' : 'PREPARING';
}

export function shipmentTab(row) {
  if (row.status === 'PREPARING') return 'PREPARING';
  if (row.status === 'READY_FOR_CARRIER') return 'AWAITING_CARRIER';
  return row.status === 'DELIVERED' ? 'HISTORY' : 'HANDED_OVER';
}

/** Orders that wait for Staff: prepare, collect cash, hand over (Session 2026-11-22). */
export function staffRetailOpenCount(pickups, shipments) {
  const tabs = [...(Array.isArray(pickups) ? pickups : []).map(pickupTab), ...(Array.isArray(shipments) ? shipments : []).map(shipmentTab)];
  return tabs.filter(tab => OPEN_TASK_TABS.includes(tab)).length;
}
