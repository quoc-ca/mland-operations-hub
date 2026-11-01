import React from 'react';
import { WorkshopAccessPage } from './WorkshopAccessPage';
import { WorkshopBookingPage } from './WorkshopBookingPage';
import { WorkshopDetailPage } from './WorkshopDetailPage';
import { WorkshopListPage } from './WorkshopListPage';
import { WorkshopLookupPage } from './WorkshopLookupPage';
import { WorkshopPaymentReturnPage } from './WorkshopPaymentReturnPage';

// Workshop route ownership stays local to the workshop flow. The app router
// only composes this public route declaration.
export const workshopRoutes = [
  { path: '/workshops', element: <WorkshopListPage /> },
  { path: '/workshops/:packageId', element: <WorkshopDetailPage /> },
  { path: '/workshops/:packageId/book', element: <WorkshopBookingPage /> },
  { path: '/workshop-booking/lookup', element: <WorkshopLookupPage /> },
  { path: '/workshop-booking/access', element: <WorkshopAccessPage /> },
  { path: '/workshop-booking/:bookingCode/payment-return', element: <WorkshopPaymentReturnPage /> },
];
