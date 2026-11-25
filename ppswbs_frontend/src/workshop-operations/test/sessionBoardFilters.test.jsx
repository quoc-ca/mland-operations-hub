import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';
import { clearRoleCache } from '../../shared/auth/useRole';

const todayLocal = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

describe('Session board date filter', () => {
  beforeEach(() => clearRoleCache());

  it('opens with From set to today and clears every filter on demand', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'M' }),
      'GET /api/v1/workshop-sessions': json(200, []),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions' });

    const from = await screen.findByLabelText('From');
    expect(from).toHaveValue(todayLocal());
    expect(screen.getByLabelText('To')).toHaveValue('');

    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByLabelText('From')).toHaveValue('');
  });
});
