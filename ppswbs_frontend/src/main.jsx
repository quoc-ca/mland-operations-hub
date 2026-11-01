import React, { useEffect, useMemo, useState } from 'react';
import { screenGroups, screenIndex } from './screenRegistry';
import { getPublicProduct, listPublicCategories, listPublicProducts, publicMediaUrl } from './api/catalogue';
import { WorkshopListPage } from './workshops/WorkshopListPage';
import { WorkshopDetailPage } from './workshops/WorkshopDetailPage';
import { WorkshopBookingPage } from './workshops/WorkshopBookingPage';
import { WorkshopPaymentReturnPage } from './workshops/WorkshopPaymentReturnPage';
import { WorkshopLookupPage } from './workshops/WorkshopLookupPage';
import { WorkshopAccessPage } from './workshops/WorkshopAccessPage';
import './style.css';
import './retail.css';
import './operations.css';
import './catalogue-api.css';
import './frames.css';
import './retail-flow.css';

const rings = [
  { id: 'silver', name: 'Nhẫn bạc tối giản', price: '1.250.000 ₫', category: 'Tối giản', availability: 'Có thể mua' },
  { id: 'gold', name: 'Nhẫn vàng dấu ấn', price: '2.800.000 ₫', category: 'Kỷ niệm', availability: 'Có thể mua' },
  { id: 'pair', name: 'Nhẫn đôi Mland', price: '3.900.000 ₫', category: 'Cặp đôi', availability: 'Hết hàng' },
];

const banners = [
  { eyebrow: 'MỘT MÓN TRANG SỨC, MỘT CÂU CHUYỆN', title: 'Dấu ấn của riêng bạn', description: 'Khám phá nhẫn và trải nghiệm workshop tại Mland.' },
  { eyebrow: 'TRẢI NGHIỆM TẠI MLAND', title: 'Tự tay tạo nên kỷ niệm', description: 'Tìm hiểu các gói workshop và chọn lịch phù hợp.' },
];

