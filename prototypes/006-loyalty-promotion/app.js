/**
 * PPSWBS Prototype 006 — Loyalty & Promotion (FE-06): console UI.
 * Style: Operations Hub console (prototypes/style-3-dense-console), same shell as prototype 002. Rules live in
 * domain.js (mock, localStorage). Roles can be switched in the top bar to try permissions; this is not authentication.
 */
(function () {
  'use strict';

  const UI_KEY = 'ppswbs_prototype_006_ui_v1';
  const ACTORS = { GUEST: null, MEMBER: 'member.mai', STAFF: 'staff.lan', MANAGER: 'manager.huy', ADMIN: 'admin.an' };

  const defaultCheckout = () => ({ lines: [{ variantId: 101, quantity: 1 }, { variantId: 103, quantity: 1 }], shipping: 30000, mode: 'auto', code: '', points: '' });
  const defaultWorkshop = () => ({ sessionId: 11, participants: 1, code: '', points: '' });

  let ui = {
    lang: 'vi', role: 'MEMBER', view: 'offers', programId: null, offerId: null,
    list: { scope: '', status: '', page: 0 }, offersScope: '', historyPage: 0,
    checkout: defaultCheckout(), workshop: defaultWorkshop(), createDraft: null,
    error: null, modal: null, sim: { latency: false, fault: 'none' }, simOpen: false
  };
  try {
    const saved = JSON.parse(localStorage.getItem(UI_KEY) || 'null');
    if (saved) ui = { ...ui, ...saved, modal: null, error: null };
  } catch (e) { /* default UI */ }

  LP.load();

  // ---------------------------------------------------------------- helpers

  const L = (vi, en) => (ui.lang === 'vi' ? vi : en);
  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const money = (v) => (v == null ? '—' : new Intl.NumberFormat('vi-VN').format(v) + ' ₫');
  const num = (v) => new Intl.NumberFormat('vi-VN').format(v);
  const date = (iso) => (iso ? new Date(iso).toLocaleString(ui.lang === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'short', timeStyle: 'short' }) : '—');
  const actor = () => ACTORS[ui.role];
  const toLocalInput = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };

  const ERRORS = {
    INVALID_INPUT: ['Dữ liệu chưa hợp lệ.', 'The request contains invalid values.'],
    AUTHENTICATION_REQUIRED: ['Cần đăng nhập.', 'Sign-in is required.'],
    ACCESS_DENIED: ['Bạn không có quyền thực hiện thao tác này.', 'You do not have access to this action.'],
    PROMOTION_NOT_FOUND: ['Không tìm thấy chương trình ưu đãi.', 'The promotion was not found.'],
    CODE_EXISTS: ['Mã đã được cấp (kể cả chương trình đã hết hạn hoặc tạm dừng). So sánh không phân biệt hoa/thường.', 'The code already exists.'],
    STALE_VERSION: ['Dữ liệu đã thay đổi. Hãy tải lại rồi thử lại.', 'The data has changed. Reload and try again.'],
    IDEMPOTENCY_CONFLICT: ['Khóa lệnh đã dùng cho một yêu cầu khác.', 'The command key was used for another request.'],
    QUOTA_LIMIT_CONFLICT: ['Hạn mức mới thấp hơn số lượt đã dùng cộng đang giữ hiện tại.', 'The new limit is below used plus held.'],
    BENEFIT_STATE_CONFLICT: ['Trạng thái hiện tại không cho phép thao tác này.', 'The current state does not allow this.'],
    QUOTE_CHANGED: ['Ưu đãi hoặc tổng tiền đã thay đổi trước khi xác nhận. Chưa giữ gì; hãy xem tổng mới.', 'The quote changed before confirmation. Nothing was held.'],
    POINTS_LIMIT_CHANGED: ['Số điểm được dùng đã thay đổi (điểm khả dụng hoặc trần mới). Chưa giữ gì; hãy chọn lại số điểm.', 'The points limit changed. Nothing was held; choose again.'],
    PROMOTION_INELIGIBLE: ['Không dùng được ưu đãi này.', 'This promotion cannot be used.'],
    POINTS_USE_NOT_ALLOWED: ['Không dùng được điểm cho giao dịch này.', 'Points cannot be used here.'],
    WORKSHOP_NET_AMOUNT_INVALID: ['Phí workshop sau giảm phải lớn hơn 0đ. Hãy bớt ưu đãi hoặc điểm.', 'The workshop fee after benefits must be above 0.'],
    DEPENDENCY_UNAVAILABLE: ['Dịch vụ liên quan (FE-02 sản phẩm / nguồn giao dịch) tạm thời không phản hồi. Chưa có gì thay đổi, hãy thử lại.', 'A dependency is unavailable. Nothing changed; try again.'],
    UNAUTHENTICATED: ['Phiên đăng nhập đã hết hạn. Thao tác chưa được lưu; dữ liệu bạn nhập vẫn được giữ.', 'Your session has expired. Nothing was saved; your input is kept.']
  };
  const ISSUES = {
    NOT_FOUND: ['Mã không tồn tại.', 'The code does not exist.'],
    WRONG_SCOPE: ['Mã này không dùng cho loại giao dịch này (mã sản phẩm / mã workshop).', 'This code is for the other service.'],
    PROMOTION_INELIGIBLE: ['Đây không phải voucher.', 'Not a voucher.'],
    SCHEDULED: ['Chương trình chưa bắt đầu.', 'Not started yet.'],
    EXPIRED: ['Chương trình đã hết hạn.', 'The programme has ended.'],
    PAUSED: ['Chương trình đang tạm dừng.', 'The programme is paused.'],
    SOLD_OUT: ['Chương trình đã hết lượt.', 'No uses left.'],
    MEMBER_LIMIT: ['Bạn đã dùng hết lượt của mình cho mã này.', 'You have used all your uses.'],
    NO_ELIGIBLE_LINE: ['Giỏ không có sản phẩm nào đang bán thuộc phạm vi mã.', 'No purchasable line is in scope.'],
    BELOW_THRESHOLD: ['Phần đủ điều kiện chưa đạt giá trị tối thiểu.', 'Below the minimum eligible amount.'],
    WRONG_PACKAGE: ['Mã không áp dụng cho gói này.', 'Not for this package.'],
    WRONG_BRANCH: ['Mã không áp dụng cho chi nhánh này.', 'Not for this branch.'],
    WRONG_DATE: ['Mã không áp dụng cho ngày này.', 'Not for this date.'],
    NOT_ALLOWED: ['Ưu đãi đang chọn không cho kết hợp điểm.', 'The selected promotion does not combine with points.'],
    BELOW_MINIMUM: ['Số điểm tối đa dùng được thấp hơn mức tối thiểu mỗi lần.', 'The maximum is below the minimum per use.']
  };
  const FIELDS = {
    name: ['Tên 1–200 ký tự', 'Name 1–200 characters'],
    code: ['Mã 3–40 ký tự A–Z, 0–9, "-" hoặc "_"', 'Code 3–40 of A–Z, 0–9, - or _'],
    startAt: ['Chọn thời điểm bắt đầu', 'Choose a start'],
    endAt: ['Kết thúc phải sau bắt đầu', 'End must be after start'],
    'discount.percentage': ['Tỷ lệ lớn hơn 0 và không quá 100%', 'Percentage above 0 up to 100'],
    'discount.maxDiscountAmount': ['Trần giảm là số nguyên VND dương', 'Cap is a positive whole VND'],
    'discount.amount': ['Mức giảm là số nguyên VND dương', 'Amount is a positive whole VND'],
    minimumEligibleAmount: ['Giá trị tối thiểu là số nguyên VND không âm', 'Minimum is a nonnegative whole VND'],
    totalLimit: ['Tổng lượt là số nguyên dương', 'Total uses is a positive whole number'],
    perMemberLimit: ['Lượt mỗi Member là số nguyên dương', 'Uses per Member is a positive whole number'],
    targets: ['Chọn ít nhất một sản phẩm hoặc size', 'Choose at least one product or size'],
    'targets.productIds': ['Sản phẩm không tồn tại hoặc đã xóa', 'Product missing or deleted'],
    'targets.variantIds': ['Size phải tồn tại, chưa xóa và thuộc một sản phẩm đã chọn', 'Size must exist and belong to a chosen product'],
    'targets.packageIds': ['Chọn ít nhất một gói workshop', 'Choose at least one package'],
    'targets.workshopDates': ['Ngày theo dạng YYYY-MM-DD', 'Dates as YYYY-MM-DD'],
    'scope': ['Giảm tự động chỉ dùng cho sản phẩm', 'Automatic discounts are product-only'],
    earnUnitAmount: ['Số tiền cho 1 điểm tích là số nguyên dương', 'Positive whole VND'],
    redeemPointValue: ['Giá trị 1 điểm là số nguyên dương', 'Positive whole VND'],
    minRedeemPoints: ['Điểm tối thiểu là số nguyên dương', 'Positive whole number'],
    maxRedeemPercent: ['Trần lớn hơn 0 và không quá 100%', 'Above 0 up to 100'],
    participants: ['Số người 1–10', '1–10 people']
  };
  function fieldText(f) {
    if (f.field === 'points') return L('Số điểm phải là số nguyên trong khoảng ' + f.message.replace('range ', ''), 'Points within ' + f.message);
    if (f.field === 'code' && ISSUES[f.message]) return ISSUES[f.message][ui.lang === 'vi' ? 0 : 1];
    if (f.field === 'code' && f.message === 'exists') return L('Mã này đã tồn tại, không cấp lại cho chương trình khác', 'This code already exists and is never reused');
    if (f.message && f.message.startsWith('min ')) return L('Không được thấp hơn ' + f.message.slice(4) + ' (đã dùng + đang giữ)', 'Not below ' + f.message.slice(4));
    const hint = FIELDS[f.field];
    return hint ? hint[ui.lang === 'vi' ? 0 : 1] : f.field + ': ' + f.message;
  }

  function saveUi() {
    try {
      const { modal, error, ...rest } = ui;
      localStorage.setItem(UI_KEY, JSON.stringify(rest));
    } catch (e) { /* ignore */ }
  }

  function toast(text, isError) {
    setTimeout(() => {
      const container = document.getElementById('toastContainer');
      if (!container) return;
      const el = document.createElement('div');
      el.className = 'toast' + (isError ? ' error' : '');
      el.textContent = text;
      container.appendChild(el);
      setTimeout(() => el.remove(), 3800);
    }, 0);
  }

  // ---------------------------------------------------------------- simulated network (FR-013, FR-039)

  let busy = false;
  let busyNoticed = false;
  let loadingView = false;
  let lastRetry = null;
  let lostCommand = null;
  let inputMemory = {};
  let shownDigest = null;
  const LATENCY_MS = 1200;
  const RETRYABLE = ['DEPENDENCY_UNAVAILABLE'];
  const FAULTS = [
    ['none', 'Bình thường', 'Normal', ''],
    ['unauth', '401 hết phiên', '401 expired', 'Thao tác ghi tiếp theo trả 401: hộp thoại đăng nhập lại, dữ liệu đang nhập được giữ.'],
    ['dependency', '503 phụ thuộc', '503 dependency', 'Lần tạo chương trình (cần FE-02 kiểm tra sản phẩm) hoặc lần mở ưu đãi công khai/Kho Voucher tiếp theo trả 503 DEPENDENCY_UNAVAILABLE.'],
    ['lost', 'Mất phản hồi', 'Lost response', 'Thao tác ghi tiếp theo ĐÃ được lưu nhưng màn hình không nhận được phản hồi: thử "Kiểm tra kết quả" hoặc "Gửi lại cùng khóa".'],
    ['conflict', '409 Manager khác sửa', '409 another Manager', 'Ngay trước lần sửa/tạm dừng/mở lại chương trình hoặc lưu chính sách điểm tiếp theo, manager.linh sửa cùng bản ghi → 409 STALE_VERSION.'],
    ['quote', 'Đổi trước xác nhận', 'Changed before confirm', 'Ngay trước lần xác nhận đơn/booking tiếp theo: nếu có dùng điểm thì điểm bị một đơn khác giữ (POINTS_LIMIT_CHANGED); nếu không, chính sách điểm có bản mới (QUOTE_CHANGED).']
  ];
  const newKey = () => (crypto.randomUUID ? crypto.randomUUID() : 'k' + Date.now().toString(16) + Math.random().toString(16).slice(2));
  const newCorrelationId = () => Array.from({ length: 4 }, () => Math.random().toString(16).slice(2, 6).padEnd(4, '0')).join('-');
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ui.sim.latency ? (ms || LATENCY_MS) : 0));
  const message = (code) => (ERRORS[code] || [code, code])[ui.lang === 'vi' ? 0 : 1];

  function takeFault(applies) {
    const f = ui.sim.fault;
    if (f === 'none' || !applies.includes(f)) return null;
    ui.sim.fault = 'none';
    saveUi();
    return f;
  }
  function setBusy(value) {
    busy = value;
    busyNoticed = false;
    document.body.classList.toggle('is-busy', value);
  }
  function ignoredWhileBusy() {
    if (!busy) return false;
    if (!busyNoticed) { busyNoticed = true; toast(L('Đang xử lý — bỏ qua lần bấm lặp.', 'Working — repeated click ignored.')); }
    return true;
  }
  function showLoading() {
    if (!ui.sim.latency) return;
    loadingView = true;
    setTimeout(() => { loadingView = false; render({ keepInputs: true }); }, LATENCY_MS * 0.7);
  }

  /**
   * Runs a command like the real client: one Idempotency-Key per intent, a resend with the same key replays the
   * committed result, a refused command keeps what the user typed. Options: onOk(result), key, deps (['dependency']),
   * conflict { simulate } for versioned commands, quote (simulate) for checkout/booking confirmation.
   */
  function run(action, success, opts) {
    const o = opts || {};
    if (busy) return Promise.resolve({ ok: false, ignored: true });
    const key = o.key || newKey();
    const fault = takeFault(['unauth', 'lost'].concat(o.deps || [], o.conflict ? ['conflict'] : [], o.quote ? ['quote'] : []));
    setBusy(true);
    return wait().then(() => {
      setBusy(false);
      try {
        if (fault === 'unauth') throw new LP.DomainError('UNAUTHENTICATED');
        if (fault === 'dependency') throw new LP.DomainError('DEPENDENCY_UNAVAILABLE');
        if (fault === 'conflict') { o.conflict.simulate(); LP.save(); }
        if (fault === 'quote') { o.quote(); LP.save(); }
        const stored = LP.receipt(key);
        const result = stored ? stored.result : action();
        if (!stored) LP.recordReceipt(key, result);
        LP.save();
        if (fault === 'lost') {
          lostCommand = { action, success, o, key };
          ui.modal = { type: 'lost', key, back: ui.modal };
          render({ keepInputs: true });
          return { ok: false, lost: true };
        }
        ui.error = null;
        lastRetry = null;
        inputMemory = {};
        if (ui.modal && ui.modal.type === 'lost') ui.modal = null;
        const text = typeof success === 'function' ? success(result) : success;
        if (text || stored) toast((text || '') + (stored ? ' ' + L('(lệnh đã được ghi trước đó — không tạo bản trùng)', '(already committed — no duplicate)') : ''));
        render();
        if (o.onOk) o.onOk(result);
        return { ok: true, result };
      } catch (e) {
        if (!(e instanceof LP.DomainError)) throw e;
        LP.save();
        ui.error = { code: e.code, fieldErrors: e.fieldErrors, correlationId: newCorrelationId(), retry: RETRYABLE.includes(e.code) };
        lastRetry = RETRYABLE.includes(e.code) ? () => run(action, success, { ...o, key }) : null;
        if (ui.modal && ui.modal.type === 'lost') ui.modal = ui.modal.back || null;
        if (e.code === 'UNAUTHENTICATED') ui.modal = { type: 'session', back: ui.modal };
        if (e.code === 'STALE_VERSION' && o.conflict) ui.modal = { type: 'conflict', back: ui.modal };
        if (e.code === 'QUOTE_CHANGED') ui.modal = { type: 'quote', quote: e.detail.quote, retry: o.onQuote };
        toast(message(e.code), true);
        render({ keepInputs: true });
        return { ok: false, error: e };
      }
    });
  }

  function errorBox() {
    if (!ui.error) return '';
    return `<div class="alert error"><span class="code">${esc(ui.error.code)}</span> — ${esc(message(ui.error.code))}
      ${ui.error.fieldErrors.length ? `<ul>${ui.error.fieldErrors.map((f) => `<li>${esc(fieldText(f))}</li>`).join('')}</ul>` : ''}
      <div class="error-meta"><span>correlationId <span class="code-mono">${esc(ui.error.correlationId)}</span></span>
        ${ui.error.retry ? `<button class="btn-secondary" data-action="retry">↻ ${L('Thử lại', 'Retry')}</button>` : ''}</div></div>`;
  }

  function unavailablePanel() {
    return `<div class="panel"><div class="empty-state"><div class="icon">📡</div><strong>503 — DEPENDENCY_UNAVAILABLE</strong>
      <div>${esc(message('DEPENDENCY_UNAVAILABLE'))}</div>
      <div class="muted">${L('Không hiển thị dữ liệu cũ như thể là hiện tại.', 'Old data is not shown as current.')} · correlationId <span class="code-mono">${newCorrelationId()}</span></div>
      <button class="btn-secondary" data-action="retry-view">↻ ${L('Thử lại', 'Retry')}</button></div></div>`;
  }

  // Inputs are remembered by screen + form + field so a refused command re-renders with what the user typed.
  function inputKey(el) {
    const name = el.name || el.id;
    if (!name) return null;
    const holder = el.closest('[data-modal]');
    const form = el.closest('[data-form]');
    const scope = holder ? 'modal-' + holder.getAttribute('data-modal') : form ? form.getAttribute('data-form') : 'page';
    return [ui.view, ui.programId, scope, name, el.type === 'checkbox' ? el.value : ''].join(':');
  }
  const editableInputs = () => Array.from(document.querySelectorAll('#app input, #app textarea, #app select'));
  function captureInputs() {
    editableInputs().forEach((el) => { const k = inputKey(el); if (k) inputMemory[k] = el.type === 'checkbox' || el.type === 'radio' ? el.checked : el.value; });
  }
  function restoreInputs() {
    editableInputs().forEach((el) => {
      const k = inputKey(el);
      if (!k || !(k in inputMemory)) return;
      if (el.type === 'checkbox' || el.type === 'radio') el.checked = inputMemory[k]; else el.value = inputMemory[k];
    });
  }

  function go(view, extra) {
    ui = { ...ui, view, error: null, modal: null, editDiscountKind: null, ...(extra || {}) };
    inputMemory = {};
    saveUi();
    showLoading();
    render();
    window.scrollTo(0, 0);
  }

  // ---------------------------------------------------------------- text and badges

  function discountText(p) {
    const d = p.discount;
    return d.kind === 'PERCENT'
      ? L('Giảm ' + d.percentage + '%, tối đa ' + money(d.maxDiscountAmount), d.percentage + '% off, up to ' + money(d.maxDiscountAmount))
      : L('Giảm ' + money(d.amount), money(d.amount) + ' off');
  }
  const scopeBadge = (p) => `<span class="badge ${p.scope === 'PRODUCT' ? 'info' : 'purple'}">${p.scope === 'PRODUCT' ? L('Mua sản phẩm', 'Products') : L('Đặt workshop', 'Workshop')}</span>`;
  const kindBadge = (p) => (p.kind === 'VOUCHER' ? `<span class="badge">${L('Voucher', 'Voucher')}</span>` : `<span class="badge success">${L('Giảm tự động', 'Automatic')}</span>`);
  function availabilityBadge(a) {
    const map = { ACTIVE: ['Đang hiệu lực', 'Active', 'success'], SCHEDULED: ['Chưa bắt đầu', 'Scheduled', 'info'], EXPIRED: ['Hết hạn', 'Expired', 'error'],
      PAUSED: ['Tạm dừng', 'Paused', 'warning'], NO_PUBLIC_TARGET: ['Hiện không có sản phẩm áp dụng', 'No product available now', 'warning'] };
    const [vi, en, cls] = map[a];
    return `<span class="badge ${cls}">${L(vi, en)}</span>`;
  }
  function benefitBadge(b) {
    const map = { HELD: ['Đang giữ', 'Held', 'warning'], SETTLED: ['Đã chốt', 'Settled', 'success'], RELEASED: ['Đã giải phóng', 'Released', ''], RESTORED: ['Đã hoàn', 'Restored', 'info'] };
    const [vi, en, cls] = map[b.state];
    return `<span class="badge ${cls}">${L(vi, en)}</span>`;
  }
  function ledgerBadge(kind) {
    const map = { EARN: ['Cộng điểm', 'Earn', 'success'], HOLD: ['Giữ', 'Hold', 'warning'], CONSUME: ['Dùng', 'Use', 'info'], RELEASE: ['Giải phóng', 'Release', ''], RESTORE: ['Hoàn', 'Restore', 'purple'] };
    const [vi, en, cls] = map[kind];
    return `<span class="badge ${cls}">${L(vi, en)}</span>`;
  }
  const signed = (n) => (n > 0 ? '+' + num(n) : n < 0 ? '−' + num(-n) : '0');
  function targetsText(p, publicOnly) {
    const t = p.targets;
    if (p.scope === 'PRODUCT') {
      if (publicOnly) return LP.publicTargets(p).map((x) => esc(x.name)).join(', ');
      const parts = (t.productIds || []).map((id) => esc(LP.product(id).name));
      (t.variantIds || []).forEach((id) => parts.push('<span class="code-mono">' + esc(LP.variant(id).sku) + '</span>'));
      return parts.join(', ');
    }
    const parts = [L('Gói: ', 'Packages: ') + t.packageIds.map((id) => esc(LP.pkg(id).name)).join(', ')];
    parts.push(L('Chi nhánh: ', 'Branches: ') + (t.branchIds ? t.branchIds.map((id) => esc(LP.branch(id).name)).join(', ') : L('mọi chi nhánh', 'all')));
    if (t.workshopDates) parts.push(L('Ngày tham gia: ', 'Workshop dates: ') + t.workshopDates.join(', '));
    return parts.join(' · ');
  }

  // ---------------------------------------------------------------- shell

  function render(opts) {
    const keepInputs = opts && opts.keepInputs;
    if (keepInputs) captureInputs();
    renderShell();
    if (keepInputs) restoreInputs();
  }

  function simBar() {
    const hint = FAULTS.find(([id]) => id === ui.sim.fault);
    return `<div class="sim-bar">
      <label class="sim-toggle"><input type="checkbox" data-sim-latency ${ui.sim.latency ? 'checked' : ''}> ${L('Mạng chậm (≈1,2 giây mỗi thao tác)', 'Slow network (≈1.2 s per action)')}</label>
      <span class="topbar-label">${L('Lần thao tác tiếp theo:', 'Next action:')}</span>
      <div class="control-pill-group">${FAULTS.map(([id, vi, en]) => `<button class="control-pill-btn ${ui.sim.fault === id ? 'active' : ''}" data-sim-fault="${id}">${L(vi, en)}</button>`).join('')}</div>
      <span class="muted sim-hint">${hint && hint[3] ? esc(hint[3]) : L('Không có sự cố nào được cài. Sự cố chỉ xảy ra một lần rồi tự về Bình thường.', 'No fault armed. A fault fires once, then resets.')}</span>
    </div>`;
  }

  function renderShell() {
    const root = document.getElementById('app');
    const held = ui.role === 'MEMBER' ? LP.state.benefits.filter((b) => b.member === LP.state.members[actor()].id && b.state === 'HELD').length : 0;
    const active = (views) => (views.includes(ui.view) ? 'active' : '');
    const menu = (view, icon, label, extraViews, count) => `<li><button class="sidebar-btn ${active([view].concat(extraViews || []))}" data-go="${view}">
      <span>${icon}</span><span>${label}</span>${count ? `<span class="count">${count}</span>` : ''}</button></li>`;
    const roles = [['GUEST', 'Khách', 'Guest'], ['MEMBER', 'Thành viên', 'Member'], ['STAFF', 'Staff', 'Staff'], ['MANAGER', 'Manager', 'Manager'], ['ADMIN', 'Admin', 'Admin']];
    root.innerHTML = `
      <aside class="console-sidebar">
        <div class="sidebar-header">
          <a class="sidebar-brand" href="../index.html"><span>MLAND</span><span class="pill">FE-06</span></a>
          <a class="hub-link" href="../index.html">← Hub</a>
        </div>
        <ul class="sidebar-menu">
          <li class="sidebar-item-header">${L('Công khai', 'Public')}</li>
          ${menu('offers', '🏷️', L('Ưu đãi', 'Offers'), ['offer'])}
          <li class="sidebar-item-header" style="margin-top: 14px;">${L('Thành viên', 'Member')}</li>
          ${menu('wallet', '🎟️', L('Kho Voucher', 'Voucher wallet'))}
          ${menu('loyalty', '⭐', 'My Loyalty')}
          ${menu('checkout', '🛒', L('Thanh toán sản phẩm (demo)', 'Retail checkout (demo)'))}
          ${menu('workshop', '💍', L('Đặt workshop (demo)', 'Workshop booking (demo)'))}
          ${menu('transactions', '🧾', L('Giao dịch của tôi', 'My transactions'), [], held)}
          <li class="sidebar-item-header" style="margin-top: 14px;">${L('Quản lí (Manager)', 'Management (Manager)')}</li>
          ${menu('programs', '📋', L('Chương trình ưu đãi', 'Programmes'), ['program', 'create'])}
          ${menu('policy', '⚙️', L('Chính sách điểm', 'Points policy'))}
        </ul>
        <div class="sidebar-footer">
          <div>${L('Vai trò', 'Role')}: <strong>${esc(ui.role)}</strong>${actor() ? ' · ' + esc(actor()) : ''}</div>
          <button data-action="reset">${L('Khôi phục dữ liệu mẫu', 'Reset sample data')}</button>
        </div>
      </aside>
      <div class="console-main">
        <header class="console-topbar">
          <div class="topbar-left">
            <span class="topbar-label">${L('Đăng nhập giả lập:', 'Simulated role:')}</span>
            <div class="control-pill-group">${roles.map(([r, vi, en]) => `<button class="control-pill-btn ${ui.role === r ? 'active' : ''}" data-role="${r}">${L(vi, en)}</button>`).join('')}</div>
            <span class="prototype-note">${L('Prototype — dữ liệu giả, không gọi backend', 'Prototype — mock data, no backend')}</span>
          </div>
          <div class="topbar-right">
            <span class="busy-indicator"><span class="spinner"></span>${L('Đang xử lý…', 'Working…')}</span>
            <button class="btn-secondary sim-btn ${ui.simOpen ? 'active' : ''}" data-action="toggle-sim">🧪 ${L('Giả lập sự cố', 'Simulate faults')}${ui.sim.fault !== 'none' || ui.sim.latency ? ' <span class="sim-dot"></span>' : ''}</button>
            <div class="control-pill-group">
              <button class="control-pill-btn ${ui.lang === 'vi' ? 'active' : ''}" data-lang="vi">VI</button>
              <button class="control-pill-btn ${ui.lang === 'en' ? 'active' : ''}" data-lang="en">EN</button>
            </div>
          </div>
        </header>
        ${ui.simOpen ? simBar() : ''}
        <main class="console-content" id="content">${content()}</main>
      </div>
      ${ui.modal ? modal() : ''}
      <div class="toast-container" id="toastContainer"></div>`;
  }

  const MEMBER_VIEWS = ['wallet', 'loyalty', 'checkout', 'workshop', 'transactions'];
  const MANAGER_VIEWS = ['programs', 'program', 'create', 'policy'];

  function content() {
    if (MEMBER_VIEWS.includes(ui.view) && ui.role !== 'MEMBER') return denied('member');
    if (MANAGER_VIEWS.includes(ui.view) && ui.role !== 'MANAGER') return denied('manager');
    if (loadingView) return skeleton();
    if (ui.unavailable === ui.view) return unavailablePanel();
    try {
      return view();
    } catch (e) {
      if (!(e instanceof LP.DomainError)) throw e;
      return `<div class="panel"><div class="empty-state"><div class="icon">🔍</div><strong>${esc(e.code)}</strong><div>${esc(message(e.code))}</div>
        <button class="btn-secondary" data-go="${MANAGER_VIEWS.includes(ui.view) ? 'programs' : 'offers'}">${L('Quay lại', 'Back')}</button></div></div>`;
    }
  }

  function view() {
    switch (ui.view) {
      case 'offers': return offersView();
      case 'offer': return offerDetail();
      case 'wallet': return walletView();
      case 'loyalty': return loyaltyView();
      case 'checkout': return checkoutView();
      case 'workshop': return workshopView();
      case 'transactions': return transactionsView();
      case 'programs': return programsView();
      case 'program': return programDetail();
      case 'create': return createView();
      case 'policy': return policyView();
      default: return offersView();
    }
  }

  function skeleton() {
    return `<div class="skeleton" aria-busy="true" aria-label="${L('Đang tải', 'Loading')}">
      <div class="skeleton-block" style="height: 22px; width: 32%;"></div><div class="skeleton-block" style="height: 12px; width: 58%;"></div>
      <div class="skeleton-grid">${'<div class="skeleton-block" style="height: 130px;"></div>'.repeat(6)}</div></div>`;
  }

  function denied(kind) {
    const guest = ui.role === 'GUEST';
    const target = kind === 'member' ? 'MEMBER' : 'MANAGER';
    const why = kind === 'member'
      ? L('Kho Voucher, My Loyalty và quyền lợi khi mua/đặt chỉ dành cho Member đã đăng nhập. Biết mã hoặc đăng nhập bằng vai trò khác không cấp quyền này.', 'Only a signed-in Member uses their own benefits.')
      : L('Chỉ Manager quản lí chương trình và chính sách điểm. Admin chỉ quản lí tài khoản/cấp quyền; Staff không có quyền FE-06.', 'Only a Manager manages programmes and the points policy.');
    return `<div class="panel"><div class="empty-state"><div class="icon">${guest ? '🔐' : '⛔'}</div>
      <strong>${guest ? '401 — AUTHENTICATION_REQUIRED' : '403 — ACCESS_DENIED'}</strong>
      <div>${why}</div>
      <button class="btn-secondary" data-role="${target}">${L('Chuyển sang ', 'Switch to ') + target}</button></div></div>`;
  }

  function pagination(result, scope) {
    if (result.pages <= 1) return `<div class="pagination muted">${result.total} ${L('kết quả', 'results')}</div>`;
    return `<div class="pagination"><span class="muted">${result.total} ${L('kết quả', 'results')} · ${L('trang', 'page')} ${result.page + 1}/${result.pages}</span>
      <button class="btn-secondary" data-page="${scope}:${result.page - 1}" ${result.page === 0 ? 'disabled' : ''}>‹</button>
      <button class="btn-secondary" data-page="${scope}:${result.page + 1}" ${result.page + 1 >= result.pages ? 'disabled' : ''}>›</button></div>`;
  }

  // ---------------------------------------------------------------- public offers (US3)

  function offerCard(p) {
    return `<div class="offer-card">
      <div class="offer-head">${scopeBadge(p)} ${kindBadge(p)}</div>
      <div class="offer-title">${esc(p.name)}</div>
      <div class="offer-discount">${discountText(p)}</div>
      <dl class="kv compact">
        <dt>${L('Tối thiểu', 'Minimum')}</dt><dd>${money(p.minimumEligibleAmount)}</dd>
        <dt>${L('Thời hạn', 'Valid')}</dt><dd>${date(p.startAt)} → ${date(p.endAt)}</dd>
        <dt>${L('Kết hợp điểm', 'Points')}</dt><dd>${p.allowPoints ? L('Có', 'Yes') : L('Không', 'No')}</dd>
        ${p.code ? `<dt>${L('Mã', 'Code')}</dt><dd class="code-mono">${esc(p.code)}</dd>` : ''}
      </dl>
      <div class="btn-row"><button class="btn-secondary" data-offer="${p.id}">${L('Xem điều kiện', 'Conditions')}</button>
        ${p.kind === 'VOUCHER' ? `<button class="btn-primary btn-accent" data-save="${p.id}">＋ ${L('Lưu vào kho', 'Save')}</button>` : `<span class="muted">${L('Tự áp dụng khi thanh toán', 'Applied at checkout')}</span>`}</div>
    </div>`;
  }

  function offersView() {
    const items = LP.publicList(ui.offersScope);
    return `
      <div class="console-header-block"><div><div class="console-title">🏷️ ${L('Ưu đãi đang có', 'Current offers')}</div>
        <div class="console-subtitle">${L('Mọi người đều xem được. Lưu voucher cần đăng nhập Member; lưu chưa giữ lượt hay điểm và không bảo đảm dùng được. Thẻ chỉ liệt kê sản phẩm đang công khai.', 'Anyone can read. Saving needs a Member and holds nothing.')}</div></div>
        <div class="control-pill-group">${[['', 'Tất cả', 'All'], ['PRODUCT', 'Mua sản phẩm', 'Products'], ['WORKSHOP', 'Đặt workshop', 'Workshop']]
          .map(([s, vi, en]) => `<button class="control-pill-btn ${ui.offersScope === s ? 'active' : ''}" data-offers-scope="${s}">${L(vi, en)}</button>`).join('')}</div></div>
      ${errorBox()}
      ${items.length ? `<div class="offer-grid">${items.map(offerCard).join('')}</div>`
        : `<div class="panel"><div class="empty-state"><div class="icon">🏷️</div><div>${L('Hiện chưa có ưu đãi nào.', 'No offer right now.')}</div></div></div>`}`;
  }

  function offerDetail() {
    let p;
    try { p = LP.publicDetail(ui.offerId); } catch (e) {
      return `<div class="panel"><div class="empty-state"><div class="icon">🔍</div><strong>404 — PROMOTION_NOT_FOUND</strong>
        <div>${L('Ưu đãi không còn hiển thị công khai (hết hạn, tạm dừng hoặc không còn sản phẩm công khai).', 'The offer is no longer public.')}</div>
        <button class="btn-secondary" data-go="offers">${L('Về danh sách ưu đãi', 'Back to offers')}</button></div></div>`;
    }
    return `
      <div class="breadcrumb"><button data-go="offers">${L('Ưu đãi', 'Offers')}</button> / ${esc(p.name)}</div>
      <div class="console-header-block"><div><div class="console-title">${esc(p.name)}</div>
        <div class="console-subtitle">${scopeBadge(p)} ${kindBadge(p)}</div></div>
        ${p.kind === 'VOUCHER' ? `<button class="btn-primary btn-accent" data-save="${p.id}">＋ ${L('Lưu vào kho', 'Save')}</button>` : ''}</div>
      ${errorBox()}
      <div class="panel"><dl class="kv">
        <dt>${L('Mức giảm', 'Discount')}</dt><dd>${discountText(p)}</dd>
        <dt>${L('Giá trị tối thiểu', 'Minimum')}</dt><dd>${money(p.minimumEligibleAmount)} ${p.scope === 'PRODUCT' ? L('(tính trên phần sản phẩm thuộc phạm vi, không tính giao hàng)', '(in-scope lines, no shipping)') : L('(phí gói)', '(package fee)')}</dd>
        <dt>${p.scope === 'WORKSHOP' ? L('Thời gian đặt bằng mã', 'Booking window') : L('Thời hạn', 'Valid')}</dt><dd>${date(p.startAt)} → ${date(p.endAt)}</dd>
        <dt>${L('Áp dụng cho', 'Applies to')}</dt><dd>${targetsText(p, true)}</dd>
        <dt>${L('Kết hợp điểm', 'Combine with points')}</dt><dd>${p.allowPoints ? L('Có', 'Yes') : L('Không', 'No')}</dd>
        ${p.kind === 'VOUCHER' ? `<dt>${L('Hạn mức', 'Limits')}</dt><dd>${L('Tổng ' + p.totalLimit + ' lượt · mỗi Member ' + p.perMemberLimit + ' lượt', p.totalLimit + ' total · ' + p.perMemberLimit + ' per Member')}</dd>
          <dt>${L('Mã', 'Code')}</dt><dd class="code-mono">${esc(p.code)}</dd>` : ''}
      </dl>
      ${p.scope === 'PRODUCT' ? `<div class="muted">${L('Chỉ liệt kê sản phẩm đang công khai. Không giới hạn chi nhánh.', 'Only public products are listed. No branch restriction.')}</div>` : ''}</div>`;
  }

  // ---------------------------------------------------------------- voucher wallet (US3)

  function walletView() {
    const saved = LP.savedVouchers(ui.role, actor());
    const note = { ACTIVE: '', SCHEDULED: L('Chưa tới thời gian dùng.', 'Not started.'), EXPIRED: L('Đã hết hạn — không dùng được, chỉ còn để tra cứu.', 'Expired.'),
      PAUSED: L('Đang tạm dừng — có thể được mở lại.', 'Paused.'), NO_PUBLIC_TARGET: L('Sản phẩm trong phạm vi hiện đang ẩn hoặc chưa công khai.', 'Its products are hidden now.') };
    return `
      <div class="console-header-block"><div><div class="console-title">🎟️ ${L('Kho Voucher', 'Voucher wallet')}</div>
        <div class="console-subtitle">${L('Mã đã lưu chỉ để tìm nhanh: chưa giữ lượt/điểm, chưa tiêu hạn mức. Có trong kho không có nghĩa là đủ điều kiện; khi thanh toán vẫn kiểm tra lại. Bạn cũng có thể nhập mã trực tiếp mà không cần lưu.', 'Saved codes hold nothing; checkout re-checks everything.')}</div></div></div>
      ${errorBox()}
      ${saved.length ? `<div class="data-table-container"><table class="console-table"><thead><tr>
          <th>${L('Mã', 'Code')}</th><th>${L('Chương trình', 'Programme')}</th><th>${L('Phạm vi', 'Scope')}</th><th>${L('Mức giảm', 'Discount')}</th><th>${L('Hạn', 'Ends')}</th><th>${L('Tình trạng', 'Status')}</th><th></th></tr></thead>
        <tbody>${saved.map((s) => `<tr><td class="code-mono">${esc(s.program.code)}</td><td>${esc(s.program.name)}<div class="muted">${esc(note[s.availability])}</div></td>
          <td>${scopeBadge(s.program)}</td><td>${discountText(s.program)}</td><td>${date(s.program.endAt)}</td><td>${availabilityBadge(s.availability)}</td>
          <td>${s.availability === 'ACTIVE' ? `<button class="btn-secondary" data-use-code="${esc(s.program.code)}" data-use-scope="${s.program.scope}">${L('Dùng', 'Use')}</button>` : ''}</td></tr>`).join('')}</tbody></table></div>`
        : `<div class="panel"><div class="empty-state"><div class="icon">🎟️</div><div>${L('Chưa lưu voucher nào.', 'No saved voucher.')}</div>
          <button class="btn-secondary" data-go="offers">${L('Xem ưu đãi', 'See offers')}</button></div></div>`}`;
  }

  // ---------------------------------------------------------------- My Loyalty (US6)

  function loyaltyView() {
    const a = LP.account(ui.role, actor(), ui.historyPage);
    const pol = a.policy;
    return `
      <div class="console-header-block"><div><div class="console-title">⭐ My Loyalty</div>
        <div class="console-subtitle">${L('Một ví điểm chung cho mua sản phẩm và workshop. Điểm không hết hạn, không có hạng thành viên.', 'One wallet for both services. Points never expire; no tiers.')}</div></div>
        <button class="btn-secondary" data-action="reload">↻ ${L('Tải lại', 'Reload')}</button></div>
      <div class="stat-grid">
        <div class="stat-card"><div class="stat-label">${L('Số dư', 'Balance')}</div><div class="stat-value">${num(a.balance)}</div><div class="muted">${L('điểm', 'points')}</div></div>
        <div class="stat-card"><div class="stat-label">${L('Đang giữ', 'Held')}</div><div class="stat-value warn">${num(a.held)}</div><div class="muted">${L('cho giao dịch chưa chốt', 'by open transactions')}</div></div>
        <div class="stat-card"><div class="stat-label">${L('Khả dụng', 'Available')}</div><div class="stat-value ok">${num(a.available)}</div><div class="muted">≈ ${money(a.available * pol.redeemPointValue)}</div></div>
      </div>
      <div class="panel"><div class="panel-title">${L('Quy đổi hiện hành', 'Current conversion')}</div>
        <div>${L(`Tích 1 điểm cho mỗi ${money(pol.earnUnitAmount)} thực trả (không tính giao hàng, làm tròn xuống). 1 điểm = ${money(pol.redeemPointValue)}. Dùng tối thiểu ${pol.minRedeemPoints} điểm/lần, tối đa ${pol.maxRedeemPercent}% giá trị sau ưu đãi.`,
          `1 point per ${money(pol.earnUnitAmount)} paid (no shipping, rounded down). 1 point = ${money(pol.redeemPointValue)}. Min ${pol.minRedeemPoints} points, up to ${pol.maxRedeemPercent}% after promotions.`)}</div></div>
      <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Lịch sử điểm', 'Point history')}</strong></div>
        ${a.history.items.length ? `<table class="console-table"><thead><tr><th>${L('Thời gian', 'Time')}</th><th>${L('Loại', 'Kind')}</th><th>${L('Giao dịch', 'Transaction')}</th>
          <th class="num">${L('Số dư', 'Balance')}</th><th class="num">${L('Giữ', 'Held')}</th><th>${L('Liên kết lần trừ gốc', 'Original debit')}</th></tr></thead>
          <tbody>${a.history.items.map((e) => `<tr><td>${date(e.at)}</td><td>${ledgerBadge(e.kind)}</td>
            <td>${e.sourceKind === 'RETAIL' ? '🛒' : '💍'} <span class="code-mono">${esc(e.sourceRef)}</span></td>
            <td class="num">${signed(e.balanceChange)}</td><td class="num">${signed(e.heldChange)}</td><td class="code-mono">${e.originalDebitId ? '#' + e.originalDebitId : '—'}</td></tr>`).join('')}</tbody></table>${pagination(a.history, 'history')}`
        : `<div class="empty-state"><div class="icon">⭐</div><div>${L('Chưa có giao dịch điểm.', 'No point history yet.')}</div></div>`}</div>`;
  }

  // ---------------------------------------------------------------- retail checkout demo (US4)

  /** Computes a quote for display; a refused voucher or points value is shown inline and the rest still prices. */
  function safeQuote(compute, selection, points) {
    const issues = {};
    const attempt = (sel, pts) => { try { return compute(sel, pts); } catch (e) { if (!(e instanceof LP.DomainError)) throw e; return e; } };
    let q = attempt(selection, points);
    if (q instanceof LP.DomainError && q.code === 'PROMOTION_INELIGIBLE') {
      issues.code = q.fieldErrors[0].message;
      q = attempt({ ...selection, mode: 'none', code: '' }, points);
    }
    if (q instanceof LP.DomainError && (q.code === 'POINTS_USE_NOT_ALLOWED' || q.code === 'INVALID_INPUT')) {
      issues.points = q.fieldErrors[0];
      q = attempt(issues.code ? { ...selection, mode: 'none', code: '' } : selection, '');
    }
    // Points that cannot be used at all are dropped (the reason is shown in the points block instead).
    if (!(q instanceof LP.DomainError) && q.points.reason && issues.points) { issues.points = null; issues.dropPoints = true; }
    return { q, issues };
  }

  function pointsBlock(q, issues, formName) {
    const p = q.points;
    const reason = p.reason ? (ISSUES[p.reason] || [p.reason, p.reason])[ui.lang === 'vi' ? 0 : 1] : '';
    return `<div class="panel"><div class="panel-title">⭐ ${L('Dùng điểm', 'Use points')}</div>
      <div class="muted">${L('Khả dụng', 'Available')}: <strong>${num(p.available)}</strong> ${L('điểm', 'points')} · ${L('tối đa cho giao dịch này', 'max here')}: <strong>${num(p.max)}</strong> (${money(p.max * q.policy.redeemPointValue)})</div>
      ${reason ? `<div class="alert warning">${esc(reason)} ${L('Bạn vẫn đặt được, chỉ không dùng điểm.', 'You can still continue without points.')}</div>` : `
      <div class="points-row"><input class="inline-input" name="points" form="${formName}" inputmode="numeric" placeholder="0" value="${esc(formName === 'checkout' ? ui.checkout.points : ui.workshop.points)}">
        <span class="muted">${L('0 = không dùng; hoặc ' + p.min + '–' + p.max + ' điểm', '0 = none; or ' + p.min + '–' + p.max)}</span>
        <button class="btn-link" type="button" data-points="${p.max}" data-points-form="${formName}">${L('Dùng tối đa', 'Use max')}</button>
        <button class="btn-link" type="button" data-points="" data-points-form="${formName}">${L('Không dùng', 'None')}</button></div>`}
      ${issues.points ? `<div class="alert error">${esc(fieldText(issues.points))}</div>` : ''}
      <div class="muted">${L('Điểm không tự trừ. Chỉ giữ khi đơn được chấp nhận và chỉ trừ khi thanh toán được xác minh.', 'Points are only held on acceptance and used on verified payment.')}</div></div>`;
  }

  function checkoutView() {
    const c = ui.checkout;
    const compute = (sel, pts) => LP.retailQuote(ui.role, actor(), c.lines, c.shipping, sel, pts);
    const { q, issues } = safeQuote(compute, { mode: c.mode, code: c.code }, c.points);
    if (issues.dropPoints) c.points = '';
    shownDigest = LP.digestOf(q);
    const saved = LP.savedVouchers(ui.role, actor()).filter((s) => s.program.scope === 'PRODUCT' && s.availability === 'ACTIVE');
    const all = [];
    LP.state.products.forEach((p) => p.variants.forEach((v) => all.push({ ...v, product: p })));
    const available = (v) => v.product.public && !v.product.deleted && v.onSale;
    return `
      <div class="console-header-block"><div><div class="console-title">🛒 ${L('Thanh toán sản phẩm', 'Retail checkout')} <span class="badge">${L('demo thay FE-08', 'FE-08 stand-in')}</span></div>
        <div class="console-subtitle">${L('Màn checkout thật thuộc FE-08; ở đây chỉ dựng giỏ mẫu để thử phần ưu đãi/điểm của FE-06. Thử mã hay chọn điểm chưa giữ gì.', 'The real checkout belongs to FE-08; this only exercises FE-06 benefits.')}</div></div></div>
      ${errorBox()}
      <form data-form="checkout" id="checkout"></form>
      <div class="two-col">
        <div>
          <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Giỏ hàng mẫu', 'Sample cart')}</strong>
            <select class="toolbar-select" data-add-line>${['<option value="">' + L('＋ Thêm sản phẩm…', '＋ Add a product…') + '</option>']
              .concat(all.map((v) => `<option value="${v.id}">${esc(v.product.name)} — ${esc(v.sku)} (${money(v.price)})${available(v) ? '' : ' · ' + L('không mua được', 'not purchasable')}</option>`)).join('')}</select></div>
            <table class="console-table"><thead><tr><th>${L('Sản phẩm', 'Product')}</th><th class="num">${L('Đơn giá', 'Price')}</th><th>${L('SL', 'Qty')}</th><th class="num">${L('Thành tiền', 'Total')}</th><th></th></tr></thead>
            <tbody>${q.rows.map((r, i) => `<tr><td>${esc(r.v.product.name)} <span class="code-mono">${esc(r.v.sku)}</span>
                ${r.purchasable ? '' : `<div><span class="badge error">${L('Không mua được (ẩn/ngừng bán/xóa) — không tính vào ưu đãi', 'Not purchasable — excluded')}</span></div>`}
                ${q.program && r.purchasable && q.program.scope === 'PRODUCT' && ((q.program.targets.productIds || []).includes(r.v.product.id) || (q.program.targets.variantIds || []).includes(r.v.id)) ? `<div><span class="badge success">${L('Thuộc phạm vi ưu đãi', 'In scope')}</span></div>` : ''}</td>
              <td class="num">${money(r.v.price)}</td><td><input class="inline-input qty" type="number" min="1" max="99" data-line-qty="${i}" value="${r.quantity}"></td>
              <td class="num">${money(r.total)}</td><td><button class="btn-link" data-remove-line="${i}">✕</button></td></tr>`).join('')}</tbody></table>
            <div class="data-table-toolbar"><span class="muted">${L('Phí giao hàng (báo giá FE-08):', 'Shipping (FE-08 quote):')}</span> <strong>${money(c.shipping)}</strong></div></div>
          <div class="panel"><div class="panel-title">🏷️ ${L('Ưu đãi chương trình (tối đa một)', 'Programme benefit (at most one)')}</div>
            <label class="radio"><input type="radio" name="mode" value="auto" form="checkout" ${c.mode === 'auto' ? 'checked' : ''}> ${L('Giảm tự động tốt nhất', 'Best automatic discount')}
              <span class="muted">${q.autos.length ? q.autos.map((a) => esc(a.name) + ' (−' + money(a.discount) + ')').join(' · ') : L('không có giảm tự động hợp lệ', 'none eligible')}</span></label>
            <label class="radio"><input type="radio" name="mode" value="voucher" form="checkout" ${c.mode === 'voucher' ? 'checked' : ''}> ${L('Dùng voucher (bỏ giảm tự động)', 'Use a voucher (replaces automatic)')}</label>
            <div class="points-row"><input class="inline-input" name="code" form="checkout" placeholder="${L('Nhập mã, ví dụ sp10', 'Code, e.g. sp10')}" value="${esc(c.code)}">
              ${saved.map((s) => `<button class="btn-link" type="button" data-pick-code="${esc(s.program.code)}">${esc(s.program.code)}</button>`).join('')}</div>
            <label class="radio"><input type="radio" name="mode" value="none" form="checkout" ${c.mode === 'none' ? 'checked' : ''}> ${L('Không dùng ưu đãi', 'No benefit')}</label>
            ${issues.code ? `<div class="alert error"><span class="code">PROMOTION_INELIGIBLE</span> — ${esc((ISSUES[issues.code] || [issues.code, issues.code])[ui.lang === 'vi' ? 0 : 1])} ${L('Chọn lại hoặc không dùng.', 'Choose again or none.')}</div>` : ''}
          </div>
          ${pointsBlock(q, issues, 'checkout')}
          <div class="btn-row"><button class="btn-secondary" type="submit" form="checkout">↻ ${L('Tính lại', 'Recalculate')}</button></div>
        </div>
        <div>${retailSummary(q)}</div>
      </div>`;
  }

  function retailSummary(q) {
    return `<div class="panel summary"><div class="panel-title">${L('Tóm tắt thanh toán', 'Summary')}</div>
      <dl class="kv">
        <dt>${L('Tiền hàng', 'Merchandise')}</dt><dd>${money(q.merchandise)}</dd>
        ${q.program ? `<dt>${L('Phần đủ điều kiện', 'Eligible base')}</dt><dd>${money(q.eligibleBase)}</dd>
          <dt>${esc(q.program.name)}</dt><dd class="minus">− ${money(q.programDiscount)}</dd>` : `<dt>${L('Ưu đãi', 'Promotion')}</dt><dd>—</dd>`}
        <dt>${L('Điểm', 'Points')} (${num(q.points.points)})</dt><dd class="minus">− ${money(q.points.amount)}</dd>
        <dt>${L('Giao hàng', 'Shipping')}</dt><dd>${money(q.shipping)}</dd>
        <dt class="total">${L('Tổng thanh toán', 'Total')}</dt><dd class="total">${money(q.payable)}</dd>
      </dl>
      <div class="muted">${L(`Điểm dự kiến: ${q.earnPreview} điểm (cộng sau khi FE-08 xác minh thanh toán đủ; không giảm chính đơn này).`, `Expected: ${q.earnPreview} points after verified payment.`)}</div>
      <div class="muted">${L('Chính sách điểm', 'Points policy')} rev ${q.policy.revision}</div>
      <button class="btn-primary btn-accent full" data-action="accept-retail" ${q.rows.length ? '' : 'disabled'}>${L('Xác nhận đặt hàng', 'Place order')}</button>
      <div class="muted">${L('Khi xác nhận, hệ thống kiểm tra lại giá, điều kiện, số dư và hạn mức. Nếu khác tổng bạn đang xem thì hỏi lại, không tự tạo đơn khác tổng.', 'Everything is re-checked on confirmation; a changed total is shown again.')}</div></div>`;
  }

  // ---------------------------------------------------------------- workshop booking demo (US5)

  function workshopView() {
    const w = ui.workshop;
    const compute = (sel, pts) => LP.workshopQuote(ui.role, actor(), w.sessionId, w.participants, sel.mode === 'none' ? '' : sel.code, pts);
    let { q, issues } = safeQuote(compute, { mode: w.code ? 'voucher' : 'none', code: w.code }, w.points);
    let netError = null;
    if (q instanceof LP.DomainError) { netError = q.code; q = null; }
    if (issues.dropPoints) w.points = '';
    shownDigest = q ? LP.digestOf(q) : null;
    const saved = LP.savedVouchers(ui.role, actor()).filter((s) => s.program.scope === 'WORKSHOP' && s.availability === 'ACTIVE');
    const sessions = [];
    LP.state.packages.forEach((p) => p.sessions.forEach((s) => sessions.push({ ...s, package: p })));
    return `
      <div class="console-header-block"><div><div class="console-title">💍 ${L('Đặt workshop', 'Workshop booking')} <span class="badge">${L('demo thay FE-03', 'FE-03 stand-in')}</span></div>
        <div class="console-subtitle">${L('Ưu đãi workshop chỉ cho Member đặt trực tiếp trên website. FE-06 trừ voucher rồi điểm trên phí gói để ra phí sau ưu đãi; cọc và phần còn lại do 003.1 tính.', 'Workshop benefits are for Members booking on the website; deposit belongs to 003.1.')}</div></div></div>
      ${errorBox()}
      <form data-form="workshop" id="workshop"></form>
      <div class="two-col">
        <div>
          <div class="panel"><div class="panel-title">${L('Lịch và số người', 'Session and people')}</div>
            <div class="form-grid">
              <div class="field field-full"><label>${L('Gói · chi nhánh · ngày', 'Package · branch · date')}</label><select name="sessionId" form="workshop">
                ${sessions.map((s) => `<option value="${s.id}" ${s.id === Number(w.sessionId) ? 'selected' : ''}>${esc(s.package.name)} · ${esc(LP.branch(s.branchId).name)} · ${s.date} ${s.time} · ${money(s.package.fee)}/${L('người', 'person')}</option>`).join('')}</select></div>
              <div class="field"><label>${L('Số người', 'People')}</label><input name="participants" form="workshop" type="number" min="1" max="10" value="${esc(w.participants)}"></div>
            </div></div>
          <div class="panel"><div class="panel-title">🎟️ ${L('Voucher workshop (tối đa một mỗi booking)', 'Workshop voucher (one per booking)')}</div>
            <div class="points-row"><input class="inline-input" name="code" form="workshop" placeholder="${L('Nhập mã, ví dụ ws100', 'Code, e.g. ws100')}" value="${esc(w.code)}">
              ${saved.map((s) => `<button class="btn-link" type="button" data-pick-ws-code="${esc(s.program.code)}">${esc(s.program.code)}</button>`).join('')}
              ${w.code ? `<button class="btn-link" type="button" data-pick-ws-code="">${L('Bỏ mã', 'Remove')}</button>` : ''}</div>
            ${issues.code ? `<div class="alert error"><span class="code">PROMOTION_INELIGIBLE</span> — ${esc((ISSUES[issues.code] || [issues.code, issues.code])[ui.lang === 'vi' ? 0 : 1])}</div>` : ''}
            <div class="muted">${L('Trần giảm áp một lần cho cả booking, không nhân số người.', 'The cap applies once per booking.')}</div></div>
          ${q ? pointsBlock(q, issues, 'workshop') : ''}
          <div class="btn-row"><button class="btn-secondary" type="submit" form="workshop">↻ ${L('Tính lại', 'Recalculate')}</button></div>
        </div>
        <div>${netError ? `<div class="alert error"><span class="code">${esc(netError)}</span> — ${esc(message(netError))}</div>` : workshopSummary(q)}</div>
      </div>`;
  }

  function workshopSummary(q) {
    return `<div class="panel summary"><div class="panel-title">${L('Tóm tắt booking', 'Summary')}</div>
      <dl class="kv">
        <dt>${L('Phí gói', 'Package fee')} (${q.participants} ${L('người', 'people')})</dt><dd>${money(q.fee)}</dd>
        <dt>${q.program ? esc(q.program.code) : L('Voucher', 'Voucher')}</dt><dd class="minus">− ${money(q.programDiscount)}</dd>
        <dt>${L('Điểm', 'Points')} (${num(q.points.points)})</dt><dd class="minus">− ${money(q.points.amount)}</dd>
        <dt class="total">${L('Phí sau ưu đãi', 'Fee after benefits')}</dt><dd class="total">${money(q.postPromotionAmount)}</dd>
        <dt>${L('Cọc (003.1 tính: làm tròn lên một nửa)', 'Deposit (by 003.1)')}</dt><dd>${money(q.deposit)}</dd>
        <dt>${L('Còn thu sau', 'Remaining')}</dt><dd>${money(q.remaining)}</dd>
      </dl>
      <div class="muted">${L('Thu phần còn lại không giảm voucher hay trừ điểm lần nữa. Điểm dự kiến ' + q.earnPreview + ' — chỉ cộng một lần khi workshop hoàn thành VÀ đã trả đủ.', 'The remainder gets no second discount. Expected ' + q.earnPreview + ' points once completed and fully paid.')}</div>
      <button class="btn-primary btn-accent full" data-action="accept-workshop">${L('Xác nhận đặt lịch', 'Confirm booking')}</button></div>`;
  }

  // ---------------------------------------------------------------- my transactions + source outcomes (US4–US7)

  function outcomeButtons(b) {
    const btn = (kind, label, cls) => `<button class="${cls || 'btn-secondary'}" data-outcome="${b.id}:${kind}">${label}</button>`;
    if (b.sourceKind === 'RETAIL') {
      if (b.state === 'HELD') return btn('PAID', L('FE-08: đã thanh toán đủ', 'FE-08: paid'), 'btn-primary') + btn('EXPIRED', L('FE-08: hết hạn, chưa trả', 'FE-08: expired unpaid'));
      if (b.state === 'SETTLED') return btn('PAID', L('Gửi lặp "đã thanh toán"', 'Repeat "paid"')) + btn('EXPIRED', L('Thử "hết hạn" sau khi đã trả', 'Try "expired" after paid'));
      return btn('EXPIRED', L('Gửi lặp "hết hạn"', 'Repeat "expired"'));
    }
    if (b.state === 'HELD') return btn('DEPOSIT', L('FE-03: xác minh cọc', 'FE-03: deposit verified'), 'btn-primary') + btn('CANCELLED', L('FE-03: hủy hợp lệ', 'FE-03: valid cancel')) + btn('EXPIRED', L('Hết hạn chưa cọc', 'Expired unpaid'));
    if (b.state === 'SETTLED' && !b.completed) return btn('COMPLETED_PAID', L('FE-03: hoàn thành & trả đủ', 'FE-03: completed & fully paid'), 'btn-primary') + btn('CANCELLED', L('FE-03: hủy hợp lệ', 'FE-03: valid cancel'));
    if (b.state === 'SETTLED') return btn('COMPLETED_PAID', L('Gửi lặp "hoàn thành"', 'Repeat "completed"')) + btn('CANCELLED', L('Thử hủy sau hoàn thành', 'Try cancel after completion'));
    return btn('CANCELLED', L('Gửi lặp "hủy"', 'Repeat "cancel"'));
  }

  function transactionsView() {
    const list = LP.myBenefits(ui.role, actor());
    return `
      <div class="console-header-block"><div><div class="console-title">🧾 ${L('Giao dịch của tôi', 'My transactions')}</div>
        <div class="console-subtitle">${L('Kết quả thanh toán/hủy thật đến từ FE-08 và FE-03 sau khi họ xác minh — không phải từ trình duyệt. Các nút dưới đây chỉ đóng vai FE-08/FE-03 trong prototype. Gửi lặp một kết quả không cộng/trừ thêm.', 'Real outcomes come from FE-08/FE-03 after verification; these buttons only stand in for them.')}</div></div></div>
      ${errorBox()}
      ${list.length ? list.map((b) => `<div class="panel tx">
          <div class="tx-head"><span>${b.sourceKind === 'RETAIL' ? '🛒' : '💍'} <span class="code-mono">${esc(b.sourceRef)}</span></span> ${benefitBadge(b)}
            ${b.completed ? `<span class="badge success">${L('Đã hoàn thành', 'Completed')}</span>` : ''}
            ${b.earned ? `<span class="badge success">+${num(b.earnedPoints)} ${L('điểm', 'pts')}</span>` : ''}
            <span class="muted">${date(b.acceptedAt)}</span></div>
          <div class="muted">${esc(b.summary)}</div>
          <dl class="kv compact">
            <dt>${L('Ưu đãi', 'Promotion')}</dt><dd>${b.programName ? esc(b.programName) + ' (rev ' + b.programRevision + ') − ' + money(b.discount) : '—'}</dd>
            <dt>${L('Điểm', 'Points')}</dt><dd>${num(b.points)} (− ${money(b.pointsAmount)})</dd>
            <dt>${b.sourceKind === 'RETAIL' ? L('Tổng đã đồng ý', 'Agreed total') : L('Phí sau ưu đãi', 'Fee after benefits')}</dt><dd>${money(b.payable)}</dd>
            <dt>${L('Chính sách ghi nhận', 'Recorded policy')}</dt><dd>rev ${LP.policyById(b.policyId).revision}</dd>
            ${b.state === 'HELD' ? `<dt>${L('Hạn giữ gốc', 'Original hold deadline')}</dt><dd>${date(b.expiresAt)}</dd>` : ''}
          </dl>
          <div class="btn-row">${outcomeButtons(b)}</div></div>`).join('')
        : `<div class="panel"><div class="empty-state"><div class="icon">🧾</div><div>${L('Chưa có giao dịch dùng ưu đãi.', 'No transaction yet.')}</div>
          <div class="btn-row"><button class="btn-secondary" data-go="checkout">🛒 ${L('Thanh toán sản phẩm', 'Retail checkout')}</button><button class="btn-secondary" data-go="workshop">💍 ${L('Đặt workshop', 'Workshop')}</button></div></div></div>`}`;
  }

  // ---------------------------------------------------------------- Manager: programmes (US1, US2)

  function programsView() {
    const f = ui.list;
    const result = LP.managementList(ui.role, f);
    return `
      <div class="console-header-block"><div><div class="console-title">📋 ${L('Chương trình ưu đãi', 'Programmes')}</div>
        <div class="console-subtitle">${L('Voucher sản phẩm, voucher workshop và giảm tự động sản phẩm. Lượt đã dùng và tiền đã giảm không tính phần đang giữ.', 'Product vouchers, workshop vouchers and automatic product discounts.')}</div></div>
        <div class="btn-row"><button class="btn-primary btn-accent" data-go="create">＋ ${L('Tạo chương trình', 'Create')}</button></div></div>
      <div class="data-table-container">
        <form class="data-table-toolbar" data-form="programs-filter">
          <select class="toolbar-select" name="scope"><option value="">${L('Mọi phạm vi', 'All scopes')}</option>
            <option value="PRODUCT" ${f.scope === 'PRODUCT' ? 'selected' : ''}>${L('Sản phẩm', 'Products')}</option>
            <option value="WORKSHOP" ${f.scope === 'WORKSHOP' ? 'selected' : ''}>Workshop</option></select>
          <select class="toolbar-select" name="status"><option value="">${L('Mọi trạng thái', 'Any status')}</option>
            <option value="ENABLED" ${f.status === 'ENABLED' ? 'selected' : ''}>${L('Đang bật', 'Enabled')}</option>
            <option value="PAUSED" ${f.status === 'PAUSED' ? 'selected' : ''}>${L('Tạm dừng', 'Paused')}</option></select>
          <button class="btn-secondary" type="submit">${L('Lọc', 'Filter')}</button>
        </form>
        ${result.items.length ? `<table class="console-table"><thead><tr><th>${L('Tên', 'Name')}</th><th>${L('Loại', 'Kind')}</th><th>${L('Mã', 'Code')}</th>
          <th>${L('Tình trạng', 'Status')}</th><th class="num">${L('Đã dùng / giữ / hạn mức', 'Used / held / limit')}</th><th class="num">${L('Tiền đã giảm', 'Discounted')}</th></tr></thead>
          <tbody>${result.items.map((p) => `<tr class="clickable" data-program="${p.id}"><td><strong>${esc(p.name)}</strong>${LP.targetWarnings(p).length ? ' <span class="badge warning">⚠</span>' : ''}</td>
            <td>${scopeBadge(p)} ${kindBadge(p)}</td><td class="code-mono">${esc(p.code || '—')}</td><td>${availabilityBadge(LP.availability(p))}</td>
            <td class="num">${p.kind === 'VOUCHER' ? num(p.consumedCount) + ' / ' + num(p.heldCount) + ' / ' + num(p.totalLimit) : num(p.acceptedCount) + ' ' + L('đơn', 'orders')}</td>
            <td class="num">${money(p.committedDiscount)}</td></tr>`).join('')}</tbody></table>${pagination(result, 'programs')}`
          : `<div class="empty-state"><div class="icon">🔍</div><div>${L('Không có chương trình khớp bộ lọc.', 'No programme matches.')}</div></div>`}
      </div>`;
  }

  function programDetail() {
    const { program: p, history } = LP.usage(ui.role, ui.programId);
    const warnings = LP.targetWarnings(p);
    const a = LP.availability(p);
    const stateText = { DELETED: L('đã xóa', 'deleted'), NOT_PUBLIC: L('đang ẩn / chưa công khai', 'hidden'), NOT_ON_SALE: L('ngừng bán', 'not on sale') };
    return `
      <div class="breadcrumb"><button data-go="programs">${L('Chương trình', 'Programmes')}</button> / ${esc(p.name)}</div>
      <div class="console-header-block"><div><div class="console-title">${esc(p.name)}</div>
        <div class="console-subtitle">${scopeBadge(p)} ${kindBadge(p)} ${availabilityBadge(a)} <span class="muted">v${p.version} · rev ${p.revision}</span></div></div>
        <div class="btn-row">${p.status === 'ENABLED' ? `<button class="btn-secondary" data-action="pause">⏸ ${L('Tạm dừng', 'Pause')}</button>` : `<button class="btn-primary" data-action="resume">▶ ${L('Mở lại', 'Resume')}</button>`}</div></div>
      ${errorBox()}
      ${warnings.length ? `<div class="alert warning">⚠ ${L('Phạm vi có mục không còn dùng được: ', 'Some targets are unusable: ')}${warnings.map((w) => esc(w.name) + ' (' + stateText[w.state] + ')').join(', ')}.
        ${L('Chương trình không tự tạm dừng; các dòng này không đủ điều kiện ở checkout. Bạn tự quyết định có tạm dừng hay không.', 'The programme is not paused automatically; decide yourself.')}</div>` : ''}
      <div class="stat-grid">
        ${p.kind === 'VOUCHER' ? `
        <div class="stat-card"><div class="stat-label">${L('Đã dùng', 'Used')}</div><div class="stat-value">${num(p.consumedCount)}</div><div class="muted">/ ${num(p.totalLimit)} ${L('lượt', 'uses')}</div></div>
        <div class="stat-card"><div class="stat-label">${L('Đang giữ', 'Held')}</div><div class="stat-value warn">${num(p.heldCount)}</div><div class="muted">${L('không tính là đã dùng', 'not counted as used')}</div></div>`
        : `<div class="stat-card"><div class="stat-label">${L('Đơn đã chốt', 'Settled orders')}</div><div class="stat-value">${num(p.acceptedCount)}</div></div>`}
        <div class="stat-card"><div class="stat-label">${L('Tiền đã giảm', 'Discounted')}</div><div class="stat-value ok">${money(p.committedDiscount)}</div><div class="muted">${L('không gồm booking đã hủy hợp lệ', 'excludes valid cancellations')}</div></div>
      </div>
      <div class="two-col">
        <form class="panel" data-form="edit-program">
          <div class="panel-title">${L('Sửa điều kiện (chỉ áp dụng cho giao dịch mới)', 'Edit terms (new transactions only)')}</div>
          <input type="hidden" name="version" value="${p.version}">
          <div class="form-grid">
            <div class="field field-full"><label>${L('Tên', 'Name')}</label><input name="name" value="${esc(p.name)}"></div>
            <div class="field"><label>${p.scope === 'WORKSHOP' ? L('Đặt bằng mã từ', 'Booking from') : L('Bắt đầu', 'Start')}</label><input type="datetime-local" name="startAt" value="${toLocalInput(p.startAt)}"></div>
            <div class="field"><label>${p.scope === 'WORKSHOP' ? L('Đến', 'Until') : L('Kết thúc', 'End')}</label><input type="datetime-local" name="endAt" value="${toLocalInput(p.endAt)}"></div>
            ${discountFields(!ui.editDiscountKind || ui.editDiscountKind === p.discount.kind ? p.discount : { kind: ui.editDiscountKind })}
            <div class="field"><label>${L('Giá trị tối thiểu (VND)', 'Minimum (VND)')}</label><input name="minimumEligibleAmount" value="${p.minimumEligibleAmount}"></div>
            ${p.kind === 'VOUCHER' ? `
            <div class="field"><label>${L('Tổng lượt', 'Total uses')}</label><input name="totalLimit" value="${p.totalLimit}"></div>
            <div class="field"><label>${L('Lượt mỗi Member', 'Uses per Member')}</label><input name="perMemberLimit" value="${p.perMemberLimit}"></div>
            <label class="radio field-full"><input type="checkbox" name="allowPoints" value="1" ${p.allowPoints ? 'checked' : ''}> ${L('Cho kết hợp điểm', 'Combine with points')}</label>`
            : `<div class="field-full muted">${L('Giảm tự động luôn kết hợp điểm và không có hạn mức lượt.', 'Automatic discounts always combine with points and have no quota.')}</div>`}
          </div>
          <div class="muted">${L('Không đổi được mã, loại, phạm vi hay đối tượng áp dụng. Hạn mức không được thấp hơn đã dùng cộng đang giữ.', 'Code, kind, scope and targets cannot change. Limits never drop below used + held.')}</div>
          <div class="btn-row"><button class="btn-primary" type="submit">${L('Lưu thay đổi', 'Save')}</button></div>
        </form>
        <div class="panel"><div class="panel-title">${L('Điều kiện hiện hành', 'Current terms')}</div>
          <dl class="kv">
            <dt>${L('Mã', 'Code')}</dt><dd class="code-mono">${esc(p.code || L('(không cần mã)', '(no code)'))}</dd>
            <dt>${L('Mức giảm', 'Discount')}</dt><dd>${discountText(p)}</dd>
            <dt>${L('Đối tượng', 'Targets')}</dt><dd>${targetsText(p, false)}</dd>
            <dt>${L('Kết hợp điểm', 'Points')}</dt><dd>${p.allowPoints ? L('Có', 'Yes') : L('Không', 'No')}</dd>
            ${p.kind === 'VOUCHER' ? `<dt>${L('Hạn mức', 'Limits')}</dt><dd>${num(p.totalLimit)} / ${num(p.perMemberLimit)} ${L('mỗi Member', 'per Member')}</dd>` : ''}
          </dl>
          <div class="panel-title">${L('Các phiên bản điều kiện', 'Revisions')}</div>
          <table class="console-table"><thead><tr><th>rev</th><th>${L('Thời điểm', 'At')}</th><th>${L('Mức giảm', 'Discount')}</th><th>${L('Hạn mức', 'Limit')}</th></tr></thead>
            <tbody>${p.revisions.slice().reverse().map((r) => `<tr><td>${r.revision}</td><td>${date(r.at)}</td><td>${discountText(r)}</td><td>${r.totalLimit == null ? '—' : num(r.totalLimit) + '/' + num(r.perMemberLimit)}</td></tr>`).join('')}</tbody></table>
        </div>
      </div>
      <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Lịch sử sử dụng (giữ cả dùng và hoàn để tra cứu)', 'Usage history (uses and restores kept)')}</strong></div>
        ${history.length ? `<table class="console-table"><thead><tr><th>${L('Giao dịch', 'Transaction')}</th><th>${L('Trạng thái', 'State')}</th><th>rev</th><th class="num">${L('Giảm', 'Discount')}</th><th>${L('Chấp nhận lúc', 'Accepted')}</th></tr></thead>
          <tbody>${history.map((b) => `<tr><td class="code-mono">${esc(b.sourceRef)}</td><td>${benefitBadge(b)}</td><td>${b.programRevision}</td><td class="num">${money(b.discount)}</td><td>${date(b.acceptedAt)}</td></tr>`).join('')}</tbody></table>`
          : `<div class="empty-state"><div>${L('Chưa có giao dịch nào dùng chương trình này.', 'No usage yet.')}</div></div>`}</div>`;
  }

  function discountFields(d) {
    const kind = d ? d.kind : 'PERCENT';
    return `<div class="field"><label>${L('Kiểu giảm', 'Discount kind')}</label><select name="discountKind" data-rerender>
        <option value="PERCENT" ${kind === 'PERCENT' ? 'selected' : ''}>${L('Phần trăm', 'Percent')}</option><option value="FIXED" ${kind === 'FIXED' ? 'selected' : ''}>${L('Số tiền cố định', 'Fixed amount')}</option></select></div>
      ${kind === 'PERCENT' ? `<div class="field"><label>${L('Tỷ lệ (%)', 'Percentage')}</label><input name="percentage" value="${esc(d && d.percentage || '')}"></div>
        <div class="field"><label>${L('Giảm tối đa (VND)', 'Cap (VND)')}</label><input name="maxDiscountAmount" value="${esc(d && d.maxDiscountAmount || '')}"></div>`
        : `<div class="field"><label>${L('Mức giảm (VND)', 'Amount (VND)')}</label><input name="amount" value="${esc(d && d.amount || '')}"></div>`}`;
  }

  function createView() {
    const d = ui.createDraft || (ui.createDraft = { kind: 'VOUCHER', scope: 'PRODUCT', discount: { kind: 'PERCENT' } });
    const now = Date.now();
    const products = LP.state.products;
    return `
      <div class="breadcrumb"><button data-go="programs">${L('Chương trình', 'Programmes')}</button> / ${L('Tạo mới', 'Create')}</div>
      <div class="console-header-block"><div><div class="console-title">＋ ${L('Tạo chương trình', 'Create programme')}</div>
        <div class="console-subtitle">${L('Chương trình ngày lễ cũng là một voucher/giảm tự động riêng có thời gian riêng, không phải lớp cộng thêm. Mã duy nhất toàn hệ thống, không tái sử dụng.', 'Holiday campaigns are ordinary programmes. Codes are unique forever.')}</div></div></div>
      ${errorBox()}
      <form class="panel" data-form="create">
        <div class="form-grid">
          <div class="field"><label>${L('Loại', 'Kind')}</label><select name="kind" data-rerender>
            <option value="VOUCHER" ${d.kind === 'VOUCHER' ? 'selected' : ''}>Voucher</option>
            <option value="AUTO_DISCOUNT" ${d.kind === 'AUTO_DISCOUNT' ? 'selected' : ''}>${L('Giảm tự động (chỉ sản phẩm)', 'Automatic (products only)')}</option></select></div>
          <div class="field"><label>${L('Phạm vi', 'Scope')}</label><select name="scope" data-rerender>
            <option value="PRODUCT" ${d.scope === 'PRODUCT' ? 'selected' : ''}>${L('Mua sản phẩm', 'Products')}</option>
            ${d.kind === 'VOUCHER' ? `<option value="WORKSHOP" ${d.scope === 'WORKSHOP' ? 'selected' : ''}>${L('Đặt workshop', 'Workshop')}</option>` : ''}</select></div>
          <div class="field field-full"><label>${L('Tên *', 'Name *')}</label><input name="name" maxlength="400"></div>
          ${d.kind === 'VOUCHER' ? `<div class="field"><label>${L('Mã *', 'Code *')}</label><input name="code" placeholder="SP15"></div>` : ''}
          <div class="field"><label>${d.scope === 'WORKSHOP' ? L('Đặt bằng mã từ *', 'Booking from *') : L('Bắt đầu *', 'Start *')}</label><input type="datetime-local" name="startAt" value="${toLocalInput(new Date(now).toISOString())}"></div>
          <div class="field"><label>${d.scope === 'WORKSHOP' ? L('Đến *', 'Until *') : L('Kết thúc *', 'End *')}</label><input type="datetime-local" name="endAt" value="${toLocalInput(new Date(now + 30 * 86400000).toISOString())}"></div>
          ${discountFields(d.discount)}
          <div class="field"><label>${d.scope === 'WORKSHOP' ? L('Phí gói tối thiểu (VND)', 'Minimum fee') : L('Giá trị tối thiểu (VND)', 'Minimum (VND)')}</label><input name="minimumEligibleAmount" value="0"></div>
          ${d.kind === 'VOUCHER' ? `
          <div class="field"><label>${L('Tổng lượt *', 'Total uses *')}</label><input name="totalLimit" value="100"></div>
          <div class="field"><label>${L('Lượt mỗi Member *', 'Uses per Member *')}</label><input name="perMemberLimit" value="1"></div>
          <label class="radio field-full"><input type="checkbox" name="allowPoints" value="1" checked> ${L('Cho kết hợp điểm', 'Combine with points')}</label>`
          : `<div class="field-full muted">${L('Giảm tự động không cần mã, không giới hạn chi nhánh và luôn kết hợp điểm.', 'No code, no branch limit, always combines with points.')}</div>`}
        </div>
        <div class="panel-title">${L('Đối tượng áp dụng', 'Targets')}</div>
        ${d.scope === 'PRODUCT' ? `
          <div class="muted">${L('Được chọn sản phẩm chưa công khai, đang ẩn hoặc size ngừng bán; sản phẩm đã xóa bị từ chối. Nếu chọn cả sản phẩm và size thì size phải thuộc sản phẩm đã chọn. Không giới hạn chi nhánh.', 'Unpublished/hidden products are allowed; deleted ones are refused. Sizes must belong to a chosen product.')}</div>
          <div class="target-grid">${products.map((p) => `<div class="target-box"><label class="radio"><input type="checkbox" name="productIds" value="${p.id}"> <strong>${esc(p.name)}</strong>
              ${p.deleted ? `<span class="badge error">${L('đã xóa', 'deleted')}</span>` : p.public ? '' : `<span class="badge warning">${L('chưa công khai', 'unpublished')}</span>`}</label>
            ${p.variants.map((v) => `<label class="radio sub"><input type="checkbox" name="variantIds" value="${v.id}"> <span class="code-mono">${esc(v.sku)}</span>${v.onSale ? '' : ` <span class="badge">${L('ngừng bán', 'off sale')}</span>`}</label>`).join('')}</div>`).join('')}</div>`
        : `<div class="target-grid">
            <div class="target-box"><strong>${L('Gói *', 'Packages *')}</strong>${LP.state.packages.map((p) => `<label class="radio sub"><input type="checkbox" name="packageIds" value="${p.id}"> ${esc(p.name)}</label>`).join('')}</div>
            <div class="target-box"><strong>${L('Chi nhánh (bỏ trống = mọi chi nhánh)', 'Branches (empty = all)')}</strong>${LP.state.branches.map((b) => `<label class="radio sub"><input type="checkbox" name="branchIds" value="${b.id}"> ${esc(b.name)}</label>`).join('')}</div>
            <div class="target-box"><strong>${L('Ngày tham gia (tùy chọn)', 'Workshop dates (optional)')}</strong><input name="workshopDates" placeholder="2026-11-05, 2026-11-06">
              <div class="muted">${L('Khác với thời gian được đặt bằng mã ở trên.', 'Separate from the booking window above.')}</div></div></div>`}
        <div class="btn-row"><button class="btn-primary btn-accent" type="submit">${L('Tạo chương trình', 'Create')}</button></div>
      </form>`;
  }

  // ---------------------------------------------------------------- Manager: points policy (US8)

  function policyView() {
    const pol = LP.policy();
    const version = LP.state.policyVersion;
    return `
      <div class="console-header-block"><div><div class="console-title">⚙️ ${L('Chính sách điểm', 'Points policy')}</div>
        <div class="console-subtitle">${L('Chung cho mua sản phẩm và workshop. Thay đổi chỉ áp dụng cho giao dịch được chấp nhận sau đó; giao dịch cũ (kể cả workshop hoàn thành sau ngày đổi) giữ chính sách đã ghi.', 'Shared by both services; changes apply to transactions accepted afterwards only.')}</div></div></div>
      ${errorBox()}
      <div class="two-col">
        <form class="panel" data-form="policy">
          <div class="panel-title">${L('Cập nhật', 'Update')} <span class="muted">(rev ${pol.revision} · v${version})</span></div>
          <input type="hidden" name="version" value="${version}">
          <div class="form-grid">
            <div class="field"><label>${L('Số tiền cho 1 điểm tích (VND)', 'VND per earned point')}</label><input name="earnUnitAmount" value="${pol.earnUnitAmount}"></div>
            <div class="field"><label>${L('Giá trị 1 điểm khi dùng (VND)', 'VND value per point')}</label><input name="redeemPointValue" value="${pol.redeemPointValue}"></div>
            <div class="field"><label>${L('Điểm tối thiểu mỗi lần dùng', 'Minimum points per use')}</label><input name="minRedeemPoints" value="${pol.minRedeemPoints}"></div>
            <div class="field"><label>${L('Trần giảm bằng điểm (% sau ưu đãi)', 'Points cap (% after promotions)')}</label><input name="maxRedeemPercent" value="${pol.maxRedeemPercent}"></div>
          </div>
          <div class="muted">${L('Làm tròn: xuống (FLOOR). Lưu lỗi thì giữ nguyên chính sách hợp lệ cũ.', 'Rounding: FLOOR. A failed save keeps the old valid policy.')}</div>
          <div class="btn-row"><button class="btn-primary" type="submit">${L('Lưu chính sách', 'Save policy')}</button></div>
        </form>
        <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Các phiên bản', 'Revisions')}</strong></div>
          <table class="console-table"><thead><tr><th>rev</th><th class="num">${L('Tích', 'Earn')}</th><th class="num">${L('1 điểm', '1 pt')}</th><th class="num">${L('Tối thiểu', 'Min')}</th><th class="num">${L('Trần', 'Cap')}</th><th>${L('Bởi', 'By')}</th></tr></thead>
            <tbody>${LP.state.policies.slice().reverse().map((x) => `<tr><td>${x.revision}${x.id === pol.id ? ' <span class="badge success">' + L('hiện hành', 'current') + '</span>' : ''}</td>
              <td class="num">${money(x.earnUnitAmount)}</td><td class="num">${money(x.redeemPointValue)}</td><td class="num">${x.minRedeemPoints}</td><td class="num">${x.maxRedeemPercent}%</td>
              <td>${esc(x.createdBy)} · ${date(x.createdAt)}</td></tr>`).join('')}</tbody></table></div>
      </div>`;
  }

  // ---------------------------------------------------------------- modals

  function modal() {
    const m = ui.modal;
    let body = '';
    if (m.type === 'session') {
      body = `<div class="modal-header">🔐 ${L('Phiên đăng nhập đã hết hạn', 'Session expired')} (401)</div>
        <div class="modal-body"><div class="alert warning">${L('Thao tác vừa rồi CHƯA được lưu. Dữ liệu bạn đã nhập trên màn hình vẫn được giữ.', 'Nothing was saved. What you typed is kept.')}</div>
          <div class="muted">${L('Ở ứng dụng thật, nút dưới chuyển sang đăng nhập của 001 rồi quay lại đúng màn hình này.', 'In the real app this opens the 001 sign-in and returns here.')}</div></div>
        <div class="modal-footer"><button class="btn-primary" data-action="relogin">${L('Đăng nhập lại (giả lập)', 'Sign in again (simulated)')}</button></div>`;
    } else if (m.type === 'lost') {
      body = `<div class="modal-header">📶 ${L('Không nhận được phản hồi', 'No response received')}</div>
        <div class="modal-body"><div class="alert warning">${L('Kết nối bị ngắt trước khi nhận phản hồi. Thao tác có thể ĐÃ hoặc CHƯA được ghi — đừng làm lại từ đầu, vì có thể tạo bản trùng. Chưa coi là thành công.', 'The action may or may not be saved — do not start over.')}</div>
          <dl class="kv"><dt>Idempotency-Key</dt><dd class="code-mono">${esc(m.key)}</dd></dl>
          ${m.checked === 'NOT_FOUND' ? `<div class="alert info">${L('Chưa thấy kết quả cho khóa này. Điều này không khẳng định lệnh chưa chạy; gửi lại cùng khóa là an toàn.', 'No result yet; resending with the same key is safe.')}</div>` : ''}
          <div class="muted">${L('"Kiểm tra kết quả" đọc lại trạng thái đã lưu. "Gửi lại cùng khóa" gửi đúng yêu cầu cũ với cùng Idempotency-Key: nếu đã ghi, server trả lại kết quả cũ.', '"Check" re-reads the saved state; "Resend" repeats with the same key.')}</div></div>
        <div class="modal-footer"><button class="btn-secondary" data-action="resend">↻ ${L('Gửi lại cùng khóa', 'Resend with same key')}</button>
          <button class="btn-primary" data-action="check-receipt">🔎 ${L('Kiểm tra kết quả', 'Check result')}</button></div>`;
    } else if (m.type === 'conflict') {
      body = `<div class="modal-header">⚠️ ${L('Manager khác vừa thay đổi dữ liệu', 'Another Manager changed this')} (409)</div>
        <div class="modal-body"><div class="alert warning"><span class="code">STALE_VERSION</span> — ${L('Thay đổi của bạn CHƯA được lưu. Trong lúc bạn sửa, bản ghi đã có phiên bản mới.', 'Your change was NOT saved.')}</div>
          <div class="muted">${L('"Tải bản mới" bỏ phần bạn nhập. "Giữ bản của tôi" giữ phần bạn nhập để đối chiếu rồi lưu lại (lần lưu sau dùng phiên bản mới).', '"Load latest" discards your input; "Keep mine" keeps it so you can save again.')}</div></div>
        <div class="modal-footer"><button class="btn-secondary" data-action="conflict-keep">${L('Giữ bản của tôi', 'Keep mine')}</button>
          <button class="btn-primary" data-action="conflict-reload">${L('Tải bản mới', 'Load latest')}</button></div>`;
    } else if (m.type === 'quote') {
      const q = m.quote;
      const total = q.sourceKind === 'RETAIL' ? q.payable : q.postPromotionAmount;
      body = `<div class="modal-header">🔄 ${L('Tổng đã thay đổi trước khi xác nhận', 'The total changed before confirmation')}</div>
        <div class="modal-body"><div class="alert warning"><span class="code">QUOTE_CHANGED</span> — ${L('Chưa tạo đơn/booking và chưa giữ gì. Đây là kết quả tính lại:', 'Nothing was created or held. Recalculated:')}</div>
          <dl class="kv"><dt>${L('Ưu đãi', 'Promotion')}</dt><dd>${q.program ? esc(q.program.name) + ' − ' + money(q.programDiscount) : '—'}</dd>
            <dt>${L('Điểm', 'Points')}</dt><dd>${num(q.points.points)} (− ${money(q.points.amount)})</dd>
            <dt>${L('Chính sách điểm', 'Points policy')}</dt><dd>rev ${q.policy.revision}</dd>
            <dt class="total">${q.sourceKind === 'RETAIL' ? L('Tổng mới', 'New total') : L('Phí sau ưu đãi mới', 'New fee')}</dt><dd class="total">${money(total)}</dd></dl></div>
        <div class="modal-footer"><button class="btn-secondary" data-action="close-modal">${L('Chọn lại', 'Choose again')}</button>
          <button class="btn-primary btn-accent" data-action="accept-new-quote">${L('Đồng ý tổng mới và xác nhận', 'Accept the new total')}</button></div>`;
    } else if (m.type === 'login-to-save') {
      body = `<div class="modal-header">🔐 ${L('Đăng nhập để lưu voucher', 'Sign in to save')}</div>
        <div class="modal-body"><div>${L('Khách xem được ưu đãi nhưng cần đăng nhập Member để lưu vào Kho Voucher.', 'Guests can read offers; saving needs a Member.')}</div></div>
        <div class="modal-footer"><button class="btn-secondary" data-action="close-modal">${L('Để sau', 'Later')}</button>
          <button class="btn-primary" data-role="MEMBER">${L('Đăng nhập (giả lập Member)', 'Sign in (simulated Member)')}</button></div>`;
    }
    return `<div class="modal-backdrop" ${['session', 'lost', 'conflict', 'quote'].includes(m.type) ? '' : 'data-backdrop="1"'}><div class="modal" data-modal="${m.type}">${body}</div></div>`;
  }

  // ---------------------------------------------------------------- events

  function formValues(form) {
    const fd = new FormData(form);
    const out = {};
    for (const [k, v] of fd.entries()) { if (k in out) out[k] = [].concat(out[k], v); else out[k] = v; }
    // Inputs that live outside the form element but point at it (form="…").
    document.querySelectorAll(`[form="${form.id}"]`).forEach((el) => {
      if (el.type === 'radio') { if (el.checked) out[el.name] = el.value; } else if (el.name) out[el.name] = el.value;
    });
    return out;
  }
  const list = (v) => (v == null ? [] : [].concat(v));

  function discountFromForm(v) {
    return v.discountKind === 'FIXED' ? { kind: 'FIXED', amount: v.amount } : { kind: 'PERCENT', percentage: v.percentage, maxDiscountAmount: v.maxDiscountAmount };
  }

  function syncCheckout() {
    const form = document.getElementById('checkout');
    if (!form) return;
    const v = formValues(form);
    ui.checkout.mode = v.mode || ui.checkout.mode;
    ui.checkout.code = v.code != null ? v.code : ui.checkout.code;
    ui.checkout.points = v.points != null ? v.points : ui.checkout.points;
    document.querySelectorAll('[data-line-qty]').forEach((el) => { ui.checkout.lines[Number(el.getAttribute('data-line-qty'))].quantity = Math.max(1, Math.min(99, Number(el.value) || 1)); });
  }
  function syncWorkshop() {
    const form = document.getElementById('workshop');
    if (!form) return;
    const v = formValues(form);
    ui.workshop.sessionId = Number(v.sessionId || ui.workshop.sessionId);
    ui.workshop.participants = v.participants || ui.workshop.participants;
    ui.workshop.code = v.code != null ? v.code : ui.workshop.code;
    ui.workshop.points = v.points != null ? v.points : ui.workshop.points;
  }

  function acceptRetail() {
    syncCheckout();
    const c = ui.checkout;
    const recompute = () => LP.retailQuote(ui.role, actor(), c.lines, c.shipping, { mode: c.mode, code: c.code }, c.points);
    const doAccept = (digest) => run(() => LP.accept(ui.role, actor(), digest, recompute),
      (b) => L('Đơn ' + b.sourceRef + ' đã được chấp nhận: ưu đãi và điểm đang được giữ tới khi FE-08 xác nhận thanh toán.', 'Order ' + b.sourceRef + ' accepted; benefits held.'),
      { quote: () => quoteFault(c.points), onQuote: (q) => doAccept(LP.digestOf(q)), onOk: () => { ui.checkout = defaultCheckout(); go('transactions'); } });
    doAccept(shownDigest);
  }
  function acceptWorkshop() {
    syncWorkshop();
    const w = ui.workshop;
    const recompute = () => LP.workshopQuote(ui.role, actor(), w.sessionId, w.participants, w.code, w.points);
    const doAccept = (digest) => run(() => LP.accept(ui.role, actor(), digest, recompute),
      (b) => L('Booking ' + b.sourceRef + ' đã được chấp nhận: voucher/điểm đang giữ, chốt khi FE-03 xác minh cọc.', 'Booking ' + b.sourceRef + ' accepted.'),
      { quote: () => quoteFault(w.points), onQuote: (q) => doAccept(LP.digestOf(q)), onOk: () => { ui.workshop = defaultWorkshop(); go('transactions'); } });
    doAccept(shownDigest);
  }
  /** "Changed before confirm": points taken by another order when points are used, otherwise a new policy revision. */
  function quoteFault(points) {
    if (Number(points || 0) > 0) LP.simulateOtherDeviceHold(actor()); else LP.simulateExternalPolicyEdit();
  }

  document.addEventListener('change', (e) => {
    const t = e.target;
    if (t.hasAttribute('data-sim-latency')) { ui.sim.latency = t.checked; saveUi(); render({ keepInputs: true }); return; }
    if (t.hasAttribute('data-add-line')) {
      syncCheckout();
      const id = Number(t.value);
      if (id) {
        const existing = ui.checkout.lines.find((l) => l.variantId === id);
        if (existing) existing.quantity = Math.min(99, existing.quantity + 1); else ui.checkout.lines.push({ variantId: id, quantity: 1 });
      }
      saveUi(); render(); return;
    }
    if (t.hasAttribute('data-line-qty') || (t.form && t.form.id === 'checkout') || t.getAttribute('form') === 'checkout') { syncCheckout(); saveUi(); render({ keepInputs: true }); return; }
    if ((t.form && t.form.id === 'workshop') || t.getAttribute('form') === 'workshop') { syncWorkshop(); saveUi(); render({ keepInputs: true }); return; }
    if (t.hasAttribute('data-rerender')) {
      const form = t.closest('form');
      if (form.getAttribute('data-form') === 'create') {
        const v = formValues(form);
        ui.createDraft = { kind: v.kind, scope: v.kind === 'AUTO_DISCOUNT' ? 'PRODUCT' : v.scope, discount: { kind: v.discountKind } };
      }
      if (form.getAttribute('data-form') === 'edit-program') ui.editDiscountKind = form.discountKind.value;
      render({ keepInputs: true });
    }
  });

  document.addEventListener('submit', (e) => {
    const form = e.target;
    const kind = form.getAttribute('data-form');
    if (!kind) return;
    e.preventDefault();
    if (ignoredWhileBusy()) return;
    const v = formValues(form);
    if (kind === 'checkout') { syncCheckout(); saveUi(); render({ keepInputs: true }); return; }
    if (kind === 'workshop') { syncWorkshop(); saveUi(); render({ keepInputs: true }); return; }
    if (kind === 'programs-filter') { ui.list = { scope: v.scope, status: v.status, page: 0 }; saveUi(); showLoading(); render(); return; }
    if (kind === 'create') {
      const input = {
        kind: v.kind, scope: v.scope, name: v.name, code: v.code, startAt: v.startAt, endAt: v.endAt, discount: discountFromForm(v),
        minimumEligibleAmount: v.minimumEligibleAmount, allowPoints: !!v.allowPoints, totalLimit: v.totalLimit, perMemberLimit: v.perMemberLimit,
        targets: v.scope === 'PRODUCT' ? { productIds: list(v.productIds), variantIds: list(v.variantIds) }
          : { packageIds: list(v.packageIds), branchIds: list(v.branchIds), workshopDates: (v.workshopDates || '').split(',') }
      };
      run(() => LP.createProgram(ui.role, actor(), input), L('Đã tạo chương trình.', 'Programme created.'),
        { deps: ['dependency'], onOk: (p) => { ui.createDraft = null; go('program', { programId: p.id }); } });
      return;
    }
    if (kind === 'edit-program') {
      // The version the form was loaded with, not the latest: an edit by someone else must surface as 409.
      const version = Number(form.version.value);
      run(() => LP.updateProgram(ui.role, actor(), ui.programId, version, {
        name: v.name, startAt: v.startAt, endAt: v.endAt, discount: discountFromForm(v), minimumEligibleAmount: v.minimumEligibleAmount,
        allowPoints: !!v.allowPoints, totalLimit: v.totalLimit, perMemberLimit: v.perMemberLimit
      }), (r) => (r.changed ? L('Đã lưu: tạo phiên bản điều kiện mới cho giao dịch mới.', 'Saved as a new revision.') : L('Không có thay đổi.', 'No change.')),
      { conflict: { simulate: () => LP.simulateExternalProgramEdit(ui.programId) } });
      return;
    }
    if (kind === 'policy') {
      const version = Number(form.version.value);
      run(() => LP.updatePolicy(ui.role, actor(), version, v),
        (r) => (r.changed ? L('Đã lưu chính sách mới; giao dịch đã chấp nhận giữ chính sách cũ.', 'New policy saved.') : L('Không có thay đổi.', 'No change.')),
        { conflict: { simulate: () => LP.simulateExternalPolicyEdit() } });
    }
  });

  document.addEventListener('click', (e) => {
    const el = e.target.closest('button, tr[data-program], [data-backdrop]');
    if (!el) return;
    if (ignoredWhileBusy()) { e.preventDefault(); return; }
    if (el.hasAttribute('data-backdrop') && e.target === el) { ui.modal = null; ui.error = null; render({ keepInputs: true }); return; }
    const data = (name) => el.getAttribute(name);

    if (data('data-sim-fault')) { ui.sim.fault = data('data-sim-fault'); saveUi(); render({ keepInputs: true }); return; }
    if (data('data-go')) { go(data('data-go')); return; }
    if (data('data-role')) { ui.role = data('data-role'); ui.error = null; ui.modal = null; saveUi(); render(); return; }
    if (data('data-lang')) { ui.lang = data('data-lang'); saveUi(); render({ keepInputs: true }); return; }
    if (el.hasAttribute('data-offers-scope')) { ui.offersScope = data('data-offers-scope'); saveUi(); openRead('offers'); return; }
    if (data('data-offer')) { go('offer', { offerId: Number(data('data-offer')) }); return; }
    if (data('data-program')) { go('program', { programId: Number(data('data-program')) }); return; }
    if (data('data-page')) {
      const [scope, n] = data('data-page').split(':');
      if (scope === 'programs') ui.list.page = Number(n); else ui.historyPage = Number(n);
      saveUi(); showLoading(); render(); return;
    }
    if (data('data-save')) {
      if (ui.role === 'GUEST') { ui.modal = { type: 'login-to-save' }; render(); return; }
      run(() => LP.saveVoucher(ui.role, actor(), data('data-save')),
        (r) => (r.already ? L('Mã đã có trong kho của bạn (lưu lặp không thêm quyền).', 'Already in your wallet.') : L('Đã lưu vào Kho Voucher. Lưu chưa giữ lượt hay điểm.', 'Saved. Nothing is held.')));
      return;
    }
    if (data('data-use-code') != null && el.hasAttribute('data-use-code')) {
      if (data('data-use-scope') === 'WORKSHOP') { ui.workshop.code = data('data-use-code'); go('workshop'); } else { ui.checkout.code = data('data-use-code'); ui.checkout.mode = 'voucher'; go('checkout'); }
      return;
    }
    if (el.hasAttribute('data-pick-code')) { syncCheckout(); ui.checkout.code = data('data-pick-code'); ui.checkout.mode = 'voucher'; saveUi(); render(); return; }
    if (el.hasAttribute('data-pick-ws-code')) { syncWorkshop(); ui.workshop.code = data('data-pick-ws-code'); saveUi(); render(); return; }
    if (el.hasAttribute('data-points')) {
      if (data('data-points-form') === 'checkout') { syncCheckout(); ui.checkout.points = data('data-points'); } else { syncWorkshop(); ui.workshop.points = data('data-points'); }
      saveUi(); render(); return;
    }
    if (el.hasAttribute('data-remove-line')) { syncCheckout(); ui.checkout.lines.splice(Number(data('data-remove-line')), 1); saveUi(); render(); return; }
    if (data('data-outcome')) {
      const [id, kind] = data('data-outcome').split(':');
      const texts = { PAID: L('FE-08 xác nhận đã thanh toán: đã chốt lượt/điểm và cộng điểm một lần.', 'Paid: settled and earned once.'),
        EXPIRED: L('Hết hạn chưa thanh toán: đã trả điểm và lượt đang giữ.', 'Expired: held benefits returned.'),
        DEPOSIT: L('Cọc đã xác minh: đã chốt voucher/điểm. Chưa cộng điểm mới.', 'Deposit verified: settled, no earn yet.'),
        COMPLETED_PAID: L('Hoàn thành và trả đủ: đã cộng điểm một lần.', 'Completed and fully paid: earned once.'),
        CANCELLED: L('Hủy hợp lệ: đã trả lại đúng phần đã giữ/đã dùng.', 'Valid cancel: benefits returned.') };
      run(() => LP.outcome(ui.role, actor(), id, kind), (r) => (r.changed ? texts[kind] : L('Kết quả này đã được xử lí trước đó — không cộng/trừ thêm.', 'Already processed — nothing changes.')));
      return;
    }

    const action = data('data-action');
    if (!action) return;
    const m = ui.modal;
    switch (action) {
      case 'reset':
        if (confirm(L('Khôi phục toàn bộ dữ liệu mẫu? Mọi thay đổi trong prototype sẽ mất.', 'Reset all sample data?'))) {
          LP.reset();
          ui.checkout = defaultCheckout();
          ui.workshop = defaultWorkshop();
          go('offers', { programId: null, createDraft: null });
          toast(L('Đã khôi phục dữ liệu mẫu.', 'Sample data restored.'));
        }
        break;
      case 'accept-retail': acceptRetail(); break;
      case 'accept-workshop': acceptWorkshop(); break;
      case 'accept-new-quote': { const q = m.quote; const retry = m.retry; ui.modal = null; render({ keepInputs: true }); retry(q); break; }
      case 'pause': case 'resume': {
        const p = LP.program(ui.programId);
        const version = p.version;
        run(() => LP.setStatus(ui.role, actor(), p.id, version, action === 'pause'),
          action === 'pause' ? L('Đã tạm dừng. Giao dịch đã chấp nhận giữ nguyên quyền lợi.', 'Paused; accepted transactions keep their benefits.')
            : L('Đã mở lại cùng chương trình và mã; không gia hạn, không reset lượt.', 'Resumed; no extension or reset.'),
          { conflict: { simulate: () => LP.simulateExternalProgramEdit(p.id) } });
        break;
      }
      case 'reload': showLoading(); render(); toast(L('Đã tải lại dữ liệu đã xác nhận.', 'Reloaded.')); break;
      case 'close-modal': ui.modal = null; ui.error = null; render({ keepInputs: true }); break;
      case 'toggle-sim': ui.simOpen = !ui.simOpen; saveUi(); render({ keepInputs: true }); break;
      case 'retry': if (lastRetry) lastRetry(); break;
      case 'retry-view': ui.unavailable = null; showLoading(); render(); break;
      case 'relogin':
        ui.modal = m.back || null;
        ui.error = null;
        render({ keepInputs: true });
        toast(L('Đã đăng nhập lại (giả lập). Dữ liệu bạn nhập vẫn còn — bấm lưu lại.', 'Signed in again. Save again.'));
        break;
      case 'check-receipt': {
        const lost = lostCommand;
        setBusy(true);
        wait().then(() => {
          setBusy(false);
          const found = LP.receipt(lost.key);
          if (!found) { ui.modal = { ...m, checked: 'NOT_FOUND' }; render({ keepInputs: true }); return; }
          ui.modal = null;
          ui.error = null;
          inputMemory = {};
          lostCommand = null;
          render();
          toast(L('Đã kiểm tra: lệnh đã được ghi lúc ' + date(found.at) + '. Không cần làm lại.', 'Checked: committed at ' + date(found.at) + '.'));
          if (lost.o.onOk) lost.o.onOk(found.result);
        });
        break;
      }
      case 'resend': {
        const lost = lostCommand;
        lostCommand = null;
        run(lost.action, lost.success, { ...lost.o, key: lost.key });
        break;
      }
      case 'conflict-reload':
        inputMemory = {};
        ui.error = null;
        ui.modal = null;
        render();
        toast(L('Đã tải bản mới nhất.', 'Latest version loaded.'));
        break;
      case 'conflict-keep': {
        ui.error = null;
        ui.modal = null;
        render({ keepInputs: true });
        const versionInput = document.querySelector('[data-form] [name="version"]');
        if (versionInput) {
          versionInput.value = ui.view === 'policy' ? LP.state.policyVersion : LP.program(ui.programId).version;
          inputMemory[inputKey(versionInput)] = versionInput.value;
        }
        toast(L('Đã giữ phần bạn nhập. Đối chiếu rồi bấm lưu lại.', 'Your input is kept. Compare, then save again.'));
        break;
      }
      default: break;
    }
  });

  /** Read views that depend on another feature can fail with 503 when that fault is armed. */
  function openRead(view) {
    if (takeFault(['dependency'])) { ui.unavailable = view; go(view); return; }
    ui.unavailable = null;
    go(view);
  }
  const plainGo = go;
  go = function (view, extra) {
    if (['offers', 'wallet'].includes(view) && ui.sim.fault === 'dependency' && !(extra && extra.skipFault)) {
      takeFault(['dependency']);
      ui.unavailable = view;
    } else if (ui.unavailable && ui.unavailable !== view) ui.unavailable = null;
    plainGo(view, extra);
  };

  // Another tab of the prototype saved: reload the "server" data but keep what this tab's user is typing.
  window.addEventListener('storage', (e) => {
    if (e.key !== LP.STATE_KEY) return;
    LP.load();
    render({ keepInputs: true });
    toast(L('Dữ liệu vừa thay đổi ở tab khác; màn hình đã cập nhật, phần bạn đang nhập vẫn giữ nguyên.', 'Data changed in another tab; your input is kept.'));
  });

  render();
})();
