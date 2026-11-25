import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import { MemberConsultationPage } from './ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from './ui/StaffConsultationInboxPage';

export const consultationRoutes = [
  {
    path: '/member/consultations',
    element: (
      <RequireSession requiredText="Vui lòng đăng nhập để chat tư vấn với nhân viên.">
        <MemberConsultationPage />
      </RequireSession>
    ),
  },
  {
    path: '/operations/consultations',
    element: (
      <RequireSession requiredText="Vui lòng đăng nhập tài khoản nhân viên.">
        <StaffConsultationInboxPage />
      </RequireSession>
    ),
  },
];
