import React from 'react';
import OtaChannelConfigurationPage from './pages/OtaChannelConfigurationPage';
import OtaReconciliationPage from './pages/OtaReconciliationPage';
import OtaFinanceReconciliationPage from './pages/OtaFinanceReconciliationPage';
import OtaAddonPage from './pages/OtaAddonPage';

export const otaConnectivityRoutes = [
  {
    path: 'ota-connectivity/channels',
    element: <OtaChannelConfigurationPage />,
  },
  {
    path: 'ota-connectivity/reconciliation',
    element: <OtaReconciliationPage />,
  },
  {
    path: 'ota-connectivity/finance',
    element: <OtaFinanceReconciliationPage />,
  },
  {
    path: 'ota-connectivity/add-ons',
    element: <OtaAddonPage />,
  },
];
