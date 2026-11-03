import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useFormat } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../../loyaltypromotion/api/useLoyaltyApi';
import { useRetailApi } from '../api/useRetailApi';
import { RetailBenefitPanel } from './RetailBenefitPanel';

const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };

export default function MemberCheckoutPage() {
  return <main className="container public-page retail-page"><p className="eyebrow">FE-08 · MEMBER</p><h1>Checkout</h1><RequireSession requiredText="Đăng nhập để checkout."><CheckoutContent /></RequireSession></main>;
}

function CheckoutContent() {
  const api = useRetailApi();
  const loyaltyApi = useLoyaltyApi();
  const format = useFormat();
  const cart = useResource(() => api.getCart(), []);
  const savedVoucherResource = useResource(() => loyaltyApi.listSavedVouchers({ limit: 100 }), []);
  const loyaltyAccount = useResource(() => loyaltyApi.getOwnAccount(), []);
  const quoteCommand = useCommand();
  const createCommand = useCommand();
  const paymentCommand = useCommand();
  const [selection, setSelection] = useState(initialSelection);
  const [quote, setQuote] = useState(null);
  const [quotedSelection, setQuotedSelection] = useState(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [checkout, setCheckout] = useState(null);
  const groups = useMemo(() => {
    const byLocation = new Map();
    (cart.data?.lines || []).forEach(line => byLocation.set(line.locationId, [...(byLocation.get(line.locationId) || []), line]));
    return [...byLocation.entries()].map(([locationId, lines]) => ({ locationId, lines }));
  }, [cart.data]);
  const branchOrders = groups.map(group => ({ locationId: Number(group.locationId), method: 'PICKUP', shippingQuoteId: null }));
  const selectionBody = { ...selection, voucherCode: selection.choice === 'VOUCHER' ? selection.voucherCode.trim() : null };
  const selectionSignature = JSON.stringify(selectionBody);
  const savedVouchers = (savedVoucherResource.data?.items || savedVoucherResource.data || []).map(item => ({
    code: item.promotion?.code || item.code,
    name: item.promotion?.name || item.name || item.promotion?.code || item.code,
    availability: item.availability,
  })).filter(item => item.code);
  const preview = async () => {
    const result = await quoteCommand.run(() => api.previewBenefits({ selection: selectionBody, branchOrders }));
    if (result.ok) { setQuote(result.result); setQuotedSelection(selectionSignature); setAcknowledged(false); setCheckout(null); }
  };
  const create = async () => {
    if (!quote?.previewId) return;
    const result = await createCommand.run(key => api.createCheckout({ branchOrders, benefitPreviewId: quote.previewId, benefitSelection: selectionBody, benefitTermsAccepted: true }, key));
    if (result.ok) setCheckout(result.result);
  };
  const pay = async () => {
    if (!checkout?.id) return;
    const result = await paymentCommand.run(key => api.initiatePayment(checkout.id, key));
    if (result.ok && result.result.paymentUrl) window.location.assign(result.result.paymentUrl);
  };
  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (!groups.length) return <section className="workspace-card empty-state"><p>Giỏ hàng đã trống.</p><Link className="button primary" to="/products">Xem sản phẩm</Link></section>;
  return <div className="retail-checkout-layout">
    <section className="workspace-card">
      <h2>Sản phẩm theo chi nhánh</h2>
      {groups.map(group => <div className="checkout-branch" key={group.locationId}>
        <strong>Chi nhánh #{group.locationId}</strong>
        {group.lines.map(line => <p key={line.id}>{line.productName || `Sản phẩm #${line.variantId}`} · Size {line.size ?? '—'} × {line.quantity} <span>{format.money(line.indicativePrice)}</span></p>)}
        <div className="pickup-choice"><strong>Nhận tại cửa hàng</strong><span>Không tính phí giao hàng</span></div>
      </div>)}
      <p className="muted small">Giao tận nơi sẽ hiển thị khi GHTK trả được báo giá do server phát hành.</p>
    </section>
    <section className="workspace-card">
      <RetailBenefitPanel
        selection={selection}
        onChange={next => { setSelection(next); setAcknowledged(false); setCheckout(null); }}
        savedVouchers={savedVouchers}
        account={loyaltyAccount.data}
        merchandiseSubtotal={quote?.merchandiseSubtotal || cart.data?.lines?.reduce((total, line) => total + BigInt(line.indicativePrice || '0') * BigInt(line.quantity || 0), 0n).toString() || '0'}
        quote={quote}
        quoteMatches={quotedSelection === selectionSignature}
        acknowledged={acknowledged}
        onAcknowledge={setAcknowledged}
        onRecalculate={preview}
        onConfirm={create}
        pending={quoteCommand.pending || createCommand.pending || Boolean(checkout)}
      />
      {savedVoucherResource.error && <ErrorNotice error={savedVoucherResource.error} onRetry={savedVoucherResource.reload} />}
      {loyaltyAccount.error && <ErrorNotice error={loyaltyAccount.error} onRetry={loyaltyAccount.reload} />}
      {quoteCommand.error && <ErrorNotice error={quoteCommand.error} onRetry={quoteCommand.retry} onReload={cart.reload} />}
    </section>
    <aside className="retail-summary"><h2>Chi tiết thanh toán</h2>
      {quote ? <><div><span>Tiền hàng</span><strong>{format.money(quote.merchandiseSubtotal)}</strong></div><div><span>Ưu đãi sản phẩm</span><strong>−{format.money(quote.discountAmount || '0')}</strong></div><div><span>Giảm bằng điểm</span><strong>−{format.money(quote.pointsDiscountAmount || '0')}</strong></div><div><span>Phí vận chuyển</span><strong>{format.money(quote.shippingTotal || '0')}</strong></div><div className="retail-total"><span>Tổng thanh toán</span><strong>{format.money(quote.payableAmount)}</strong></div></> : <p className="muted">Kiểm tra thông tin để nhận báo giá xác minh.</p>}
      {createCommand.error && <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} onReload={cart.reload} />}
      {checkout && <><SuccessNotice>Đã tạo checkout #{checkout.id}.</SuccessNotice><button type="button" className="button primary" disabled={paymentCommand.pending} onClick={pay}>{paymentCommand.pending ? 'Đang chuyển VNPay…' : 'Thanh toán qua VNPay'}</button><Link className="text-link" to="/member/orders">Xem đơn mua</Link></>}{paymentCommand.error && <ErrorNotice error={paymentCommand.error} onRetry={paymentCommand.retry} />}
    </aside>
  </div>;
}
