import React from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { router } from './router';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import '../style.css';
import '../shared/ui/ui.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <I18nProvider>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </I18nProvider>
  </React.StrictMode>,
);
