import React from 'react';
import { RouterProvider } from 'react-router';
import { router } from './router';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import '../style.css';
import '../shared/ui/ui.css';

/** Application composition root: providers and routing only. */
export default function App() {
  return (
    <I18nProvider>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </I18nProvider>
  );
}
