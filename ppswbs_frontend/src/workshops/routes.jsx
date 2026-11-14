import React from 'react';
import { useParams, useSearchParams } from 'react-router';
import { WorkshopAccessPage } from './WorkshopAccessPage';
import { WorkshopBookingPage } from './WorkshopBookingPage';
import { WorkshopCartPage } from './WorkshopCartPage';
import { WorkshopDetailPage } from './WorkshopDetailPage';
import { WorkshopListPage } from './WorkshopListPage';
import { WorkshopLookupPage } from './WorkshopLookupPage';
import { WorkshopPaymentReturnPage } from './WorkshopPaymentReturnPage';

function WorkshopDetailRoute() {
  const { packageId } = useParams();
  return <WorkshopDetailPage packageId={packageId} />;
}

function WorkshopBookingRoute() {
  const { packageId } = useParams();
  return <WorkshopBookingPage packageId={packageId} />;
}

function WorkshopBookingQueryRoute() {
  const [params] = useSearchParams();
  const packageRef = params.get('packageReference') || params.get('packageId') || '1';
  return <WorkshopBookingPage packageId={packageRef} />;
}

function WorkshopPaymentReturnRoute() {
  const { groupCode } = useParams();
  return <WorkshopPaymentReturnPage groupCode={groupCode} />;
}

// Workshop route ownership stays local to the workshop flow. The app router
// only composes this public route declaration.
export const workshopRoutes = [
  { path: '/workshops', element: <WorkshopListPage /> },
  { path: '/workshops/:packageId', element: <WorkshopDetailRoute /> },
  { path: '/workshops/:packageId/book', element: <WorkshopBookingRoute /> },
  { path: '/workshops/booking', element: <WorkshopBookingQueryRoute /> },
  { path: '/workshop-booking/cart', element: <WorkshopCartPage /> },
  { path: '/workshop-booking/lookup', element: <WorkshopLookupPage /> },
  { path: '/workshop-booking/access', element: <WorkshopAccessPage /> },
  { path: '/workshop-booking/:groupCode/payment-return', element: <WorkshopPaymentReturnRoute /> },
];
