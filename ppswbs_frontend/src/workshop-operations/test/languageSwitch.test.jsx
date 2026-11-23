import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';
import { clearRoleCache } from '../../shared/auth/useRole';
import { enumLabel } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';

// The Staff/Manager workshop screens follow the language of the shared I18nProvider, and a backend enum code shows
// its label instead of the raw code (a code without a label falls back to the code).
const SESSION = { id: 'session-1', workshopDate: '2026-12-05', locationId: 4, capacity: 10, state: 'SALES_PAUSED' };

function openBoard(language) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'M' }),
    'GET /api/v1/workshop-locations': json(200, [{ id: 4, name: 'MLAND Hoàn Kiếm' }]),
    'GET /api/v1/workshop-sessions': json(200, [SESSION]),
  });
  return renderRoutes(operationsRoutes, { language, path: '/operations/sessions?from=' });
}

describe('Session board language', () => {
  beforeEach(() => clearRoleCache());

  it('shows English labels and the English state label in en', async () => {
    openBoard('en');
    const table = await screen.findByRole('table');
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument();
    expect(within(table).getByText('Sales paused')).toBeInTheDocument();
    expect(within(table).queryByText('SALES_PAUSED')).not.toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: 'Capacity' })).toBeInTheDocument();
  });

  it('shows Vietnamese labels and the Vietnamese state label in vi', async () => {
    openBoard('vi');
    const table = await screen.findByRole('table');
    expect(screen.getByRole('button', { name: 'Xóa lọc' })).toBeInTheDocument();
    expect(within(table).getByText('Tạm dừng bán')).toBeInTheDocument();
    expect(within(table).queryByText('SALES_PAUSED')).not.toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: 'Sức chứa' })).toBeInTheDocument();
  });
});

describe('enum labels', () => {
  it('maps known codes per language and falls back to the raw code', () => {
    expect(enumLabel(messages, 'en', 'sessionState', 'CANCELLED')).toBe('Cancelled');
    expect(enumLabel(messages, 'vi', 'sessionState', 'CANCELLED')).toBe('Đã hủy');
    expect(enumLabel(messages, 'vi', 'sessionState', 'SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });

  it('keeps the vi and en dictionaries in step', () => {
    const missingInEn = Object.keys(messages.vi).filter(key => !(key in messages.en));
    const missingInVi = Object.keys(messages.en).filter(key => !(key in messages.vi));
    expect(missingInEn).toEqual([]);
    expect(missingInVi).toEqual([]);
  });
});
