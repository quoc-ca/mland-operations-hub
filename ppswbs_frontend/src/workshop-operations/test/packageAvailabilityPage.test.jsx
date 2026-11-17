import { beforeEach, describe, expect, it } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { executePrivateStateCleanup } from '../../shared/auth/privateStateCleanup';
import PackageAvailabilityMatrixPage from '../pages/PackageAvailabilityMatrixPage';

const routes = [{ path: '/operations/package-availability', element: <PackageAvailabilityMatrixPage /> }];
const PATH = '/operations/package-availability';
const MATRIX = {
  packages: [
    { id: 1, name: 'Brass', lifecycleState: 'PUBLISHED' },
    { id: 2, name: 'Silver Clay 5g', lifecycleState: 'PUBLISHED' },
  ],
  locations: [
    { id: 10, name: 'Hoàn Kiếm', active: true },
    { id: 12, name: 'Đà Nẵng', active: false },
  ],
  cells: [
    { packageId: 1, locationId: 10, state: 'ACTIVE', version: 2 },
    { packageId: 2, locationId: 10, state: 'INACTIVE', version: 1 },
  ],
};
const PUT_1_10 = 'PUT /api/v1/workshop-packages/1/locations/10/availability';
const PUT_2_10 = 'PUT /api/v1/workshop-packages/2/locations/10/availability';
const GET = 'GET /api/v1/workshop-package-availability';
const PROFILE = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }) };

beforeEach(() => clearRoleCache());

const open = handlers => {
  const calls = mockFetch({ ...PROFILE, ...handlers });
  renderRoutes(routes, { path: PATH });
  return calls;
};
const box = name => screen.findByRole('checkbox', { name });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

