import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { createOtaConnectivityClient } from '../api/otaConnectivityApi';
import { otaConnectivityRoutes } from '../routes';

/**
 * 003.4 T119 / C2: the reconciliation queue filters by `category` through listReconciliationWorkItems. An
 * INBOUND_EVENT work item has no mapping (mappingId null), and the page must not assume one.
 */
const INBOUND = { workItemId: 'a0000000-0000-0000-0000-000000000001', mappingId: null, status: 'OPEN', correlationId: 'c0000000-0000-0000-0000-000000000001', safeSummary: 'Same reference, different payload', category: 'INBOUND_EVENT' };
const PAYOUT = { workItemId: 'a0000000-0000-0000-0000-000000000002', mappingId: 'f0000000-0000-0000-0000-000000000002', status: 'OPEN', correlationId: 'c0000000-0000-0000-0000-000000000002', safeSummary: 'Payout dispute', category: 'PAYOUT_DISPUTE' };

const open = () => renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/reconciliation', language: 'en' });

describe('003.4 T119 category filter on the reconciliation queue', () => {
  it('passes category through listReconciliationWorkItems only when one is given, trimmed', async () => {
    const http = vi.fn().mockResolvedValue({ items: [] });
    const api = createOtaConnectivityClient(http);
    await api.listReconciliationWorkItems('OPEN', ' INBOUND_EVENT ');
    expect(http).toHaveBeenLastCalledWith('/api/v1/ota-reconciliation-work-items', { query: { status: 'OPEN', category: 'INBOUND_EVENT' } });
    await api.listReconciliationWorkItems('ALL', '');
    expect(http).toHaveBeenLastCalledWith('/api/v1/ota-reconciliation-work-items', { query: {} });
  });

  it('filters by INBOUND_EVENT, shows an item without a mapping, and returns to all categories', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/ota-reconciliation-work-items': call => json(200, { items: call.query.category === 'INBOUND_EVENT' ? [INBOUND] : [INBOUND, PAYOUT] }),
    });
    open();
    expect(await screen.findByText(`Item: ${PAYOUT.workItemId}`)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'INBOUND_EVENT' }));
    await waitFor(() => expect(screen.queryByText(`Item: ${PAYOUT.workItemId}`)).not.toBeInTheDocument());
    expect(screen.getByText(`Item: ${INBOUND.workItemId}`)).toBeInTheDocument();
    expect(screen.getByText('None (Global)')).toBeInTheDocument();
    expect(calls.at(-1).query).toEqual({ category: 'INBOUND_EVENT' });

    await user.click(screen.getByRole('button', { name: 'All categories' }));
    expect(await screen.findByText(`Item: ${PAYOUT.workItemId}`)).toBeInTheDocument();
    expect(calls.at(-1).query).toEqual({});
  });

  it('applies a typed category together with the status filter and says so when nothing matches', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/ota-reconciliation-work-items': call => json(200, { items: call.query.category ? [] : [PAYOUT] }),
    });
    open();
    await screen.findByText(`Item: ${PAYOUT.workItemId}`);
    await user.click(screen.getByRole('button', { name: 'OPEN' }));
    await user.type(screen.getByLabelText('Category:'), 'payout_dispute');
    await user.click(screen.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText(/matching status "OPEN" and category "PAYOUT_DISPUTE"/)).toBeInTheDocument();
    expect(calls.at(-1).query).toEqual({ status: 'OPEN', category: 'PAYOUT_DISPUTE' });
  });
  it('shows the type of every work item next to its mapping, including an item with no mapping', async () => {
    mockFetch({
      'GET /api/v1/ota-reconciliation-work-items': () => json(200, { items: [INBOUND, PAYOUT] }),
    });
    open();
    const inboundCard = (await screen.findByText(`Item: ${INBOUND.workItemId}`)).closest('.mapping-card');
    const payoutCard = (await screen.findByText(`Item: ${PAYOUT.workItemId}`)).closest('.mapping-card');
    expect(within(inboundCard).getByText('Type').nextElementSibling).toHaveTextContent('INBOUND_EVENT');
    expect(within(inboundCard).getByText('None (Global)')).toBeInTheDocument();
    expect(within(payoutCard).getByText('Type').nextElementSibling).toHaveTextContent('PAYOUT_DISPUTE');
  });
});
