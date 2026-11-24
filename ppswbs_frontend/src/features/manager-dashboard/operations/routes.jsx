import React from 'react';
import { Navigate } from 'react-router';

// Manager Home was merged into the Manager dashboard (spec 014, navigation decision of 2026-11-22).
export const managerHomeRoutes = [
  { path: '/manager/home', element: <Navigate to="/manager/dashboard" replace /> },
];
