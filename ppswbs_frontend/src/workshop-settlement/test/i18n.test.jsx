import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

useFreshRole();

const REFUNDS = { items: [{ refundId: 'R-1', ticketCode: 'TK-1', source: 'MLAND_CANCELLATION', amount: '200000', status: 'PENDING_APPROVAL' }] };

describe('Settlement screens follow the chosen language', () => {
  it('shows the refund queue in English with labels for the enum codes and grouped amounts', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-refund-requests': json(200, REFUNDS) });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    expect(await screen.findByRole('columnheader', { name: 'Reason' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeInTheDocument();
    expect(screen.getAllByText('Pending approval').length).toBeGreaterThan(0);
    expect(screen.getByText('Mland cancellation')).toBeInTheDocument();
    expect(screen.getByText('200,000 ₫')).toBeInTheDocument();
  });

  it('shows the same refund queue in Vietnamese, never the raw enum codes', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-refund-requests': json(200, REFUNDS) });
    renderRoutes(settlementRoutes, { language: 'vi', path: '/settlement/refunds' });
    expect(await screen.findByRole('columnheader', { name: 'Lý do' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Số tiền' })).toBeInTheDocument();
    expect(screen.getAllByText('Chờ duyệt').length).toBeGreaterThan(0);
    expect(screen.getByText('Mland hủy ca')).toBeInTheDocument();
    expect(screen.getByText('200.000 ₫')).toBeInTheDocument();
    expect(screen.queryByText('PENDING_APPROVAL')).not.toBeInTheDocument();
  });

  it('labels the cash-close form in Vietnamese', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER') });
    renderRoutes(settlementRoutes, { language: 'vi', path: '/settlement/cash-close' });
    expect(await screen.findByLabelText('Chi nhánh')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tải lại số kỳ vọng' })).toBeInTheDocument();
  });
});
