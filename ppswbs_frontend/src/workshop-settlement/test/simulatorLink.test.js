import { describe, expect, it } from 'vitest';
import { simulatorLink } from '../pages/SettlementWorkspacePage';

describe('simulatorLink', () => {
  const pay = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=30000000&vnp_TxnRef=FIN-123&vnp_SecureHash=x';

  it('points at the backend simulator page for the merchant reference', () => {
    expect(simulatorLink(pay, 'https://t.ngrok.dev/')).toBe('https://t.ngrok.dev/api/v1/integrations/payments/simulator/FIN-123');
  });

  it('is empty when the simulator is not configured or the url has no reference', () => {
    expect(simulatorLink(pay, '')).toBe('');
    expect(simulatorLink('https://x.test/pay', 'https://t.ngrok.dev')).toBe('');
    expect(simulatorLink('not a url', 'https://t.ngrok.dev')).toBe('');
  });
});
