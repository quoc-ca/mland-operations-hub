import React from 'react';
import { useParams } from 'react-router';
import { WorkshopAccessPage } from './WorkshopAccessPage';
import { WorkshopBookingPage } from './WorkshopBookingPage';
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

function WorkshopPaymentReturnRoute() {
  const { bookingCode } = useParams();
  return <WorkshopPaymentReturnPage bookingCode={bookingCode} />;
}

// Workshop route ownership stays local to the workshop flow. The app router
// only composes this public route declaration.
export const workshopRoutes = [
  { path: '/workshops', element: <WorkshopListPage /> },
  { path: '/workshops/:packageId', element: <WorkshopDetailRoute /> },
  { path: '/workshops/:packageId/book', element: <WorkshopBookingRoute /> },
  { path: '/workshop-booking/lookup', element: <WorkshopLookupPage /> },
  { path: '/workshop-booking/access', element: <WorkshopAccessPage /> },
  { path: '/workshop-booking/:bookingCode/payment-return', element: <WorkshopPaymentReturnRoute /> },
];
