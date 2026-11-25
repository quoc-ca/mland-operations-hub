import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { designReviewRoutes } from '../routes';

const open = (path, options) => renderRoutes(designReviewRoutes, { path, ...options });

describe('Staff & Manager Custom Order Operations Page (Dev C)', () => {
  it('renders Custom Orders Operations page with KPI cards and tab badges', async () => {
    mockFetch({
      'GET /api/v1/custom-orders': json(200, [
        {
          customOrderReference: 'CO-PAID001',
          status: 'PAID',
          createdAt: new Date().toISOString(),
          customerName: 'Nguyễn Văn A',
          customerEmail: 'vana@example.com',
          designRequestId: 'dr-101',
          designRequestReference: 'DR-REQ101',
          designOriginalDescription: 'Yêu cầu nhẫn đôi hoa hồng',
          quote: {
            quoteReference: 'QUO-001',
            totalAmount: '20000000',
            currency: 'VND',
            status: 'PAID',
          },
          designSnapshot: {
            metalType: 'Vàng trắng 18k',
            gemstoneType: 'Kim cương tự nhiên',
            ringSize: '14',
            engravingText: 'A & B',
          },
        },
        {
          customOrderReference: 'CO-PROD002',
          status: 'IN_PRODUCTION',
          createdAt: new Date().toISOString(),
          customerName: 'Trần Thị B',
          customerEmail: 'thib@example.com',
          quote: {
            quoteReference: 'QUO-002',
            totalAmount: '12000000',
            currency: 'VND',
            status: 'PAID',
          },
        },
      ]),
    });

    open('/staff/custom-orders', { session: fakeSession('signed-in', { role: 'STAFF' }) });

    expect(await screen.findByText('Điều phối & Chế tác Đơn hàng Custom')).toBeInTheDocument();
    expect(screen.getByText('CO-PAID001')).toBeInTheDocument();
    expect(screen.getByText(/DR-REQ101/)).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByText(/Vàng trắng 18k/)).toBeInTheDocument();
    expect(screen.getByText(/Kim cương tự nhiên/)).toBeInTheDocument();
    expect(screen.getAllByText('Chờ chế tác').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Đang gia công').length).toBeGreaterThan(0);
  });

  it('allows Staff to transition PAID order to IN_PRODUCTION via radio button and confirmation popup', async () => {
    const user = userEvent.setup();
    let updatedStatusBody = null;

    mockFetch({
      'GET /api/v1/custom-orders': json(200, [
        {
          customOrderReference: 'CO-ORDER123',
          status: 'PAID',
          createdAt: new Date().toISOString(),
          customerName: 'Lê Văn C',
          quote: {
            quoteReference: 'QUO-123',
            totalAmount: '15000000',
            currency: 'VND',
            status: 'PAID',
          },
        },
      ]),
      'POST /api/v1/custom-orders/CO-ORDER123/status': (req) => {
        updatedStatusBody = req.body;
        return json(200, {
          customOrderReference: 'CO-ORDER123',
          status: 'IN_PRODUCTION',
          productionStatus: 'IN_PRODUCTION',
        });
      },
    });

    open('/staff/custom-orders', { session: fakeSession('signed-in', { role: 'STAFF' }) });

    // Find the radio button for 'IN_PRODUCTION'
    const inProdRadio = await screen.findByRole('radio', { name: /Đang gia công/i });
    await user.click(inProdRadio);

    // Confirmation popup modal appears
    expect(await screen.findByText(/Xác nhận thay đổi trạng thái đơn hàng/i)).toBeInTheDocument();
    expect(screen.getAllByText(/CO-ORDER123/i).length).toBeGreaterThan(0);

    // Click confirm in modal
    const confirmBtn = screen.getByRole('button', { name: /Xác nhận chuyển/i });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(updatedStatusBody).toEqual({
        productionStatus: 'IN_PRODUCTION',
        status: 'IN_PRODUCTION',
      });
    });
  });

  it('allows Staff to transition IN_PRODUCTION order to READY_FOR_FULFILMENT via radio button and confirmation popup', async () => {
    const user = userEvent.setup();
    let updatedStatusBody = null;

    mockFetch({
      'GET /api/v1/custom-orders': json(200, [
        {
          customOrderReference: 'CO-PROD999',
          status: 'IN_PRODUCTION',
          createdAt: new Date().toISOString(),
          customerName: 'Hoàng Thị D',
          quote: {
            quoteReference: 'QUO-999',
            totalAmount: '18000000',
            currency: 'VND',
            status: 'PAID',
          },
        },
      ]),
      'POST /api/v1/custom-orders/CO-PROD999/status': (req) => {
        updatedStatusBody = req.body;
        return json(200, {
          customOrderReference: 'CO-PROD999',
          status: 'READY_FOR_FULFILMENT',
          productionStatus: 'COMPLETED',
          fulfilmentStatus: 'READY_FOR_FULFILMENT',
        });
      },
    });

    open('/staff/custom-orders', { session: fakeSession('signed-in', { role: 'STAFF' }) });

    // Switch to IN_PRODUCTION tab
    const inProdTab = await screen.findByRole('tab', { name: /Đang gia công/i });
    await user.click(inProdTab);

    // Click radio button for READY_FOR_FULFILMENT
    const readyRadio = await screen.findByRole('radio', { name: /Sẵn sàng giao hàng/i });
    await user.click(readyRadio);

    // Modal appears
    expect(await screen.findByText(/Xác nhận thay đổi trạng thái đơn hàng/i)).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /Xác nhận chuyển/i });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(updatedStatusBody).toEqual({
        productionStatus: 'COMPLETED',
        fulfilmentStatus: 'READY_FOR_FULFILMENT',
        status: 'READY_FOR_FULFILMENT',
      });
    });
  });
});
