import { vi, beforeEach, describe, expect, it } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes, fakeSession } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { executePrivateStateCleanup } from '../../shared/auth/privateStateCleanup';
import { operationsRoutes } from '../routes';
import { activeFilterCount, formatTime, hasReversedRange, shortRef, toQuery, writeFilters } from '../components/auditReviewModel';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const OPS = 'GET /api/v1/workshop-audit/operations-events';
const SETTLEMENT = 'GET /api/v1/workshop-audit/settlement-records';
const PROFILE = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }) };

const opsRow = (id, extra = {}) => ({
  id, occurredAt: '2026-11-17T07:32:08Z', actor: { accountId: 12, displayName: 'Nguyễn Văn A' },
  action: 'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED', outcome: 'SUCCESS', sessionId: null,
  correlationId: 'c1f3aaaaaaaaaaaaa9a2', metadata: { toState: 'ACTIVE', ticketCode: 'TK-1' }, ...extra,
});
const settlementRow = (id, extra = {}) => ({
  id, occurredAt: '2026-11-17T08:10:44Z', actionCode: 'REFUND_APPROVED', aggregateType: 'SETTLEMENT_REFUND_REQUEST',
  aggregateReference: 'RR-1042', actor: { accountId: 12, displayName: 'Nguyễn Văn A' }, actorReference: 'account:12',
  correlationReference: '5ac2bbbbbbbbbbbbb10f', evidenceReference: 'EV-8812', reasonText: 'Khách hủy do ca bị hủy bởi Mland.', ...extra,
});

beforeEach(() => clearRoleCache());

const open = (handlers, path = '/operations/audit') => {
  const calls = mockFetch({ ...PROFILE, ...handlers });
  const view = renderRoutes(operationsRoutes, { path });
  return { calls, ...view };
};
const callsTo = (calls, path) => calls.filter(call => call.path === path);
const OPS_PATH = '/api/v1/workshop-audit/operations-events';
const SETTLEMENT_PATH = '/api/v1/workshop-audit/settlement-records';
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

describe('Audit review model', () => {
  it('builds the API query from trimmed filters and converts local date-times to ISO instants', () => {
    const query = toQuery({ action: ' SESSION_CREATED ', outcome: '', actorId: '12', from: '2026-11-10T00:00', to: '' });
    expect(query.action).toBe('SESSION_CREATED');
    expect(query).not.toHaveProperty('outcome');
    expect(query).not.toHaveProperty('to');
    expect(query.from).toBe(new Date('2026-11-10T00:00').toISOString());
  });

  it('keeps each tab\'s filters apart in the URL and removes empty ones', () => {
    let params = writeFilters(new URLSearchParams(), 'operations', { action: 'A', outcome: 'DENIED' });
    params = writeFilters(params, 'settlement', { actionCode: 'REFUND_APPROVED' });
    expect(params.get('o_action')).toBe('A');
    expect(params.get('s_actionCode')).toBe('REFUND_APPROVED');
    params = writeFilters(params, 'operations', { action: '', outcome: 'DENIED' });
    expect(params.has('o_action')).toBe(false);
    expect(params.get('s_actionCode')).toBe('REFUND_APPROVED');
    expect(activeFilterCount({ a: 'x', b: ' ', c: '' })).toBe(1);
  });

  it('detects a reversed range and formats time and references', () => {
    expect(hasReversedRange({ from: '2026-11-17T10:00', to: '2026-11-10T10:00' })).toBe(true);
    expect(hasReversedRange({ from: '2026-11-10T10:00', to: '2026-11-10T10:00' })).toBe(false);
    expect(hasReversedRange({ from: '2026-11-10T10:00', to: '' })).toBe(false);
    expect(formatTime('not a date')).toBe('—');
    expect(shortRef('c1f3aaaaaaaaaaaaa9a2')).toBe('c1f3…9a2');
    expect(shortRef('short')).toBe('short');
  });
});

