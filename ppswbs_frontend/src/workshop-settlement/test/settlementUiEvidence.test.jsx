import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';

const emptyPolicies = json(200, { items: [] });

describe('Settlement rendered UI evidence', () => {
  it('renders an empty settlement case list with supported filters', async () => {
    mockFetch({
      'GET /api/v1/workshop-settlement-cases': json(200, { items: [] }),
      'GET /api/v1/workshop-refund-policies': emptyPolicies,
    });
    renderRoutes(settlementRoutes, { path: '/settlement/cases?tab=cases' });
    expect(await screen.findByText('All settlement cases')).toBeInTheDocument();
    expect(screen.getByText('No settlement cases found.')).toBeInTheDocument();
    const statusFilter = screen.getByRole('combobox', { name: 'Status' });
    const statusValues = Array.from(statusFilter.querySelectorAll('option')).map(option => option.value);
    // FR-022b1 vocabulary only: no CANCELED, EXPIRED or deferred-payment state.
    expect(statusValues).toEqual(['', 'OPEN', 'PENDING_PROVIDER', 'RESOLVED', 'REJECTED', 'FAILED']);
  });

  it('renders a pending-provider case without treating it as resolved', async () => {
    mockFetch({
      'GET /api/v1/workshop-settlement-cases': json(200, { items: [{ caseReference: 'CASE-001', caseType: 'MLAND_CANCELLATION_REMEDY', status: 'PENDING_PROVIDER', bookingCode: 'BK-001' }] }),
      'GET /api/v1/workshop-refund-policies': emptyPolicies,
    });
    renderRoutes(settlementRoutes, { path: '/settlement/cases?tab=cases' });
    expect(await screen.findByText('CASE-001')).toBeInTheDocument();
    expect(screen.getByText('PENDING_PROVIDER')).toBeInTheDocument();
    expect(screen.getByText('BK-001')).toBeInTheDocument();
  });

  it('renders refund policy history and an empty-state when no revisions exist', async () => {
    mockFetch({
      'GET /api/v1/workshop-settlement-cases': json(200, { items: [] }),
      'GET /api/v1/workshop-refund-policies': json(200, { items: [] }),
    });
    renderRoutes(settlementRoutes, { path: '/settlement/cases?tab=policies' });
    expect(await screen.findByText('Policy history')).toBeInTheDocument();
    expect(screen.getByText('No policy revisions found.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create policy revision' })).toBeInTheDocument();
  });

  it('renders a backend error with retry affordance instead of a fabricated case list', async () => {
    mockFetch({
      'GET /api/v1/workshop-settlement-cases': json(503, { code: 'SETTLEMENT_UNAVAILABLE', message: 'Settlement service unavailable', correlationId: 'corr-settlement-1' }),
      'GET /api/v1/workshop-refund-policies': emptyPolicies,
    });
    renderRoutes(settlementRoutes, { path: '/settlement/cases?tab=cases' });
    expect(await screen.findByText('Hệ thống tạm thời chưa xử lý được. Bấm Thử lại để gửi lại đúng thao tác này.')).toBeInTheDocument();
    expect(screen.getByText(/corr-settlement-1/)).toBeInTheDocument();
    expect(screen.queryByText('No settlement cases found.')).not.toBeInTheDocument();
  });
});