describe('Package availability matrix page', () => {
  it('shows a busy skeleton while loading, then the matrix', async () => {
    const gate = deferred();
    open({ [GET]: async () => { await gate.promise; return json(200, MATRIX); } });
    expect(await screen.findByText('Đang tải ma trận…')).toBeInTheDocument();
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    gate.resolve();
    expect(await box('Brass tại Hoàn Kiếm')).toBeChecked();
    expect(screen.getByRole('heading', { name: 'Ma trận gói × chi nhánh' })).toBeInTheDocument();
    expect(document.querySelector('[aria-busy="true"]')).toBeNull();
  });

  it('shows the empty state with a link to packages and a reload button', async () => {
    const calls = open({ [GET]: json(200, { packages: [], locations: MATRIX.locations, cells: [] }) });
    expect(await screen.findByText('Chưa có gói hoặc chi nhánh để thiết lập')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đến Gói workshop' })).toHaveAttribute('href', '/operations/packages');
    await userEvent.setup().click(screen.getAllByRole('button', { name: 'Tải lại' }).at(-1));
    await waitFor(() => expect(calls.filter(c => c.method === 'GET' && c.path.endsWith('availability'))).toHaveLength(2));
  });

  it('sends the cell version, shows saving, and changes the cell only after the server confirms', async () => {
    const gate = deferred();
    const calls = open({
      [GET]: json(200, MATRIX),
      [PUT_2_10]: async () => { await gate.promise; return json(200, { packageId: 2, locationId: 10, state: 'ACTIVE', version: 2 }); },
    });
    const user = userEvent.setup();
    const cell = await box('Silver Clay 5g tại Hoàn Kiếm');
    await user.click(cell);
    await waitFor(() => expect(cell).toHaveAttribute('aria-disabled', 'true'));
    expect(cell).not.toBeChecked(); // no optimistic flip
    expect(screen.getByText('Đang lưu')).toBeInTheDocument();
    const put = calls.find(c => c.method === 'PUT');
    expect(put.body).toEqual({ state: 'ACTIVE', version: 1 });
    expect(put.headers['Idempotency-Key']).toBeTruthy();
    gate.resolve();
    await waitFor(() => expect(cell).toBeChecked());
    expect(cell).not.toHaveAttribute('aria-disabled');
    expect(await screen.findByText(/Đã bật bán/)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Silver Clay 5g tại Hoàn Kiếm');
  });

  it('uses version 0 for a pair with no stored row', async () => {
    const calls = open({
      [GET]: json(200, MATRIX),
      'PUT /api/v1/workshop-packages/1/locations/12/availability': json(200, { packageId: 1, locationId: 12, state: 'ACTIVE', version: 1 }),
    });
    const cell = await box('Brass tại Đà Nẵng');
    expect(cell).toBeEnabled();
    await userEvent.setup().click(cell);
    await waitFor(() => expect(cell).toBeChecked());
    expect(calls.find(c => c.method === 'PUT').body).toEqual({ state: 'ACTIVE', version: 0 });
  });

  it('hides the success message after a few seconds', async () => {
    open({ [GET]: json(200, MATRIX), [PUT_2_10]: json(200, { packageId: 2, locationId: 10, state: 'ACTIVE', version: 2 }) });
    const cell = await box('Silver Clay 5g tại Hoàn Kiếm');
    await userEvent.setup().click(cell);
    expect(await screen.findByText(/Đã bật bán/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/Đã bật bán/)).toBeNull(), { timeout: 6000 });
  }, 10000);

  it('quick toggles of two cells send two requests with different keys; a click on a saving cell sends nothing', async () => {
    const g1 = deferred();
    const g2 = deferred();
    const calls = open({
      [GET]: json(200, MATRIX),
      [PUT_1_10]: async () => { await g1.promise; return json(200, { packageId: 1, locationId: 10, state: 'INACTIVE', version: 3 }); },
      [PUT_2_10]: async () => { await g2.promise; return json(200, { packageId: 2, locationId: 10, state: 'ACTIVE', version: 2 }); },
    });
    const user = userEvent.setup();
    const a = await box('Brass tại Hoàn Kiếm');
    const b = await box('Silver Clay 5g tại Hoàn Kiếm');
    await user.click(a);
    await user.click(b);
    await user.click(a); // saving: ignored
    const puts = calls.filter(c => c.method === 'PUT');
    expect(puts).toHaveLength(2);
    expect(puts[0].headers['Idempotency-Key']).not.toBe(puts[1].headers['Idempotency-Key']);
    g1.resolve(); g2.resolve();
    await waitFor(() => expect(a).not.toBeChecked());
    await waitFor(() => expect(b).toBeChecked());
  });

  it('409 OPTIMISTIC_LOCK_CONFLICT reloads the cell, marks it, offers a matrix reload and does not retry', async () => {
    let reads = 0;
    const calls = open({
      [GET]: () => {
        reads += 1;
        return json(200, reads === 1 ? MATRIX : {
          ...MATRIX, cells: [{ packageId: 1, locationId: 10, state: 'INACTIVE', version: 5 }, MATRIX.cells[1]],
        });
      },
      [PUT_1_10]: json(409, { code: 'OPTIMISTIC_LOCK_CONFLICT', message: 'Conflict' }),
    });
    const user = userEvent.setup();
    const cell = await box('Brass tại Hoàn Kiếm');
    await user.click(cell);
    expect(await screen.findByText('Ô vừa được thay đổi ở nơi khác')).toBeInTheDocument();
    await waitFor(() => expect(cell).not.toBeChecked());
    expect(cell.closest('.pam-cell')).toHaveAttribute('data-error', 'conflict');
    expect(calls.filter(c => c.method === 'PUT')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Tải lại ma trận' }));
    await waitFor(() => expect(reads).toBe(3));
    expect(screen.queryByText('Ô vừa được thay đổi ở nơi khác')).toBeNull();
  });

  it('other failures keep the previous value and show an alert with the correlation reference and retry', async () => {
    let attempt = 0;
    const calls = open({
      [GET]: json(200, MATRIX),
      [PUT_1_10]: () => {
        attempt += 1;
        return attempt === 1
          ? json(503, { code: 'SERVICE_UNAVAILABLE', message: 'down', correlationId: 'corr-77' })
          : json(200, { packageId: 1, locationId: 10, state: 'INACTIVE', version: 3 });
      },
    });
    const user = userEvent.setup();
    const cell = await box('Brass tại Hoàn Kiếm');
    await user.click(cell);
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Không lưu được thay đổi');
    expect(alert).toHaveTextContent('corr-77');
    expect(cell).toBeChecked();
    expect(cell.closest('.pam-cell')).toHaveAttribute('data-error', 'error');
    await user.click(within(alert).getByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(cell).not.toBeChecked());
    const puts = calls.filter(c => c.method === 'PUT');
    expect(puts[1].headers['Idempotency-Key']).toBe(puts[0].headers['Idempotency-Key']);
  });

  it('a 400 write error keeps the value and shows the alert without an automatic retry', async () => {
    const calls = open({ [GET]: json(200, MATRIX), [PUT_1_10]: json(400, { code: 'VALIDATION_FAILED', message: 'bad' }) });
    const cell = await box('Brass tại Hoàn Kiếm');
    await userEvent.setup().click(cell);
    expect(await screen.findByRole('alert')).toHaveTextContent('Không lưu được thay đổi');
    expect(cell).toBeChecked();
    expect(calls.filter(c => c.method === 'PUT')).toHaveLength(1);
  });

  it('403 on read shows the denial page and no matrix', async () => {
    open({ [GET]: json(403, { code: 'FORBIDDEN', message: 'no' }) });
    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).toBeNull();
  });

  it('403 on write shows the denial page and drops the matrix', async () => {
    open({ [GET]: json(200, MATRIX), [PUT_1_10]: json(403, { code: 'FORBIDDEN', message: 'no' }) });
    const cell = await box('Brass tại Hoàn Kiếm');
    await userEvent.setup().click(cell);
    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).toBeNull();
  });

  it('session cleanup clears the matrix from memory and keeps data out of the URL', async () => {
    open({ [GET]: json(200, MATRIX) });
    await box('Brass tại Hoàn Kiếm');
    act(() => executePrivateStateCleanup());
    await waitFor(() => expect(screen.queryByRole('checkbox')).toBeNull());
    expect(window.location.href).not.toMatch(/Brass|corr|token/);
  });
});
