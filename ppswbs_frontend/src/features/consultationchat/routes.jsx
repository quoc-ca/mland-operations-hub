import React from 'react';
import { MemberConsultationPage } from './ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from './ui/StaffConsultationInboxPage';

export const consultationRoutes = [
  {
    path: '/member/consultations',
    element: <MemberConsultationPage />,
  },
  {
    path: '/operations/consultations',
    element: <StaffConsultationInboxPage />,
  },
];
