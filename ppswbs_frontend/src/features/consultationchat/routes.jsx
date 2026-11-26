import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import { MemberConsultationPage } from './ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from './ui/StaffConsultationInboxPage';
import { RoleArea } from '../../shared/auth/RoleArea';

export const consultationRoutes = [
  {
    path: '/member/consultations',
    element: (
      <RoleArea roles={['MEMBER']} requiredText="Vui lòng đăng nhập để chat tư vấn với nhân viên.">
        <MemberConsultationPage />
      </RoleArea>
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
