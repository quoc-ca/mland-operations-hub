import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../../../shared/auth/AuthProvider';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import MainTopbar from '../../../shared/ui/MainTopbar';
import { fakeSession, json, mockFetch } from '../../../test/render';
import { signInAs } from './fixtures';

// T119: the "Quản lý Ưu đãi" entry of the account menu is for the Manager only (FR-001; /promotion-management/* is a
// MANAGER route family, so an Admin would only reach /404).
async function openMenuAs(role) {
  signInAs(role);
  mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role, fullName: 'Người dùng' }) });
  const router = createMemoryRouter([{ path: '/', element: <MainTopbar /> }], { initialEntries: ['/'] });
  render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={fakeSession()}><RouterProvider router={router} /></AuthProvider>
    </I18nProvider>,
  );
  await userEvent.setup().click(await screen.findByRole('button', { name: /Tùy chọn tài khoản/ }));
}

describe('T119 promotion management link in the top bar', () => {
  it('is shown to a Manager', async () => {
    await openMenuAs('MANAGER');
    expect(await screen.findByRole('menuitem', { name: /Quản lý Ưu đãi/ })).toHaveAttribute('href', '/promotion-management/programs');
  });

  it('is not shown to an Admin, who still sees product management', async () => {
    await openMenuAs('ADMIN');
    expect(await screen.findByRole('menuitem', { name: /Quản trị Sản phẩm/ })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Quản lý Ưu đãi/ })).toBeNull();
  });
});
