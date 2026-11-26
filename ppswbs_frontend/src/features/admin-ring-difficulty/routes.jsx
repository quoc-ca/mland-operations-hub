import React from 'react';
import { RoleArea } from '../../shared/auth/RoleArea';
import RingDifficultyPolicyPage from './ui/RingDifficultyPolicyPage';

export const adminRingDifficultyRoutes = [
  {
    path: '/admin/ring-difficulty',
    element: (
      <RoleArea roles={['ADMIN']} requiredText="Vui lòng đăng nhập với tài khoản Admin.">
        <RingDifficultyPolicyPage />
      </RoleArea>
    ),
  },
];
