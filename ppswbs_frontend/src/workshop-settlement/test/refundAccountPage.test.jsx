import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';

const LINK = '/api/v1/refund-account-links/tok-1';

async function fillAndSend(user) {
  await user.type(await screen.findByLabelText('Bank'), 'BIDV');
  await user.type(screen.getByLabelText('Account number'), '12345678901');
  await user.type(screen.getByLabelText('Account holder name'), 'NGUYEN VAN A');
  await user.click(screen.getByRole('button', { name: 'Review details' }));
  await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
  await user.click(screen.getByRole('button', { name: 'Send account' }));
}

// T165 (FR-040/FR-022d) and T169 (FR-022a): the account link page.
describe('RefundAccountPage', () => {
  it('sends one Idempotency-Key per attempt and reuses it when the same attempt is retried', async () => {
    let attempt = 0;
    const calls = mockFetch({
      [`POST ${LINK}`]: () => (++attempt === 1 ? json(503, { code: 'SERVICE_UNAVAILABLE' }) : new Response(null, { status: 204 })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/refund-account#tok-1', language: 'en' });
    await fillAndSend(user);
    await user.click(await screen.findByRole('button', { name: /retry|try again/i }));
    expect(await screen.findByText(/Your bank account was received/)).toBeInTheDocument();
    const posts = calls.filter(call => call.path === LINK);
    expect(posts).toHaveLength(2);
    expect(posts[0].headers['Idempotency-Key']).toBeTruthy();
    expect(posts[1].headers['Idempotency-Key']).toBe(posts[0].headers['Idempotency-Key']);
  });

  it('says the link is not valid for an unknown token (404) and asks for a new link when it is spent (410)', async () => {
    mockFetch({ [`POST ${LINK}`]: json(404, { code: 'RESOURCE_NOT_FOUND' }) });
    const first = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/refund-account#tok-1', language: 'en' });
    await fillAndSend(first);
    expect(await screen.findByText(/This link is not valid/)).toBeInTheDocument();
    view.unmount();

    mockFetch({ [`POST ${LINK}`]: json(410, { code: 'REFUND_ACCOUNT_LINK_NOT_USABLE' }) });
    const second = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/refund-account#tok-1', language: 'en' });
    await fillAndSend(second);
    expect(await screen.findByText(/Ask the shop to send you a new link/)).toBeInTheDocument();
    // Neither answer echoes the account number back.
    expect(screen.getAllByRole('alert').every(alert => !alert.textContent.includes('12345678901'))).toBe(true);
  });
});
