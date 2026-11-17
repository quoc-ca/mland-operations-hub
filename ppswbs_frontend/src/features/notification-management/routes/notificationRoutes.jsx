import React from 'react';
import TemplateListPage from '../ui/TemplateListPage';
import TemplateDetailView from '../ui/TemplateDetailView';
import NotificationRequestListPage from '../ui/NotificationRequestListPage';
import NotificationManagementGuard from './NotificationManagementGuard';

export const notificationRoutes = [
  {
    path: '/admin/notifications/templates',
    element: (
      <NotificationManagementGuard>
        <TemplateListPage />
      </NotificationManagementGuard>
    ),
  },
  {
    path: '/admin/notifications/templates/:code',
    element: (
      <NotificationManagementGuard>
        <TemplateDetailView />
      </NotificationManagementGuard>
    ),
  },
  {
    path: '/admin/notifications/requests',
    element: (
      <NotificationManagementGuard>
        <NotificationRequestListPage />
      </NotificationManagementGuard>
    ),
  },
];

export default notificationRoutes;