describe('Audit review page', () => {
  it('lists Operations rows newest first with actor name and id, outcome text, and the 7-day note', async () => {
    const { calls } = open({ [OPS]: json(200, { items: [opsRow(2), opsRow(1, { actor: null, outcome: 'DENIED', sessionId: 2334 })], nextCursor: null }) });

    expect(await screen.findByText(/Chỉ đọc. Dòng mới nhất ở trên/)).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3); // header + 2
    expect(within(rows[1]).getByText('Nguyễn Văn A #12')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Thành công')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Hệ thống')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Bị từ chối')).toBeInTheDocument();
    expect(within(rows[2]).getByText('#2334')).toBeInTheDocument();
    expect(screen.getByText('Đang hiển thị 7 ngày gần nhất vì chưa chọn “Từ”.')).toBeInTheDocument();
    expect(callsTo(calls, OPS_PATH)[0].query).toEqual({});
  });

  it('opens whitelisted metadata on demand and never shows personal data wording as data', async () => {
    open({ [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }) });
    const button = await screen.findByRole('button', { name: 'Chi tiết' });

    await userEvent.click(button);

    expect(screen.getByText('toState')).toBeInTheDocument();
    expect(screen.getByText('TK-1')).toBeInTheDocument();
    expect(screen.getByText('Chỉ hiện các khóa được phép. Không có email, số điện thoại hay họ tên khách.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thu gọn' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('applies filters to the request and to the URL, and keeps them filled', async () => {
    const { calls, router } = open({ [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }) });
    await screen.findByRole('button', { name: 'Chi tiết' });

    await userEvent.type(screen.getByLabelText('Hành động'), 'SESSION_CREATED');
    await userEvent.selectOptions(screen.getByLabelText('Kết quả'), 'DENIED');
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));

    await waitFor(() => expect(callsTo(calls, OPS_PATH)).toHaveLength(2));
    expect(callsTo(calls, OPS_PATH)[1].query).toMatchObject({ action: 'SESSION_CREATED', outcome: 'DENIED' });
    expect(router.state.location.search).toContain('o_action=SESSION_CREATED');
    expect(screen.getByLabelText('Hành động')).toHaveValue('SESSION_CREATED');
    expect(screen.getByRole('button', { name: 'Bộ lọc (2)' })).toBeInTheDocument();
  });

  it('restores the same filtered view from the URL, as after returning from sign-in (FR-055)', async () => {
    const { calls } = open(
      { [OPS]: json(200, { items: [], nextCursor: null }) },
      '/operations/audit?o_action=SESSION_CREATED&o_outcome=DENIED',
    );

    await screen.findByText('Không có thao tác nào khớp bộ lọc');
    expect(callsTo(calls, OPS_PATH)[0].query).toMatchObject({ action: 'SESSION_CREATED', outcome: 'DENIED' });
    expect(screen.getByLabelText('Hành động')).toHaveValue('SESSION_CREATED');
  });

  it('refuses a reversed time range on the page and keeps the inputs filled, without a request', async () => {
    const { calls } = open({ [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }) });
    await screen.findByRole('button', { name: 'Chi tiết' });

    await userEvent.type(screen.getByLabelText('Từ'), '2026-11-17T10:00');
    await userEvent.type(screen.getByLabelText('Đến'), '2026-11-10T10:00');
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('“Từ” phải trước hoặc bằng “Đến”.');
    expect(screen.getByLabelText('Từ')).toHaveValue('2026-11-17T10:00');
    expect(callsTo(calls, OPS_PATH)).toHaveLength(1);
  });

  it('pages by cursor with "Tải thêm", appends rows and drops the button on the last page', async () => {
    const { calls } = open({
      [OPS]: call => (call.query.cursor
        ? json(200, { items: [opsRow(1, { action: 'OLDER_ROW' })], nextCursor: null })
        : json(200, { items: [opsRow(2, { action: 'NEWER_ROW' })], nextCursor: 'cur-2' })),
    });
    await screen.findByText('NEWER_ROW');

    await userEvent.click(screen.getByRole('button', { name: 'Tải thêm' }));

    expect(await screen.findByText('OLDER_ROW')).toBeInTheDocument();
    expect(screen.getByText('NEWER_ROW')).toBeInTheDocument();
    expect(callsTo(calls, OPS_PATH)[1].query.cursor).toBe('cur-2');
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).not.toBeInTheDocument();
  });

  it('does not repeat a row the next page returns again', async () => {
    open({
      [OPS]: call => (call.query.cursor
        ? json(200, { items: [opsRow(2, { action: 'DUP_ROW' }), opsRow(1, { action: 'LAST_ROW' })], nextCursor: null })
        : json(200, { items: [opsRow(2, { action: 'DUP_ROW' })], nextCursor: 'cur-2' })),
    });
    await screen.findByText('DUP_ROW');

    await userEvent.click(screen.getByRole('button', { name: 'Tải thêm' }));

    await screen.findByText('LAST_ROW');
    expect(screen.getAllByText('DUP_ROW')).toHaveLength(1);
  });

  it('shows the loading state with the filter button disabled while the first page is read', async () => {
    const gate = deferred();
    open({ [OPS]: () => gate.promise });

    expect(await screen.findByText('Đang tải nhật ký…')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Lọc' })).toBeDisabled();
    await act(async () => gate.resolve(json(200, { items: [opsRow(1)], nextCursor: null })));
    await screen.findByRole('button', { name: 'Chi tiết' });
    expect(screen.getByRole('button', { name: 'Lọc' })).toBeEnabled();
  });

  it('shows the empty state and clears filters from it', async () => {
    const { calls } = open(
      { [OPS]: json(200, { items: [], nextCursor: null }) },
      '/operations/audit?o_action=NOTHING',
    );
    await screen.findByText('Không có thao tác nào khớp bộ lọc');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));

    await waitFor(() => expect(callsTo(calls, OPS_PATH)).toHaveLength(2));
    expect(callsTo(calls, OPS_PATH)[1].query).toEqual({});
  });

  it('shows a safe error with the correlation id, keeps the filters and retries', async () => {
    let attempt = 0;
    const { calls } = open(
      {
        [OPS]: () => {
          attempt += 1;
          return attempt === 1
            ? json(503, { code: 'DEPENDENCY_UNAVAILABLE', correlationId: 'corr-503' })
            : json(200, { items: [opsRow(1)], nextCursor: null });
        },
      },
      '/operations/audit?o_action=SESSION_CREATED',
    );

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Không tải được nhật ký.');
    expect(alert).toHaveTextContent('corr-503');
    expect(screen.getByLabelText('Hành động')).toHaveValue('SESSION_CREATED');

    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));

    await screen.findByRole('button', { name: 'Chi tiết' });
    expect(callsTo(calls, OPS_PATH)[1].query).toMatchObject({ action: 'SESSION_CREATED' });
  });

  it('keeps the rows already shown when "Tải thêm" fails and retries only that page', async () => {
    let failMore = true;
    const { calls } = open({
      [OPS]: call => {
        if (!call.query.cursor) return json(200, { items: [opsRow(2, { action: 'KEPT_ROW' })], nextCursor: 'cur-2' });
        if (failMore) { failMore = false; return json(500, { code: 'INTERNAL_ERROR', correlationId: 'corr-more' }); }
        return json(200, { items: [opsRow(1, { action: 'MORE_ROW' })], nextCursor: null });
      },
    });
    await screen.findByText('KEPT_ROW');

    await userEvent.click(screen.getByRole('button', { name: 'Tải thêm' }));
    await screen.findByRole('alert');
    expect(screen.getByText('KEPT_ROW')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    await screen.findByText('MORE_ROW');
    expect(callsTo(calls, OPS_PATH).at(-1).query.cursor).toBe('cur-2');
  });

  it('shows no rows when the backend refuses the read (403)', async () => {
    open({ [OPS]: json(403, { code: 'FORBIDDEN', correlationId: 'corr-403' }) });

    await screen.findByRole('alert');
    expect(screen.queryByRole('button', { name: 'Chi tiết' })).not.toBeInTheDocument();
    expect(screen.queryByText('Nguyễn Văn A #12')).not.toBeInTheDocument();
  });

  it('drops the rows from memory on session loss (shared private-state cleanup)', async () => {
    open({ [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }) });
    await screen.findByText('Nguyễn Văn A #12');

    act(() => executePrivateStateCleanup());

    await waitFor(() => expect(screen.queryByText('Nguyễn Văn A #12')).not.toBeInTheDocument());
  });

  it('asks for sign-in without reading anything when there is no session', async () => {
    const calls = mockFetch({ ...PROFILE, [OPS]: json(200, { items: [], nextCursor: null }) });
    renderRoutes(operationsRoutes, { path: '/operations/audit', session: fakeSession('signed-out') });

    const signIn = await screen.findByRole('link', { name: 'Đăng nhập' });
    expect(signIn).toHaveAttribute('href', expect.stringContaining('returnTo=%2Foperations%2Faudit'));
    expect(callsTo(calls, OPS_PATH)).toHaveLength(0);
  });

  describe('Settlement tab', () => {
    it('reads the Settlement route with its own filters and shows the full reason text and the note', async () => {
      const { calls } = open({
        [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }),
        [SETTLEMENT]: json(200, { items: [settlementRow(5), settlementRow(4, { actor: null, actorReference: 'SYSTEM', reasonText: null, evidenceReference: null })], nextCursor: null }),
      });
      await screen.findByRole('button', { name: 'Chi tiết' });

      await userEvent.click(screen.getByRole('tab', { name: 'Quyết toán' }));

      expect(await screen.findByText('Khách hủy do ca bị hủy bởi Mland.')).toBeInTheDocument();
      expect(screen.getByText('EV-8812')).toBeInTheDocument();
      expect(screen.getByText('SYSTEM')).toBeInTheDocument();
      expect(screen.getByText('Lý do hiện đầy đủ (tối đa 500 ký tự); có thể chứa thông tin cá nhân, chỉ Manager xem.')).toBeInTheDocument();
      expect(screen.queryByLabelText('Kết quả')).not.toBeInTheDocument(); // no outcome or session filter for this source
      expect(screen.queryByLabelText('Ca (ID)')).not.toBeInTheDocument();
      expect(callsTo(calls, SETTLEMENT_PATH)).toHaveLength(1);
      expect(screen.getByRole('tab', { name: 'Quyết toán' })).toHaveAttribute('aria-selected', 'true');
    });

    it('filters by action code and actor, and keeps each tab\'s own filters when switching', async () => {
      const { calls, router } = open({
        [OPS]: json(200, { items: [opsRow(1)], nextCursor: null }),
        [SETTLEMENT]: json(200, { items: [settlementRow(5)], nextCursor: null }),
      });
      await screen.findByRole('button', { name: 'Chi tiết' });
      await userEvent.type(screen.getByLabelText('Hành động'), 'SESSION_CREATED');
      await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));
      await waitFor(() => expect(callsTo(calls, OPS_PATH)).toHaveLength(2));

      await userEvent.click(screen.getByRole('tab', { name: 'Quyết toán' }));
      await screen.findByText('Khách hủy do ca bị hủy bởi Mland.');
      await userEvent.type(screen.getByLabelText('Mã hành động'), 'REFUND_APPROVED');
      await userEvent.type(screen.getByLabelText('Người thực hiện (ID)'), '12');
      await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));
      await waitFor(() => expect(callsTo(calls, SETTLEMENT_PATH)).toHaveLength(2));
      expect(callsTo(calls, SETTLEMENT_PATH)[1].query).toMatchObject({ actionCode: 'REFUND_APPROVED', actorId: '12' });

      await userEvent.click(screen.getByRole('tab', { name: 'Vận hành' }));
      expect(await screen.findByLabelText('Hành động')).toHaveValue('SESSION_CREATED');
      expect(router.state.location.search).toContain('s_actionCode=REFUND_APPROVED');
    });
  });
});
