import React from 'react';
import { Navigate } from 'react-router';
import { RequireSession } from '../../shared/auth/RequireSession';
import RingDesignEntryPage from './ui/RingDesignEntryPage';
import RingModelSelectionPage from './ui/RingModelSelectionPage';
import RingConfigurationPage from './ui/RingConfigurationPage';
import DesignEvaluationPage from './ui/DesignEvaluationPage';
import BranchCompatibilityPage from './ui/BranchCompatibilityPage';
import MemberDesignHistoryPage from './ui/MemberDesignHistoryPage';
import './ring-design.css';

export const ringDesignConfigurationRoutes = [
  {
    path: '/ring-design',
    element: <Navigate to="/workshops/PKG-COUPLE-SILVER/design" replace />,
  },
  {
    path: '/ring-designs',
    element: <Navigate to="/workshops/PKG-COUPLE-SILVER/design" replace />,
  },
  {
    path: '/workshops/:packageReference/design',
    element: <RingDesignEntryPage />,
  },
  {
    path: '/workshops/:packageReference/design/models',
    element: <RingModelSelectionPage />,
  },
  {
    path: '/workshops/:packageReference/design/configure',
    element: <RingConfigurationPage />,
  },
  {
    path: '/workshops/:packageReference/design/recovery',
    element: <BranchCompatibilityPage />,
  },
  {
    path: '/workshops/:packageReference/design/:designId',
    element: <DesignEvaluationPage />,
  },
  {
    path: '/my/designs',
    element: (
      <RequireSession>
        <MemberDesignHistoryPage />
      </RequireSession>
    ),
  },
];