function toHref(id) { return `#/${id}`; }
function parseRoute() {
  const path = window.location.pathname.replace(/\/+$/, '') || '/';
  const hashRaw = window.location.hash.replace(/^#[/]?/, '');
  const hashPath = hashRaw.split('?')[0];

  const candidate = (path !== '/' && path !== '') ? path : ('/' + hashPath);

  if (candidate === '/workshops') {
    return { route: 'workshops', params: {} };
  }
  const bookMatch = candidate.match(/^\/workshops\/([^/]+)\/book$/);
  if (bookMatch) {
    return { route: 'booking-setup', params: { packageId: decodeURIComponent(bookMatch[1]) } };
  }
  const detailMatch = candidate.match(/^\/workshops\/([^/]+)$/);
  if (detailMatch) {
    return { route: 'workshop-details', params: { packageId: decodeURIComponent(detailMatch[1]) } };
  }
  if (candidate === '/workshop-booking/lookup') {
    return { route: 'booking-lookup', params: {} };
  }
  if (candidate === '/workshop-booking/access') {
    return { route: 'booking-access', params: {} };
  }
  const paymentReturnMatch = candidate.match(/^\/workshop-booking\/([^/]+)\/payment-return$/);
  if (paymentReturnMatch) {
    return { route: 'workshop-payment-return', params: { bookingCode: decodeURIComponent(paymentReturnMatch[1]) } };
  }

  const legacyKey = hashPath || 'home';
  return { route: legacyKey, params: {} };
}

function useRoute() {
  const [routeInfo, setRouteInfo] = useState(parseRoute());
  useEffect(() => {
    const listener = () => setRouteInfo(parseRoute());
    window.addEventListener('hashchange', listener);
    window.addEventListener('popstate', listener);
    return () => {
      window.removeEventListener('hashchange', listener);
      window.removeEventListener('popstate', listener);
    };
  }, []);
  return routeInfo;
}

function CarouselControls({ index, count, change, label }) {
  return <div className="carousel-controls" aria-label={label}><span aria-live="polite">{String(index + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}</span><button type="button" aria-label="Trước" onClick={() => change((index - 1 + count) % count)}>←</button><button type="button" aria-label="Tiếp theo" onClick={() => change((index + 1) % count)}>→</button></div>;
}

function Header() { return <header className="header"><a className="brand" href={toHref('home')}>MLAND</a><nav aria-label="Điều hướng chính"><a href={toHref('catalogue')}>Sản phẩm</a><a href={toHref('workshops')}>Workshop</a><a href={toHref('stores')}>Cửa hàng</a></nav><a className="header-login" href={toHref('login')}>Đăng nhập</a></header>; }

function HomePage() {
  const [banner, setBanner] = useState(0); const slide = banners[banner];
  useEffect(() => { const timer = window.setInterval(() => setBanner(value => (value + 1) % banners.length), 6000); return () => window.clearInterval(timer); }, []);
  return <><section className="hero"><div className="container hero-inner" key={banner}><p className="eyebrow">{slide.eyebrow}</p><h1>{slide.title}</h1><p className="lead">{slide.description}</p><div className="actions"><a className="button primary" href={toHref('catalogue')}>Khám phá sản phẩm</a><a className="button outline" href={toHref('workshops')}>Tìm hiểu workshop</a></div><CarouselControls index={banner} count={banners.length} change={setBanner} label="Điều hướng banner" /></div></section><section className="container products"><p className="eyebrow">BỘ SƯU TẬP</p><h2>Sản phẩm dành cho bạn</h2><p>Khám phá những thiết kế tại Mland.</p><div className="preview-grid">{rings.map((ring, index) => <a className="ring-card" href={toHref('product-details')} key={ring.id}><RingArt tone={ring.id} /><strong>{ring.name}</strong><span>{ring.price}</span><span className="text-link">Xem chi tiết →</span></a>)}</div></section><Callout /></>;
}

function RingArt({ tone = 'silver' }) { return <div className={`ring-visual ${tone}`} aria-label="Ảnh minh hoạ sản phẩm"><span /></div>; }
function Callout() { return <section className="callout"><div className="container"><p className="eyebrow light">TRẢI NGHIỆM TẠI MLAND</p><h2>Tạo nên chiếc nhẫn của bạn</h2><p>Tìm hiểu các gói workshop và chọn lịch phù hợp.</p><a href={toHref('workshops')} className="button outline workshop-action">Xem workshop</a></div></section>; }

function Catalogue() {
  const [query, setQuery] = useState(''); const [category, setCategory] = useState('all'); const [onlyAvailable, setOnlyAvailable] = useState(false);
  const filtered = useMemo(() => rings.filter(ring => (!query || ring.name.toLowerCase().includes(query.toLowerCase())) && (category === 'all' || ring.category === category) && (!onlyAvailable || ring.availability === 'Có thể mua')), [query, category, onlyAvailable]);
  return <PublicLayout eyebrow="CATALOGUE" title="Khám phá bộ sưu tập"><div className="filter-bar"><label>Tìm kiếm<input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tên sản phẩm" /></label><label>Danh mục<select value={category} onChange={event => setCategory(event.target.value)}><option value="all">Tất cả</option><option>Tối giản</option><option>Kỷ niệm</option><option>Cặp đôi</option></select></label><label className="check"><input type="checkbox" checked={onlyAvailable} onChange={event => setOnlyAvailable(event.target.checked)} /> Chỉ hiện sản phẩm có thể mua</label></div><div className="catalogue-grid">{filtered.map(ring => <a className="ring-card" href={toHref('product-details')} key={ring.id}><RingArt tone={ring.id} /><span className="badge">{ring.category}</span><strong>{ring.name}</strong><span>{ring.price}</span><span className={ring.availability === 'Có thể mua' ? 'status ok' : 'status'}>{ring.availability}</span></a>)}</div>{filtered.length === 0 && <div className="empty-state">Không có sản phẩm khớp với bộ lọc. Hãy thử lại với từ khoá khác.</div>}</PublicLayout>;
}

function ProductDetails() { const [size, setSize] = useState('12'); return <PublicLayout eyebrow="SẢN PHẨM" title="Nhẫn bạc tối giản"><div className="detail-grid"><RingArt tone="silver" /><div><p className="price">1.250.000 ₫</p><p>Thiết kế thanh lịch, phù hợp cho một món quà nhỏ nhưng có dấu ấn riêng.</p><label>Chọn size<select value={size} onChange={event => setSize(event.target.value)}><option value="10">Size 10</option><option value="12">Size 12</option><option value="14">Size 14</option></select></label><p className="status ok">Có thể mua</p><a className="button primary" href={toHref('login')}>Đăng nhập để mua</a><p className="muted">Khách có thể xem sản phẩm; thêm vào giỏ cần tài khoản Member.</p></div></div></PublicLayout>; }

function PublicLayout({ eyebrow, title, children }) { return <main className="container public-page"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{children}</main>; }

function LoginLike({ mode }) { const title = mode === 'register' ? 'Tạo tài khoản' : mode === 'forgot-password' ? 'Khôi phục mật khẩu' : 'Đăng nhập'; return <main className="auth-page"><form className="auth-card"><p className="eyebrow">TÀI KHOẢN</p><h1>{title}</h1><label>Email<input type="email" placeholder="you@example.com" /></label>{mode !== 'forgot-password' && <label>Mật khẩu<input type="password" placeholder="••••••••" /></label>}<button type="button" className="button primary">{title}</button><a className="text-link" href={toHref(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? 'Chưa có tài khoản?' : 'Quay lại đăng nhập'}</a></form></main>; }

function BookingFlow({ title }) { const steps = ['Gói & thiết kế', 'Chi nhánh', 'Lịch & ca', 'Thông tin', 'Xác nhận']; return <Workspace screen={{ title, groupLabel: 'Workshop & thiết kế' }}><div className="steps">{steps.map((step, index) => <div className={index === 0 ? 'step active' : 'step'} key={step}><span>{index + 1}</span>{step}</div>)}</div><section className="workspace-card"><h2>Thông tin đang chọn</h2><div className="form-grid"><label>Gói workshop<select><option>Signature workshop</option><option>Silver clay workshop</option><option>Wax workshop</option></select></label><label>Thiết kế<select><option>Chọn sau</option><option>Mẫu có sẵn</option><option>Tự thiết kế</option></select></label></div><button type="button" className="button primary">Tiếp tục</button></section></Workspace>; }

function CartPage() { return <Workspace screen={screenIndex.cart}><section className="workspace-card"><div className="card-head"><h2>Giỏ hàng theo chi nhánh</h2><a className="button outline" href={toHref('catalogue')}>Tiếp tục mua</a></div><BranchCart branch="MLAND Quận 1" items={[['Nhẫn bạc tối giản', 'Size 12', '1.250.000 ₫']]} /><BranchCart branch="MLAND Thủ Đức" items={[['Nhẫn vàng dấu ấn', 'Size 14', '2.800.000 ₫']]} /><div className="cart-total"><span>Tạm tính</span><strong>4.050.000 ₫</strong></div><a className="button primary" href={toHref('checkout')}>Tiến hành checkout</a></section></Workspace>; }
function BranchCart({ branch, items }) { return <section className="branch-cart"><div><strong>{branch}</strong><span>Giao hàng được tính riêng theo chi nhánh.</span></div>{items.map(([name, size, price]) => <div className="cart-line" key={name}><RingArt tone={name.includes('vàng') ? 'gold' : 'silver'} /><div><strong>{name}</strong><span>{size}</span><span className="muted">Số lượng <button type="button">−</button> 1 <button type="button">+</button></span></div><strong>{price}</strong></div>)}</section>; }

function CheckoutPage() { const [delivery, setDelivery] = useState('shipping'); return <Workspace screen={screenIndex.checkout}><div className="checkout-grid"><section className="workspace-card"><h2>Nhận hàng</h2><label className="choice"><input type="radio" checked={delivery === 'shipping'} onChange={() => setDelivery('shipping')} /> Giao tận nơi</label><label className="choice"><input type="radio" checked={delivery === 'pickup'} onChange={() => setDelivery('pickup')} /> Nhận tại cửa hàng</label>{delivery === 'shipping' ? <div className="form-grid"><label>Người nhận<input placeholder="Họ và tên" /></label><label>Số điện thoại<input placeholder="Số điện thoại" /></label><label>Địa chỉ<textarea placeholder="Địa chỉ giao hàng" /></label></div> : <div className="notice">Staff tại đúng chi nhánh sẽ chuẩn bị đơn; bạn chỉ cần xuất trình thông tin đơn khi nhận.</div>}<h2>Phương thức thanh toán</h2><label className="choice"><input type="radio" defaultChecked /> VNPay</label><label className="choice"><input type="radio" /> Thanh toán tại cửa hàng</label></section><aside className="checkout-summary"><h2>Tóm tắt đơn</h2><p>MLAND Quận 1 <span>1.250.000 ₫</span></p><p>Phí ship (1 nhẫn × 50g) <span>35.000 ₫</span></p><p>MLAND Thủ Đức <span>2.800.000 ₫</span></p><p>Phí ship (1 nhẫn × 50g) <span>42.000 ₫</span></p><hr /><p className="total"><strong>Tổng cộng</strong><strong>4.127.000 ₫</strong></p><button type="button" className="button primary">Xác nhận checkout</button><small>Phí ship là minh hoạ. Báo giá sẽ được xác nhận lại trước thanh toán.</small></aside></div></Workspace>; }

function WorkshopCatalogue({ details = false }) { return <PublicLayout eyebrow="WORKSHOP" title={details ? 'Signature Workshop' : 'Workshop tại MLAND'}>{details ? <div className="detail-grid"><RingArt tone="gold" /><div><p className="price">Từ 890.000 ₫ / người</p><p>Tự tay tạo nhẫn cùng nghệ nhân; gói đã bao gồm hướng dẫn, dụng cụ và hình ảnh kỷ niệm.</p><div className="info-list"><span>Thời lượng: 150 phút</span><span>Số người: 1–4</span><span>Đặt cọc: 50%</span></div><a className="button primary" href={toHref('booking-setup')}>Đặt workshop</a></div></div> : <div className="catalogue-grid">{[['Signature Workshop','Tùy chỉnh mẫu nhẫn của riêng bạn','890.000 ₫'],['Silver Clay Workshop','Nặn bạc clay thành chiếc nhẫn đầu tiên','790.000 ₫'],['Wax Workshop','Tạo mẫu sáp và chúng tôi hoàn thiện','1.290.000 ₫']].map(([name, copy, price], index) => <a className="ring-card" href={toHref('workshop-details')} key={name}><RingArt tone={index === 1 ? 'silver' : index === 2 ? 'pair' : 'gold'} /><strong>{name}</strong><span>{copy}</span><strong>{price}</strong><span className="text-link">Xem chi tiết →</span></a>)}</div>}</PublicLayout>; }

function OrdersPage({ details = false }) { return <Workspace screen={screenIndex[details ? 'retail-order-details' : 'my-orders']}>{details ? <section className="workspace-card"><div className="card-head"><div><h2>Đơn #ML-240012</h2><span className="status ok">Đang giao hàng</span></div><button className="button outline" type="button">Liên hệ hỗ trợ</button></div><div className="order-detail"><div><h3>MLAND Quận 1</h3><p>Nhẫn bạc tối giản • Size 12 • 1.250.000 ₫</p><p>Mã vận đơn: Đang cập nhật</p></div><div><h3>Tiến trình</h3><p>Đã xác nhận → Đã bàn giao đơn vị vận chuyển → Đang giao</p></div></div><p className="prototype-note">Khi GHTK được kết nối, trạng thái và mốc dự kiến giao sẽ lấy từ API giao hàng.</p></section> : <section className="workspace-card"><div className="card-head"><h2>Đơn mua gần đây</h2><a className="button outline" href={toHref('catalogue')}>Mua tiếp</a></div><div className="order-list">{[['ML-240012','2 sáng hồi','Đang giao hàng','4.127.000 ₫'],['ML-240009','05/10/2026','Hoàn thành','1.250.000 ₫'],['ML-240006','30/09/2026','Đã hủy','2.800.000 ₫']].map(([id,date,status,total]) => <a href={toHref('retail-order-details')} className="order-row" key={id}><strong>{id}</strong><span>{date}</span><span className={status === 'Đang giao hàng' ? 'status ok' : 'status'}>{status}</span><strong>{total}</strong></a>)}</div></section>}</Workspace>; }

function StaffOrderOperations() { return <Workspace screen={screenIndex['retail-order-operations']}><section className="workspace-card"><div className="card-head"><div><h2>Đơn #ML-240012</h2><p className="muted">MLAND Quận 1 • Giao tận nơi</p></div><span className="status ok">Đã thanh toán</span></div><div className="operation-grid"><button className="operation-action" type="button"><strong>1. Chuẩn bị hàng</strong><span>Xác nhận sản phẩm đã sẵn sàng</span></button><button className="operation-action" type="button"><strong>2. Bàn giao đơn vị vận chuyển</strong><span>Ghi nhận mã giao nhận khi GHTK đã xác nhận</span></button><button className="operation-action" type="button"><strong>3. Cập nhật ghi chú</strong><span>Thêm thông tin nội bộ cho đơn</span></button></div></section></Workspace>; }

function ManagerDashboard() { return <Workspace screen={screenIndex['manager-dashboard']}><section className="dashboard-filter"><label>Khoảng xem<select><option>Tháng 10 / 2026</option><option>Tuần này</option><option>Hôm nay</option></select></label><label>Từ ngày<input type="date" defaultValue="2026-10-01" /></label><label>Đến ngày<input type="date" defaultValue="2026-10-31" /></label><button className="button primary" type="button">Áp dụng</button></section><section className="metric-grid"><article><span>Doanh thu đã xác nhận</span><strong>128,5M ₫</strong></article><article><span>Đơn hoàn thành</span><strong>86</strong></article><article><span>Workshop đã diễn ra</span><strong>24</strong></article></section><section className="workspace-card"><div className="card-head"><h2>Kết quả theo chi nhánh</h2><span className="status ok">Dữ liệu: COMPLETE</span></div><div className="table"><div className="table-row header-row"><span>Chi nhánh</span><span>Doanh thu</span><span>Đơn</span><span>Workshop</span></div><div className="table-row"><span>Quận 1</span><span>75,2M ₫</span><span>49</span><span>14</span></div><div className="table-row"><span>Thủ Đức</span><span>53,3M ₫</span><span>37</span><span>10</span></div></div></section></Workspace>; }

function Workspace({ screen, children }) {
  const group = screenGroups.find(item => item.id === screen.group) || screenGroups[0];
  return <main className="app-shell"><aside className="side-nav"><p className="eyebrow">{screen.groupLabel}</p><strong>MLAND</strong>{group.screens.map(([id, title]) => <a key={id} className={id === screen.id ? 'active' : ''} href={toHref(id)}>{title}</a>)}</aside><section className="workspace"><div className="workspace-top"><div><p className="eyebrow">{screen.groupLabel}</p><h1>{screen.title}</h1></div><a className="button outline" href={toHref('home')}>Về trang chủ</a></div>{children || <GenericContent screen={screen} />}</section></main>;
}

function GenericContent({ screen }) { const action = screen.title.includes('List') || screen.title.includes('Orders') || screen.title.includes('Accounts') ? 'Tạo mới' : 'Lưu thay đổi'; return <><section className="metric-grid"><article><span>Đang xử lý</span><strong>12</strong></article><article><span>Hoàn thành</span><strong>48</strong></article><article><span>Cần chú ý</span><strong>3</strong></article></section><section className="workspace-card"><div className="card-head"><h2>{screen.title}</h2><button type="button" className="button primary">{action}</button></div><div className="table"><div className="table-row header-row"><span>Mã</span><span>Thông tin</span><span>Trạng thái</span><span>Cập nhật</span></div>{['ML-001', 'ML-002', 'ML-003'].map((code, index) => <div className="table-row" key={code}><span>{code}</span><span>Dữ liệu minh hoạ cho {screen.title}</span><span className="status ok">Đang hoạt động</span><span>{index + 1} giờ trước</span></div>)}</div></section><p className="prototype-note">Màn UI đang dùng dữ liệu minh hoạ. API, phân quyền và trạng thái nghiệp vụ sẽ được nối theo từng feature contract.</p></>; }

function Dashboard({ screen }) { return <Workspace screen={screen}><section className="metric-grid"><article><span>Doanh thu</span><strong>28,5M ₫</strong></article><article><span>Đơn mới</span><strong>18</strong></article><article><span>Việc cần xử lý</span><strong>6</strong></article></section><section className="workspace-card"><h2>Thao tác nhanh</h2><div className="quick-grid">{['Catalogue', 'Workshop', 'Đơn hàng', 'Khách hàng'].map(item => <a href={toHref(screen.group === 'manager' ? 'revenue-dashboard' : 'catalogue')} key={item}>{item}<span>→</span></a>)}</div></section></Workspace>; }

function LiveCatalogue() {
  const [query, setQuery] = useState(''); const [categoryId, setCategoryId] = useState(''); const [onlyAvailable, setOnlyAvailable] = useState(false);
  const [state, setState] = useState({ loading: true, error: '', products: [], categories: [] });
  const load = () => {
    setState(previous => ({ ...previous, loading: true, error: '' }));
    Promise.all([listPublicProducts({ q: query, categoryId, canBuy: onlyAvailable || undefined }), listPublicCategories()])
      .then(([products, categories]) => setState({ loading: false, error: '', products: products.items || [], categories: categories.items || [] }))
      .catch(() => setState(previous => ({ ...previous, loading: false, error: 'Không tải được catalogue. Hãy kiểm tra backend rồi thử lại.' })));
  };
  useEffect(() => { const timer = window.setTimeout(load, 250); return () => window.clearTimeout(timer); }, [query, categoryId, onlyAvailable]);
  return <PublicLayout eyebrow="CATALOGUE" title="Khám phá bộ sưu tập"><div className="filter-bar"><label>Tìm kiếm<input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tên sản phẩm" /></label><label>Danh mục<select value={categoryId} onChange={event => setCategoryId(event.target.value)}><option value="">Tất cả</option>{state.categories.map(category => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label><label className="check"><input type="checkbox" checked={onlyAvailable} onChange={event => setOnlyAvailable(event.target.checked)} /> Chỉ hiện sản phẩm có thể mua</label></div>{state.loading && <div className="empty-state">Đang tải sản phẩm…</div>}{state.error && <div className="empty-state"><p>{state.error}</p><button type="button" className="button primary" onClick={load}>Thử lại</button></div>}{!state.loading && !state.error && <div className="catalogue-grid">{state.products.map(product => <a className="ring-card" href={`${toHref('product-details')}?productId=${encodeURIComponent(product.id)}`} key={product.id}>{product.images?.[0]?.url ? <img className="product-image" src={publicMediaUrl(product.images[0].url)} alt={product.images[0].altText || product.name} /> : <RingArt tone="silver" />}<span className="badge">{product.category?.name || 'Trang sức'}</span><strong>{product.name}</strong><span>{product.priceRange?.minimum || '—'} ₫</span><span className={product.canBuy ? 'status ok' : 'status'}>{product.canBuy ? 'Có thể mua' : 'Tạm hết hàng'}</span></a>)}</div>}{!state.loading && !state.error && state.products.length === 0 && <div className="empty-state">Không có sản phẩm khớp với bộ lọc hiện tại.</div>}</PublicLayout>;
}

function LiveProductDetails() {
  const productId = new URLSearchParams(window.location.hash.split('?')[1] || '').get('productId');
  const [state, setState] = useState({ loading: Boolean(productId), error: '', product: null }); const [variantId, setVariantId] = useState('');
  const load = () => { if (!productId) return; setState({ loading: true, error: '', product: null }); getPublicProduct(productId).then(product => { setState({ loading: false, error: '', product }); setVariantId(product.variants?.find(variant => variant.saleEnabled && variant.canBuy)?.id || ''); }).catch(() => setState({ loading: false, error: 'Không tìm thấy hoặc không tải được sản phẩm này.', product: null })); };
  useEffect(() => { load(); }, [productId]);
  if (!productId) return <PublicLayout eyebrow="SẢN PHẨM" title="Chọn một sản phẩm"><div className="empty-state"><p>Hãy chọn sản phẩm từ catalogue để xem chi tiết.</p><a className="button primary" href={toHref('catalogue')}>Mở catalogue</a></div></PublicLayout>;
  if (state.loading) return <PublicLayout eyebrow="SẢN PHẨM" title="Đang tải"><div className="empty-state">Đang tải thông tin sản phẩm…</div></PublicLayout>;
  if (state.error || !state.product) return <PublicLayout eyebrow="SẢN PHẨM" title="Không thể tải sản phẩm"><div className="empty-state"><p>{state.error}</p><button className="button primary" type="button" onClick={load}>Thử lại</button></div></PublicLayout>;
  const product = state.product; const selected = product.variants?.find(variant => variant.id === variantId);
  return <PublicLayout eyebrow="SẢN PHẨM" title={product.name}><div className="detail-grid">{product.images?.[0]?.url ? <img className="detail-image" src={publicMediaUrl(product.images[0].url)} alt={product.images[0].altText || product.name} /> : <RingArt tone="silver" />}<div><p className="price">{selected?.appliedPrice || product.priceRange?.minimum || '—'} ₫</p><p>{product.description || 'Chưa có mô tả cho sản phẩm này.'}</p><label>Chọn size<select value={variantId} onChange={event => setVariantId(event.target.value)}>{product.variants?.map(variant => <option value={variant.id} disabled={!variant.saleEnabled || !variant.canBuy} key={variant.id}>Size {variant.size}{!variant.saleEnabled || !variant.canBuy ? ' — không thể mua' : ''}</option>)}</select></label><p className={selected?.saleEnabled && selected?.canBuy ? 'status ok' : 'status'}>{selected?.saleEnabled && selected?.canBuy ? 'Có thể mua' : 'Chưa thể mua size này'}</p><button className="button primary" disabled={!selected?.saleEnabled || !selected?.canBuy} type="button">Thêm vào giỏ</button><p className="prototype-note">Nút thêm giỏ đang chờ Firebase Member session để gọi API FE-08 có xác thực; thông tin catalogue ở màn này là API thật.</p></div></div></PublicLayout>;
}

function QuantityPicker({ value, min = 1, max = 99, onChange, disabled = false }) { return <span className="quantity-picker"><button type="button" disabled={disabled || value <= min} onClick={() => onChange(value - 1)}>−</button><output>{value}</output><button type="button" disabled={disabled || value >= max} onClick={() => onChange(value + 1)}>+</button></span>; }

function RetailProductDetails() {
  const productId = new URLSearchParams(window.location.hash.split('?')[1] || '').get('productId');
  const [state, setState] = useState({ loading: Boolean(productId), error: '', product: null }); const [variantId, setVariantId] = useState(''); const [quantity, setQuantity] = useState(1);
  const load = () => { if (!productId) return; setState({ loading: true, error: '', product: null }); getPublicProduct(productId).then(product => { const first = product.variants?.find(item => item.saleEnabled && item.canBuy); setVariantId(first?.id || ''); setState({ loading: false, error: '', product }); }).catch(() => setState({ loading: false, error: 'Không tìm thấy hoặc không tải được sản phẩm này.', product: null })); };
  useEffect(() => { load(); }, [productId]);
  if (!productId) return <PublicLayout eyebrow="SẢN PHẨM" title="Chọn một sản phẩm"><div className="empty-state"><a className="button primary" href={toHref('catalogue')}>Mở catalogue</a></div></PublicLayout>;
  if (state.loading) return <PublicLayout eyebrow="SẢN PHẨM" title="Đang tải"><div className="empty-state">Đang tải thông tin sản phẩm…</div></PublicLayout>;
  if (state.error || !state.product) return <PublicLayout eyebrow="SẢN PHẨM" title="Không thể tải sản phẩm"><div className="empty-state"><p>{state.error}</p><button className="button primary" type="button" onClick={load}>Thử lại</button></div></PublicLayout>;
  const product = state.product; const selected = product.variants?.find(item => item.id === variantId); const purchasable = Boolean(selected?.saleEnabled && selected?.canBuy);
  return <PublicLayout eyebrow="SẢN PHẨM" title={product.name}><div className="detail-grid">{product.images?.[0]?.url ? <img className="detail-image" src={publicMediaUrl(product.images[0].url)} alt={product.images[0].altText || product.name} /> : <RingArt tone="silver" />}<div><p className="price">{selected?.appliedPrice || product.priceRange?.minimum || '—'} ₫</p><p>{product.description || 'Chưa có mô tả cho sản phẩm này.'}</p><label>Chọn size<select value={variantId} onChange={event => { setVariantId(event.target.value); setQuantity(1); }}>{product.variants?.map(item => <option value={item.id} disabled={!item.saleEnabled || !item.canBuy} key={item.id}>Size {item.size}{!item.saleEnabled || !item.canBuy ? ' — không thể mua' : ''}</option>)}</select></label><div className="quantity-block"><span>Số lượng</span><QuantityPicker value={quantity} onChange={setQuantity} disabled={!purchasable} /></div><p className={purchasable ? 'status ok' : 'status'}>{purchasable ? 'Có thể mua' : 'Chưa thể mua size này'}</p><button className="button primary" disabled={!purchasable} type="button">Đăng nhập để thêm {quantity} vào giỏ</button><p className="prototype-note">Số lượng tuân theo API cart: từ 1 đến 99. Khi có Firebase session, nút này sẽ gọi `addCartLine({`{ variantId, locationId, quantity }`})`.</p></div></div></PublicLayout>;
}

function RetailCartWorkspace() {
  const [lines, setLines] = useState([{ id: 'line-1', branch: 'MLAND Quận 1', name: 'Nhẫn bạc tối giản', size: 'Size 12', price: 1250000, quantity: 1 }, { id: 'line-2', branch: 'MLAND Thủ Đức', name: 'Nhẫn vàng dấu ấn', size: 'Size 14', price: 2800000, quantity: 1 }]);
  const update = (id, quantity) => setLines(current => current.map(line => line.id === id ? { ...line, quantity } : line)); const remove = id => setLines(current => current.filter(line => line.id !== id)); const subtotal = lines.reduce((sum, line) => sum + line.price * line.quantity, 0); const groups = [...new Set(lines.map(line => line.branch))];
  return <Workspace screen={screenIndex.cart}><section className="workspace-card"><div className="card-head"><div><h2>Giỏ hàng theo chi nhánh</h2><p className="muted">{lines.length}/50 dòng sản phẩm. Mỗi chi nhánh tạo một phần đơn riêng.</p></div><a className="button outline" href={toHref('catalogue')}>Tiếp tục mua</a></div>{lines.length === 0 ? <div className="empty-state"><p>Giỏ hàng chưa có sản phẩm.</p><a className="button primary" href={toHref('catalogue')}>Xem sản phẩm</a></div> : groups.map(branch => <section className="branch-cart" key={branch}><div><strong>{branch}</strong><span>Phí giao được báo riêng khi checkout.</span></div>{lines.filter(line => line.branch === branch).map(line => <div className="cart-line" key={line.id}><RingArt tone={line.name.includes('vàng') ? 'gold' : 'silver'} /><div><strong>{line.name}</strong><span>{line.size}</span><QuantityPicker value={line.quantity} onChange={quantity => update(line.id, quantity)} /><button className="text-button" type="button" onClick={() => remove(line.id)}>Xóa</button></div><strong>{(line.price * line.quantity).toLocaleString('vi-VN')} ₫</strong></div>)}</section>)}<div className="cart-total"><span>Tạm tính</span><strong>{subtotal.toLocaleString('vi-VN')} ₫</strong></div><a className="button primary" href={toHref('checkout')}>Xem lại checkout</a></section><p className="prototype-note">Tăng/giảm/xóa đang hoạt động trong UI. Khi có Firebase, mỗi thay đổi sẽ gọi `PATCH` hoặc `DELETE /ready-ring-cart/lines` và dùng dữ liệu phản hồi của server.</p></Workspace>;
}

function RetailCheckoutWorkspace() {
  const [delivery, setDelivery] = useState('shipping'); const [form, setForm] = useState({ name: '', phone: '', address: '', voucher: '', points: '' }); const [errors, setErrors] = useState({}); const [notice, setNotice] = useState('');
  const setField = key => event => setForm(current => ({ ...current, [key]: event.target.value }));
  const validate = () => { const next = {}; if (delivery === 'shipping') { if (!form.name.trim()) next.name = 'Nhập người nhận.'; if (!/^(0|\+84)\d{9,10}$/.test(form.phone.replace(/\s/g, ''))) next.phone = 'Số điện thoại chưa hợp lệ.'; if (!form.address.trim()) next.address = 'Nhập địa chỉ giao hàng.'; } setErrors(next); if (!Object.keys(next).length) setNotice('Thông tin hợp lệ. Khi có Firebase, hệ thống sẽ lấy báo giá, kiểm tra ưu đãi và tạo checkout.'); };
  return <Workspace screen={screenIndex.checkout}><div className="checkout-grid"><section className="workspace-card"><h2>Nhận hàng</h2><label className="choice"><input type="radio" checked={delivery === 'shipping'} onChange={() => setDelivery('shipping')} /> Giao tận nơi</label><label className="choice"><input type="radio" checked={delivery === 'pickup'} onChange={() => setDelivery('pickup')} /> Nhận tại cửa hàng</label>{delivery === 'shipping' ? <div className="form-grid"><label>Người nhận<input value={form.name} onChange={setField('name')} placeholder="Họ và tên" />{errors.name && <small className="field-error">{errors.name}</small>}</label><label>Số điện thoại<input value={form.phone} onChange={setField('phone')} placeholder="0xxxxxxxxx" />{errors.phone && <small className="field-error">{errors.phone}</small>}</label><label className="wide-field">Địa chỉ<textarea value={form.address} onChange={setField('address')} placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" />{errors.address && <small className="field-error">{errors.address}</small>}</label></div> : <div className="notice">Staff tại đúng chi nhánh sẽ chuẩn bị đơn; bạn xuất trình thông tin đơn khi nhận.</div>}<h2>Ưu đãi</h2><div className="benefit-box"><label>Mã voucher<input value={form.voucher} onChange={setField('voucher')} placeholder="Nhập mã voucher" /></label><label>Điểm muốn dùng<input value={form.points} onChange={setField('points')} inputMode="numeric" placeholder="0" /></label><small>Voucher/điểm chỉ được chấp nhận sau khi FE-06 trả báo giá đã xác minh.</small></div><h2>Thanh toán</h2><label className="choice"><input type="radio" defaultChecked /> VNPay</label><label className="choice"><input type="radio" /> Thanh toán khi nhận tại cửa hàng</label>{notice && <div className="notice">{notice}</div>}</section><aside className="checkout-summary"><h2>Tóm tắt đơn</h2><p>Quận 1 — Nhẫn bạc <span>1.250.000 ₫</span></p><p>Ship (1 nhẫn × 50g) <span>35.000 ₫</span></p><p>Thủ Đức — Nhẫn vàng <span>2.800.000 ₫</span></p><p>Ship (1 nhẫn × 50g) <span>42.000 ₫</span></p><hr /><p className="total"><strong>Tổng cộng</strong><strong>4.127.000 ₫</strong></p><button type="button" className="button primary" onClick={validate}>Kiểm tra thông tin checkout</button><small>Phí ship, voucher và điểm chỉ là khung hiển thị cho đến khi API GHTK/FE-06 sẵn sàng.</small></aside></div></Workspace>;
}

function RetailOrdersExperience() { const [filter, setFilter] = useState('all'); const orders = [{ id: 'ML-240012', date: '02/11/2026', status: 'SHIPPING', label: 'Đang giao', total: '4.127.000 ₫' }, { id: 'ML-240009', date: '05/10/2026', status: 'COMPLETED', label: 'Hoàn thành', total: '1.250.000 ₫' }, { id: 'ML-240006', date: '30/09/2026', status: 'CANCELLED', label: 'Đã hủy', total: '2.800.000 ₫' }]; const shown = filter === 'all' ? orders : orders.filter(order => order.status === filter); return <Workspace screen={screenIndex['my-orders']}><section className="workspace-card"><div className="card-head"><div><h2>Đơn mua của tôi</h2><p className="muted">Timeline và trạng thái sẽ nhận từ retail order API/GHTK khi được tích hợp.</p></div><select value={filter} onChange={event => setFilter(event.target.value)}><option value="all">Tất cả</option><option value="SHIPPING">Đang giao</option><option value="COMPLETED">Hoàn thành</option><option value="CANCELLED">Đã hủy</option></select></div><div className="order-list">{shown.map(order => <a href={toHref('retail-order-details')} className="order-row" key={order.id}><strong>{order.id}</strong><span>{order.date}</span><span className={order.status === 'SHIPPING' ? 'status ok' : 'status'}>{order.label}</span><strong>{order.total}</strong></a>)}</div></section></Workspace>; }

function RetailStaffOperations() { const [stage, setStage] = useState(0); const actions = [['Chuẩn bị hàng', 'Xác nhận sản phẩm đã sẵn sàng tại đúng chi nhánh.'], ['Bàn giao vận chuyển', 'Ghi nhận bàn giao vật lý sau khi shipment đã được tạo.'], ['Hoàn tất ghi chú', 'Lưu ghi chú vận hành nội bộ.']]; return <Workspace screen={screenIndex['retail-order-operations']}><section className="workspace-card"><div className="card-head"><div><h2>Đơn #ML-240012</h2><p className="muted">MLAND Quận 1 • giao tận nơi • đã thanh toán</p></div><span className="status ok">Đang xử lý</span></div><div className="operation-grid">{actions.map(([title, description], index) => <button className={`operation-action ${index < stage ? 'done' : ''}`} key={title} type="button" disabled={index > stage} onClick={() => setStage(Math.min(actions.length, stage + 1))}><strong>{index + 1}. {title}</strong><span>{index < stage ? 'Đã ghi nhận trong UI' : description}</span></button>)}</div><p className="prototype-note">Các bước chỉ mô phỏng giao diện. API thật sẽ kiểm tra Staff được phân công, trạng thái đã thanh toán và shipment trước khi cho bàn giao.</p></section></Workspace>; }

function RoleScreen({ screen }) {
  const needsForm = /Form|Configuration|Assign|Edit|New |Pricing|Parameters|Options|Capacity|Holidays/.test(screen.title);
  const needsDetail = /Details|Review|Operations|Confirmation|Analysis|Result|Status|Lookup|Support|Conversation/.test(screen.title);
  if (needsForm) return <Workspace screen={screen}><section className="workspace-card"><div className="card-head"><div><h2>{screen.title}</h2><p className="muted">Biểu mẫu khung — chưa gửi dữ liệu lên máy chủ.</p></div><span className="status">Bản nháp</span></div><div className="form-grid"><label>Tên hiển thị<input placeholder="Nhập tên hoặc mã tham chiếu" /></label><label>Trạng thái<select><option>Đang hoạt động</option><option>Tạm dừng</option><option>Bản nháp</option></select></label><label>Chi nhánh áp dụng<select><option>MLAND Quận 1</option><option>MLAND Thủ Đức</option></select></label><label>Ngày hiệu lực<input type="date" /></label><label className="wide-field">Ghi chú<textarea placeholder="Nhập ghi chú hoặc mô tả…" /></label></div><div className="actions"><button className="button primary" type="button">Lưu bản nháp</button><button className="button outline" type="button">Hủy</button></div></section><p className="prototype-note">Các trường sẽ được thay bằng đúng DTO/validation sau khi API của feature này được chốt.</p></Workspace>;
  if (needsDetail) return <Workspace screen={screen}><section className="workspace-card"><div className="card-head"><div><h2>{screen.title}</h2><p className="muted">ML-240012 • Cập nhật 5 phút trước</p></div><span className="status ok">Đang hoạt động</span></div><div className="detail-panels"><article><h3>Thông tin chính</h3><p><span>Mã</span><strong>ML-240012</strong></p><p><span>Chi nhánh</span><strong>MLAND Quận 1</strong></p><p><span>Người phụ trách</span><strong>Đang phân công</strong></p></article><article><h3>Tiến trình</h3><ol><li>Đã tạo</li><li>Đang xử lý</li><li>Chờ xác nhận</li></ol></article></div><div className="actions"><button className="button primary" type="button">Cập nhật</button><button className="button outline" type="button">Xem lịch sử</button></div></section><p className="prototype-note">Khung detail có trạng thái, thông tin và action; dữ liệu sẽ nối API theo role sau.</p></Workspace>;
  return <Workspace screen={screen} />;
}

function App() {
  const { route, params } = useRoute(); const screen = screenIndex[route] || screenIndex.home;
  let content;
  if (route === 'home') content = <HomePage />;
  else if (route === 'catalogue') content = <LiveCatalogue />;
  else if (route === 'product-details') content = <RetailProductDetails />;
  else if (route === 'workshops') content = <WorkshopListPage />;
  else if (route === 'workshop-details') content = <WorkshopDetailPage packageId={params.packageId} />;
  else if (route === 'booking-setup') content = <WorkshopBookingPage packageId={params.packageId || 1} />;
  else if (route === 'workshop-payment-return') content = <WorkshopPaymentReturnPage bookingCode={params.bookingCode} />;
  else if (route === 'booking-lookup') content = <WorkshopLookupPage />;
  else if (route === 'booking-access') content = <WorkshopAccessPage />;
  else if (['login', 'register', 'forgot-password'].includes(route)) content = <LoginLike mode={route} />;
  else if (['signature-setup', 'wax-setup', 'booking-summary'].includes(route)) content = <BookingFlow title={screen.title} />;
  else if (route === 'cart') content = <RetailCartWorkspace />;
  else if (route === 'checkout') content = <RetailCheckoutWorkspace />;
  else if (route === 'my-orders') content = <RetailOrdersExperience />;
  else if (route === 'retail-order-details') content = <OrdersPage details />;
  else if (route === 'retail-order-operations') content = <RetailStaffOperations />;
  else if (route === 'manager-dashboard') content = <ManagerDashboard />;
  else if (route.endsWith('dashboard')) content = <Dashboard screen={screen} />;
  else content = <RoleScreen screen={screen} />;
  return <><Header />{content}<footer><div className="container footer-inner"><strong>MLAND</strong><span>© MLAND</span></div></footer></>;
}

export default App;
