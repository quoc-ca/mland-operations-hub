import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { RetailBenefitPanel } from './RetailBenefitPanel';
import { addressApiClient } from '../../../accountmanagement';

const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };

export default function MemberCheckoutPage() {
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · MEMBER</p><h1>Thanh toán</h1><p>Kiểm tra sản phẩm, nhận hàng và ưu đãi trước khi đặt hàng.</p></div><Link to="/member/cart" className="retail-back-link">← Quay lại giỏ hàng</Link></div><RequireSession requiredText="Đăng nhập để thanh toán."><CheckoutContent /></RequireSession></main>;
}

function CheckoutContent() {
  const api = useRetailApi();
  const format = useFormat();
  const cart = useResource(() => api.getCart(), []);
  const addresses = useResource(() => addressApiClient.getOwnAddresses(), []);
  const savedVoucherResource = useResource(() => api.listSavedVouchers(), []);
  const quoteCommand = useCommand();
  const shippingCommand = useCommand();
  const createCommand = useCommand();
  const paymentCommand = useCommand();
  const location = useLocation();
  // Lines ticked on the cart page; null (direct visit) keeps the whole-cart checkout.
  const cartLineIds = Array.isArray(location.state?.cartLineIds) && location.state.cartLineIds.length ? location.state.cartLineIds.map(String) : null;
  const lineFilter = cartLineIds ? new Set(cartLineIds) : null;
  // Voucher/points chosen on the cart page arrive via router state; the checkout still recalculates them.
  const [selection, setSelection] = useState(() => {
    const fromCart = location.state?.benefitSelection;
    return fromCart && typeof fromCart.choice === 'string' ? { ...initialSelection, ...fromCart } : initialSelection;
  });
  const [selectedAddressId, setSelectedAddressId] = useState(null);
  const [fulfilmentByLocation, setFulfilmentByLocation] = useState({});
  const [shippingQuotes, setShippingQuotes] = useState({});
  const [shippingMessage, setShippingMessage] = useState('');
  const [quote, setQuote] = useState(null);
  const [quotedSelection, setQuotedSelection] = useState(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [checkout, setCheckout] = useState(null);

  const groups = useMemo(() => {
    const byLocation = new Map();
    (cart.data?.lines || []).filter(line => !lineFilter || lineFilter.has(String(line.id))).forEach(line => byLocation.set(line.locationId, [...(byLocation.get(line.locationId) || []), line]));
    return [...byLocation.entries()].map(([locationId, lines]) => ({ locationId, lines }));
  }, [cart.data, location.state]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (selectedAddressId != null || !addresses.data?.length) return;
    setSelectedAddressId((addresses.data.find(item => item.isDefault) || addresses.data[0]).id);
  }, [addresses.data, selectedAddressId]);

  const selectedAddress = useMemo(() => {
    if (!addresses.data?.length) return null;
    return addresses.data.find(item => item.id === Number(selectedAddressId)) || addresses.data.find(item => item.isDefault) || addresses.data[0];
  }, [addresses.data, selectedAddressId]);
  const recipient = selectedAddress ? {
    address: [selectedAddress.detailAddress, selectedAddress.hamletName].filter(Boolean).join(', '),
    province: selectedAddress.provinceName,
    district: selectedAddress.districtName,
    ward: selectedAddress.wardName,
    recipientName: selectedAddress.recipientName,
    recipientPhone: selectedAddress.phoneNumber,
  } : null;
  const methodFor = locationId => fulfilmentByLocation[String(locationId)] || 'PICKUP';
  const resetCheckout = () => { setQuote(null); setQuotedSelection(null); setAcknowledged(false); setCheckout(null); };
  const branchOrders = useMemo(() => groups.map(group => {
    const method = methodFor(group.locationId);
    return { locationId: Number(group.locationId), method, shippingQuoteId: method === 'GHTK' ? shippingQuotes[group.locationId]?.quoteId || null : null, recipient: method === 'GHTK' ? recipient : null };
  }), [groups, fulfilmentByLocation, shippingQuotes, recipient]);
  const hasUnquotedDelivery = groups.some(group => methodFor(group.locationId) === 'GHTK' && !shippingQuotes[group.locationId]?.quoteId);
  const selectionBody = { ...selection, voucherCode: selection.choice === 'VOUCHER' ? selection.voucherCode.trim() : null };
  const selectionSignature = JSON.stringify(selectionBody);
  const savedVouchers = (savedVoucherResource.data?.items || savedVoucherResource.data || []).map(item => ({ code: item.promotion?.code || item.code, name: item.promotion?.name || item.name || item.promotion?.code || item.code, availability: item.availability })).filter(item => item.code);

  const quoteDelivery = async locationId => {
    if (!recipient) { setShippingMessage('Hãy thêm và chọn một địa chỉ nhận hàng trước khi dùng GHTK.'); return; }
    setShippingMessage('');
    const result = await shippingCommand.run(() => api.quoteShipping({ locationId: Number(locationId), recipient, ...(cartLineIds ? { cartLineIds } : {}) }));
    if (!result.ok) return;
    setShippingQuotes(current => ({ ...current, [locationId]: result.result }));
    if (!result.result.deliveryAvailable || !result.result.quoteId) setShippingMessage(result.result.message || 'GHTK chưa hỗ trợ giao đến địa chỉ này. Hãy chọn nhận tại cửa hàng hoặc một địa chỉ khác.');
  };
  const changeMethod = async (locationId, method) => {
    setFulfilmentByLocation(current => ({ ...current, [locationId]: method }));
    resetCheckout();
    if (method !== 'GHTK') { setShippingQuotes(current => { const next = { ...current }; delete next[locationId]; return next; }); return; }
    await quoteDelivery(locationId);
  };
  const changeAddress = event => {
    setSelectedAddressId(Number(event.target.value));
    setFulfilmentByLocation({}); setShippingQuotes({});
    setShippingMessage('Địa chỉ đã đổi. Các chi nhánh được chuyển về nhận tại cửa hàng; hãy chọn GHTK lại nếu cần giao tận nơi.');
    resetCheckout();
  };
  const preview = async () => {
    if (hasUnquotedDelivery) { setShippingMessage('Mỗi chi nhánh giao GHTK cần có báo giá còn hiệu lực trước khi tính tổng tiền.'); return; }
    const result = await quoteCommand.run(() => api.previewBenefits({ selection: selectionBody, branchOrders, ...(cartLineIds ? { cartLineIds } : {}) }));
    if (result.ok) { setQuote(result.result); setQuotedSelection(selectionSignature); setAcknowledged(false); setCheckout(null); }
  };
  const create = async () => {
    if (!quote?.previewId || hasUnquotedDelivery) return;
    const result = await createCommand.run(key => api.createCheckout({ branchOrders, benefitPreviewId: quote.previewId, benefitSelection: selectionBody, benefitTermsAccepted: true, ...(cartLineIds ? { cartLineIds } : {}) }, key));
    if (result.ok) setCheckout(result.result);
  };
  const pay = async () => {
    if (!checkout?.id) return;
    const result = await paymentCommand.run(key => api.initiatePayment(checkout.id, key));
    if (result.ok && result.result.paymentUrl) window.location.assign(result.result.paymentUrl);
  };

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (!groups.length) return <section className="workspace-card empty-state"><p>{cartLineIds ? 'Sản phẩm bạn chọn không còn trong giỏ hàng.' : 'Giỏ hàng đã trống.'}</p><Link className="button outline" to="/member/cart">Về giỏ hàng</Link><Link className="button primary" to="/products">Xem sản phẩm</Link></section>;
  return <div className="retail-checkout-layout">
    <section className="workspace-card retail-address-card"><div className="retail-address-heading"><h2>Địa chỉ nhận hàng</h2><Link to="/member/addresses">Quản lý địa chỉ</Link></div>{addresses.loading && !addresses.data && <Loading />}{addresses.error && <ErrorNotice error={addresses.error} onRetry={addresses.reload} />}{addresses.data?.length ? <><select className="retail-address-select" value={selectedAddress?.id ?? ''} onChange={changeAddress} aria-label="Chọn địa chỉ nhận hàng">{addresses.data.map(address => <option key={address.id} value={address.id}>{address.isDefault ? 'Mặc định — ' : ''}{address.recipientName} · {address.detailAddress}, {address.wardName}, {address.districtName}, {address.provinceName}</option>)}</select><p><strong>{selectedAddress?.recipientName}</strong> · {selectedAddress?.phoneNumber} {selectedAddress?.isDefault && <span className="retail-address-default">Mặc định</span>}</p><p>{selectedAddress && [selectedAddress.detailAddress, selectedAddress.hamletName, selectedAddress.wardName, selectedAddress.districtName, selectedAddress.provinceName].filter(Boolean).join(', ')}</p></> : <p>Bạn chưa có địa chỉ đã lưu. <Link to="/member/addresses">Thêm địa chỉ</Link></p>}<p className="muted small">Chỉ dùng địa chỉ đã lưu trong hồ sơ. GHTK cần đủ tỉnh/thành, quận/huyện và phường/xã để báo giá chính xác.</p></section>
    <section className="workspace-card retail-checkout-products"><div className="retail-card-title"><div><span className="retail-kicker">ĐƠN HÀNG CỦA BẠN</span><h2>Sản phẩm theo chi nhánh</h2></div></div>{groups.map(group => { const method = methodFor(group.locationId); const shipping = shippingQuotes[group.locationId]; return <div className="checkout-branch" key={group.locationId}><strong>Chi nhánh #{group.locationId}</strong>{group.lines.map(line => <div className="retail-checkout-item" key={line.id}><span className="retail-product-placeholder" aria-hidden="true">◇</span><span><strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong><small>Size {line.size ?? '—'} · Số lượng {line.quantity}</small></span><b>{line.indicativePrice == null ? 'Chưa có giá' : format.money((BigInt(line.indicativePrice) * BigInt(line.quantity)).toString())}</b></div>)}<div className="retail-fulfilment-options"><label className={method === 'PICKUP' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'PICKUP'} onChange={() => changeMethod(group.locationId, 'PICKUP')} /> Nhận tại cửa hàng <span>Không tính phí giao hàng</span></label><label className={method === 'GHTK' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'GHTK'} disabled={!selectedAddress || shippingCommand.pending} onChange={() => changeMethod(group.locationId, 'GHTK')} /> Giao tận nơi qua GHTK <span>{shipping?.quoteId ? format.money(shipping.deliveryFee) : 'Lấy báo giá'}</span></label></div>{method === 'GHTK' && <div className="retail-shipping-status">{shipping?.quoteId ? <>Phí giao hàng được báo giá cho địa chỉ đã chọn. Có hiệu lực đến {new Date(shipping.expiresAt).toLocaleTimeString('vi-VN')}. <button type="button" className="text-link" disabled={shippingCommand.pending} onClick={() => quoteDelivery(group.locationId)}>Lấy lại phí</button></> : shipping?.message || 'Đang chờ báo giá GHTK.'}</div>}</div>; })}{(shippingMessage || shippingCommand.error) && <p className="notice error">{shippingMessage || shippingCommand.error?.message}</p>}</section>
    <section className="workspace-card retail-checkout-benefits"><div className="retail-card-title"><div><span className="retail-kicker">DÀNH RIÊNG CHO BẠN</span><h2>Ưu đãi & điểm</h2></div></div><RetailBenefitPanel selection={selection} onChange={next => { setSelection(next); resetCheckout(); }} savedVouchers={savedVouchers} maxPoints={quotedSelection === selectionSignature ? quote?.maxPoints : null} minRedeemPoints={quotedSelection === selectionSignature ? quote?.minRedeemPoints : null} quote={quote} quoteMatches={quotedSelection === selectionSignature} acknowledged={acknowledged} onAcknowledge={setAcknowledged} onRecalculate={preview} onConfirm={create} pending={quoteCommand.pending || createCommand.pending || Boolean(checkout)} hideActions />{savedVoucherResource.error && <ErrorNotice error={savedVoucherResource.error} onRetry={savedVoucherResource.reload} />}{quoteCommand.error && <ErrorNotice error={quoteCommand.error} onRetry={quoteCommand.retry} onReload={cart.reload} />}</section>
    <section className="retail-summary retail-payment-section"><div className="retail-payment-method"><h2>Phương thức thanh toán</h2><strong>VNPay</strong></div><div className="retail-payment-details"><h2>Chi tiết thanh toán</h2>{quote ? <><div><span>Tiền hàng</span><strong>{format.money(quote.merchandiseSubtotal)}</strong></div><div><span>Ưu đãi sản phẩm</span><strong>−{format.money(quote.discountAmount || '0')}</strong></div><div><span>Giảm bằng điểm</span><strong>−{format.money(quote.pointsDiscountAmount || '0')}</strong></div><div><span>Phí vận chuyển</span><strong>{format.money(quote.shippingTotal || '0')}</strong></div><div className="retail-total"><span>Tổng thanh toán</span><strong>{format.money(quote.payableAmount)}</strong></div></> : <p className="muted">Chọn hình thức nhận hàng và tính tổng tiền để nhận báo giá xác minh.</p>}</div><div className="retail-payment-actions"><button type="button" className="button outline" disabled={quoteCommand.pending || createCommand.pending || Boolean(checkout) || hasUnquotedDelivery} onClick={preview}>Tính lại ưu đãi</button><button type="button" className="button primary" disabled={!quote?.eligible || quotedSelection !== selectionSignature || !acknowledged || quoteCommand.pending || createCommand.pending || Boolean(checkout) || hasUnquotedDelivery} onClick={create}>Đặt hàng</button></div>{createCommand.error && <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} onReload={cart.reload} />}{checkout && <><SuccessNotice>Đã tạo checkout #{checkout.id}.</SuccessNotice><button type="button" className="button primary" disabled={paymentCommand.pending} onClick={pay}>{paymentCommand.pending ? 'Đang chuyển VNPay…' : 'Thanh toán qua VNPay'}</button><Link className="text-link" to="/member/orders">Xem đơn mua</Link></>}{paymentCommand.error && <ErrorNotice error={paymentCommand.error} onRetry={paymentCommand.retry} />}</section>
  </div>;
}
