import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import { MemberConsultationPage } from './ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from './ui/StaffConsultationInboxPage';
import { MaskAdmin, RoleArea } from '../../shared/auth/RoleArea';

export const consultationRoutes = [
  {
    path: '/member/consultations',
    element: (
      <RequireSession requiredText="Vui lòng đăng nhập để chat tư vấn với nhân viên.">
        <MaskAdmin><MemberConsultationPage /></MaskAdmin>
      </RequireSession>
    ),
  },
  {
    path: '/operations/consultations',
    element: (
      <RoleArea roles={['STAFF', 'MANAGER']} requiredText="Vui lòng đăng nhập tài khoản nhân viên.">
        <StaffConsultationInboxPage />
      </RoleArea>
    ),
  },
];
