/**
 * PPSWBS Prototype 002 — Product Management (FE-02): console UI.
 * Style: Operations Hub console (prototypes/style-3-dense-console). Rules live in domain.js (mock, localStorage).
 * Roles can be switched in the top bar to try permissions; this is not authentication.
 */
(function () {
  'use strict';

  const UI_KEY = 'ppswbs_prototype_002_ui_v1';
  const ACTORS = {
    GUEST: null,
    MEMBER: 'member.mai',
    STAFF: 'staff.lan',
    MANAGER: 'manager.huy',
    ADMIN: 'admin.an'
  };

  let ui = {
    lang: 'vi', role: 'STAFF', view: 'products', productId: null, tab: 'overview',
    list: { q: '', categoryId: '', published: '', page: 0 },
    pub: { q: '', categoryId: '', min: '', max: '', canBuy: false, sort: 'name-asc', page: 0 },
    publicId: null, selectedVariant: null, gallery: 0,
    importText: '', importFile: 'san-pham.xlsx', importBatchId: null,
    imageDraft: null, createDraft: null, error: null, modal: null
  };
  try {
    const saved = JSON.parse(localStorage.getItem(UI_KEY) || 'null');
    if (saved) ui = { ...ui, ...saved, modal: null, error: null };
  } catch (e) { /* default UI */ }

  PM.load();

  // ---------------------------------------------------------------- helpers

  const L = (vi, en) => (ui.lang === 'vi' ? vi : en);
  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const money = (v) => (v == null ? '—' : new Intl.NumberFormat('vi-VN').format(BigInt(v)) + ' ₫');
  const date = (iso) => (iso ? new Date(iso).toLocaleString(ui.lang === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'short', timeStyle: 'short' }) : '—');
  const actor = () => ACTORS[ui.role];
  const canManage = () => ui.role === 'STAFF' || ui.role === 'MANAGER';
  const isManager = () => ui.role === 'MANAGER';

  const ERRORS = {
    VALIDATION_ERROR: ['Dữ liệu chưa hợp lệ.', 'The request contains invalid values.'],
    CATALOGUE_ACCESS_DENIED: ['Bạn không có quyền thực hiện thao tác này.', 'You do not have permission for this action.'],
    PRODUCT_NOT_FOUND: ['Không tìm thấy sản phẩm.', 'The product was not found.'],
    VARIANT_NOT_FOUND: ['Không tìm thấy size.', 'The size was not found.'],
    CATEGORY_NOT_FOUND: ['Danh mục không tồn tại hoặc đã ngừng hoạt động.', 'The category was not found or is inactive.'],
    MEDIA_NOT_FOUND: ['Ảnh không dùng được (không phải ảnh bạn tải lên hoặc đang bị xóa).', 'The image cannot be used.'],
    PROPOSAL_NOT_FOUND: ['Không tìm thấy đề xuất giá.', 'The price proposal was not found.'],
    STALE_VERSION: ['Dữ liệu đã thay đổi. Hãy tải lại rồi thử lại.', 'The data has changed. Reload and try again.'],
    DUPLICATE_CATEGORY_NAME: ['Tên danh mục đã tồn tại (kể cả danh mục đã ngừng).', 'A category with this name already exists.'],
    PENDING_PRICE_PROPOSAL_EXISTS: ['Size này đang có đề xuất giá chờ duyệt.', 'This size already has a pending price proposal.'],
    REVIEW_NOT_CURRENT: ['Hồ sơ duyệt không còn hiện hành.', 'The review is no longer current.'],
    PRICE_NOT_APPROVED: ['Giá chưa được duyệt.', 'The price has not been approved.'],
    PRICE_APPLICATION_STALE: ['Đã có giá mới hơn được áp dụng.', 'A newer price has already been applied.'],
    PUBLICATION_PREREQUISITES_MISSING: ['Sản phẩm chưa đủ điều kiện.', 'The product does not meet the conditions.'],
    PRODUCT_DELETED: ['Sản phẩm đã bị xóa.', 'The product has been deleted.'],
    IMPORT_FILE_INVALID: ['Dữ liệu không đúng mẫu (hàng tiêu đề phải đúng 7 cột như mẫu).', 'The data does not match the template.'],
    IMPORT_ROW_LIMIT_EXCEEDED: ['Quá 500 dòng dữ liệu.', 'More than 500 data rows.'],
    IMPORT_HAS_ERRORS: ['Lô có lỗi, không thể xác nhận.', 'The batch has errors.'],
    IMPORT_BATCH_EXPIRED: ['Bản xem trước đã hết hạn (60 phút). Hãy kiểm tra lại file.', 'The preview has expired.'],
    IMPORT_PREVIEW_STALE: ['Dữ liệu đã thay đổi sau bước kiểm tra. Hãy kiểm tra lại file.', 'The data changed since the preview.'],
    IMPORT_BATCH_NOT_FOUND: ['Không tìm thấy lô import.', 'The import batch was not found.'],
    INVALID_SORT: ['Kiểu sắp xếp không được hỗ trợ.', 'The sort option is not supported.']
  };
  const FIELD_HINTS = {
    categoryId: ['Danh mục phải đang hoạt động', 'The category must be active'],
    images: ['Cần ít nhất một ảnh dùng được', 'At least one usable image'],
    review: ['Nội dung hiện tại phải được Manager duyệt', 'The current content must be approved'],
    name: ['Tên 1–200 ký tự', 'Name 1–200 characters'],
    description: ['Mô tả bắt buộc, tối đa 5.000 ký tự', 'Description required, max 5,000'],
    reason: ['Lý do 1–2.000 ký tự', 'Reason 1–2,000 characters'],
    proposedPrice: ['Giá là số nguyên VND dương, không dấu phân cách', 'Positive whole VND'],
    approvedPrice: ['Giá là số nguyên VND dương', 'Positive whole VND'],
    quantity: ['Số lượng là số nguyên không âm', 'Nonnegative whole number'],
    minimumPrice: ['Giá từ phải là số nguyên ≥ 0 và không lớn hơn giá đến', 'Minimum must be ≥ 0 and ≤ maximum'],
    maximumPrice: ['Giá đến phải là số nguyên ≥ 0', 'Maximum must be ≥ 0'],
    q: ['Từ khóa tối đa 200 ký tự', 'Keyword max 200 characters'],
    variants: ['Cần 1–25 size, không trùng', '1–25 distinct sizes']
  };
  function fieldText(f) {
    const key = Object.keys(FIELD_HINTS).find((k) => f.field === k || f.field.startsWith(k + '['));
    if (f.field.includes('appliedPrice')) return L('Size ' + f.field.match(/\[(.*?)\]/)[1] + ' chưa có giá đã duyệt và đã áp dụng', 'Size ' + f.field + ' needs an applied approved price');
    if (f.field.startsWith('variants[') && f.message === 'price') return L('Size ' + f.field.match(/\[(.*?)\]/)[1] + ' chưa có giá đề xuất', 'Size needs a proposed price');
    if (f.field.startsWith('prices[')) return L('Giá cuối của ' + f.field.match(/\[(.*?)\]/)[1] + ' không hợp lệ', 'Invalid final price');
    if (f.field.startsWith('images[')) return L('Ảnh ' + f.field + ': mô tả ảnh 1–200 ký tự, vị trí không trùng', 'Image: alt 1–200, unique position');
    if (f.field.startsWith('variants[') && f.field.endsWith('.size')) return L('Size phải từ 5 đến 29 và không trùng', 'Size 5–29, distinct');
    if (f.field.startsWith('variants[') && f.field.endsWith('.proposedPrice')) return FIELD_HINTS.proposedPrice[ui.lang === 'vi' ? 0 : 1];
    if (f.field.startsWith('variants[') && f.field.endsWith('.quantity')) return FIELD_HINTS.quantity[ui.lang === 'vi' ? 0 : 1];
    return key ? FIELD_HINTS[key][ui.lang === 'vi' ? 0 : 1] : f.field + ': ' + f.message;
  }

  function saveUi() {
    try {
      const { modal, error, ...rest } = ui;
      localStorage.setItem(UI_KEY, JSON.stringify(rest));
    } catch (e) { /* ignore */ }
  }

  function toast(message, isError) {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    const el = document.createElement('div');
    el.className = 'toast' + (isError ? ' error' : '');
    el.textContent = message;
    container.appendChild(el);
    setTimeout(() => el.remove(), 3200);
  }

  /** Runs a command; on a domain error keeps the screen data and shows the coded message. */
  function run(action, success) {
    try {
      const result = action();
      ui.error = null;
      PM.save();
      if (success) {
        const message = typeof success === 'function' ? success(result) : success;
        if (message) setTimeout(() => toast(message), 0);
      }
      return { ok: true, result };
    } catch (e) {
      if (!(e instanceof PM.DomainError)) throw e;
      ui.error = { code: e.code, fieldErrors: e.fieldErrors };
      setTimeout(() => toast((ERRORS[e.code] || [e.code, e.code])[ui.lang === 'vi' ? 0 : 1], true), 0);
      return { ok: false, error: e };
    } finally {
      render();
    }
  }

  function errorBox() {
    if (!ui.error) return '';
    const [vi, en] = ERRORS[ui.error.code] || [ui.error.code, ui.error.code];
    return `<div class="alert error"><span class="code">${esc(ui.error.code)}</span> — ${esc(L(vi, en))}
      ${ui.error.fieldErrors.length ? `<ul>${ui.error.fieldErrors.map((f) => `<li>${esc(fieldText(f))}</li>`).join('')}</ul>` : ''}</div>`;
  }

  function go(view, extra) {
    ui = { ...ui, view, error: null, modal: null, ...(extra || {}) };
    saveUi();
    render();
    window.scrollTo(0, 0);
  }

  // ---------------------------------------------------------------- badges

  function reviewBadge(state, effective) {
    const map = { DRAFT: ['Nháp', 'Draft', ''], PENDING: ['Chờ duyệt', 'Pending', 'warning'], APPROVED: ['Đã duyệt', 'Approved', 'success'], REJECTED: ['Bị từ chối', 'Rejected', 'error'] };
    const [vi, en, cls] = map[state];
    return `<span class="badge ${cls}">${L(vi, en)}${effective === false ? ' · ' + L('hết hiệu lực', 'ineffective') : ''}</span>`;
  }
  function productStatus(p) {
    if (p.deletedAt) return `<span class="badge error">${L('Đã xóa', 'Deleted')}</span>`;
    if (p.published) return `<span class="badge success">${L('Đang công khai', 'Published')}</span>`;
    if (p.firstPublishedAt) return `<span class="badge purple">${L('Đã ẩn', 'Hidden')}</span>`;
    return `<span class="badge">${L('Chưa công khai', 'Unpublished')}</span>`;
  }

  // ---------------------------------------------------------------- shell

  function render() {
    const root = document.getElementById('app');
    const pendingCount = PM.state.products.filter((p) => !p.deletedAt
      && (PM.currentReview(p).state === 'PENDING' || p.variants.some((v) => v.pendingProposalId && !(PM.proposal(p, v.pendingProposalId) || {}).reviewId))).length;
    const menu = (view, icon, label, count) => `<li><button class="sidebar-btn ${ui.view === view || (view === 'products' && ['product', 'create'].includes(ui.view)) || (view === 'catalogue' && ui.view === 'publicDetail') ? 'active' : ''}" data-go="${view}">
      <span>${icon}</span><span>${label}</span>${count ? `<span class="count">${count}</span>` : ''}</button></li>`;
    const roles = [['GUEST', 'Khách', 'Guest'], ['MEMBER', 'Thành viên', 'Member'], ['STAFF', 'Staff', 'Staff'], ['MANAGER', 'Manager', 'Manager'], ['ADMIN', 'Admin', 'Admin']];
    root.innerHTML = `
      <aside class="console-sidebar">
        <div class="sidebar-header">
          <a class="sidebar-brand" href="../index.html"><span>MLAND</span><span class="pill">FE-02</span></a>
          <a class="hub-link" href="../index.html">← Hub</a>
        </div>
        <ul class="sidebar-menu">
          <li class="sidebar-item-header">${L('Catalogue công khai', 'Public catalogue')}</li>
          ${menu('catalogue', '💍', L('Sản phẩm đang bán', 'Products'))}
          <li class="sidebar-item-header" style="margin-top: 14px;">${L('Quản trị sản phẩm', 'Catalogue management')}</li>
          ${menu('products', '📋', L('Danh sách sản phẩm', 'Products'))}
          ${menu('approvals', '✅', L('Chờ duyệt', 'Pending approval'), pendingCount)}
          ${menu('categories', '🗂️', L('Danh mục', 'Categories'))}
          ${menu('import', '📥', L('Import Excel', 'Excel import'))}
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
            <div class="control-pill-group">
              <button class="control-pill-btn ${ui.lang === 'vi' ? 'active' : ''}" data-lang="vi">VI</button>
              <button class="control-pill-btn ${ui.lang === 'en' ? 'active' : ''}" data-lang="en">EN</button>
            </div>
          </div>
        </header>
        <main class="console-content" id="content">${content()}</main>
      </div>
      ${ui.modal ? modal() : ''}
      <div class="toast-container" id="toastContainer"></div>`;
  }

  function content() {
    const management = ['products', 'product', 'create', 'approvals', 'categories', 'import'];
    if (management.includes(ui.view) && !canManage()) return denied();
    switch (ui.view) {
      case 'catalogue': return publicCatalogue();
      case 'publicDetail': return publicDetail();
      case 'products': return productList();
      case 'create': return createProduct();
      case 'product': return productDetail();
      case 'approvals': return approvals();
      case 'categories': return categoriesView();
      case 'import': return importView();
      default: return publicCatalogue();
    }
  }

  function denied() {
    const guest = ui.role === 'GUEST';
    return `<div class="panel"><div class="empty-state"><div class="icon">${guest ? '🔐' : '⛔'}</div>
      <strong>${guest ? '401 — ' + L('Cần đăng nhập', 'Sign-in required') : '403 — CATALOGUE_ACCESS_DENIED'}</strong>
      <div>${guest ? L('Màn quản trị chỉ dành cho Staff/Manager đã đăng nhập.', 'Management is for signed-in Staff/Manager.')
        : L('Vai trò ' + ui.role + ' không có quyền quản trị catalogue. Chỉ Staff và Manager đang hoạt động được dùng.', 'Role ' + ui.role + ' cannot manage the catalogue.')}</div>
      <button class="btn-secondary" data-role="STAFF">${L('Chuyển sang Staff', 'Switch to Staff')}</button></div></div>`;
  }

  // ---------------------------------------------------------------- management list

  function productList() {
    const f = ui.list;
    const result = PM.managementList(f);
    const cats = PM.state.categories;
    return `
      <div class="console-header-block">
        <div><div class="console-title">📋 ${L('Sản phẩm', 'Products')}</div>
          <div class="console-subtitle">${L('Tìm theo tên, mã sản phẩm (123 hoặc SP000123) hoặc đúng SKU. 20 sản phẩm/trang, mới nhất trước.', 'Search by name, product number or exact SKU. 20 per page, newest first.')}</div></div>
        <div class="btn-row"><button class="btn-primary btn-accent" data-go="create">＋ ${L('Tạo sản phẩm', 'Create product')}</button></div>
      </div>
      <div class="data-table-container">
        <form class="data-table-toolbar" data-form="list-filter">
          <input class="search-input" name="q" value="${esc(f.q)}" placeholder="${L('Tên, SP000123 hoặc SKU…', 'Name, SP000123 or SKU…')}">
          <select class="toolbar-select" name="categoryId"><option value="">${L('Mọi danh mục', 'All categories')}</option>
            ${cats.map((c) => `<option value="${c.id}" ${String(c.id) === String(f.categoryId) ? 'selected' : ''}>${esc(c.name)}${c.active ? '' : ' (' + L('ngừng', 'inactive') + ')'}</option>`).join('')}</select>
          <select class="toolbar-select" name="published">
            <option value="">${L('Mọi trạng thái công khai', 'Any publication')}</option>
            <option value="true" ${f.published === 'true' ? 'selected' : ''}>${L('Đang công khai', 'Published')}</option>
            <option value="false" ${f.published === 'false' ? 'selected' : ''}>${L('Chưa/không công khai', 'Not published')}</option></select>
          <button class="btn-secondary" type="submit">${L('Lọc', 'Filter')}</button>
          <button class="btn-link" type="button" data-action="clear-list">${L('Xóa lọc', 'Clear')}</button>
        </form>
        ${result.items.length ? `<table class="console-table"><thead><tr>
            <th>${L('Mã', 'No.')}</th><th>${L('Tên sản phẩm', 'Name')}</th><th>${L('Danh mục', 'Category')}</th><th>${L('Size / SKU', 'Sizes / SKU')}</th>
            <th>${L('Hồ sơ duyệt', 'Review')}</th><th>${L('Công khai', 'Publication')}</th><th class="num">${L('Giá đang bán', 'Applied price')}</th></tr></thead>
          <tbody>${result.items.map((p) => {
            const c = PM.category(p.categoryId);
            const prices = p.variants.filter((v) => v.appliedPrice).map((v) => BigInt(v.appliedPrice));
            const low = prices.length ? prices.reduce((a, b) => (b < a ? b : a)) : null;
            const high = prices.length ? prices.reduce((a, b) => (b > a ? b : a)) : null;
            const priceText = low == null ? '—' : money(low) + (high !== low ? ' – ' + money(high) : '');
            const review = PM.currentReview(p);
            return `<tr class="clickable" data-open="${p.id}">
              <td class="code-mono">SP${String(p.id).padStart(6, '0')}</td>
              <td><strong>${esc(p.name)}</strong>${p.source === 'IMPORT' ? ` <span class="badge info">Import</span>` : ''}</td>
              <td>${esc(c.name)}${c.active ? '' : ` <span class="badge warning">${L('ngừng', 'inactive')}</span>`}</td>
              <td>${p.variants.map((v) => `<span class="code-mono" title="${esc(v.sku)}">${v.size}</span>`).join(' · ')}</td>
              <td>${p.firstPublishedAt ? `<span class="muted">${L('Đã qua duyệt đầu', 'Initial review done')}</span>` : reviewBadge(review.state, review.effective)}</td>
              <td>${productStatus(p)}</td>
              <td class="num">${priceText}</td></tr>`;
          }).join('')}</tbody></table>${pagination(result, 'list')}`
        : `<div class="empty-state"><div class="icon">🔍</div><div>${L('Không có sản phẩm khớp bộ lọc.', 'No product matches the filters.')}</div></div>`}
      </div>`;
  }

  function pagination(result, scope) {
    if (result.pages <= 1) return `<div class="pagination muted">${result.total} ${L('kết quả', 'results')}</div>`;
    return `<div class="pagination"><span class="muted">${result.total} ${L('kết quả', 'results')} · ${L('trang', 'page')} ${result.page + 1}/${result.pages}</span>
      <button class="btn-secondary" data-page="${scope}:${result.page - 1}" ${result.page === 0 ? 'disabled' : ''}>‹</button>
      <button class="btn-secondary" data-page="${scope}:${result.page + 1}" ${result.page + 1 >= result.pages ? 'disabled' : ''}>›</button></div>`;
  }

  // ---------------------------------------------------------------- create

  function createProduct() {
    const d = ui.createDraft || (ui.createDraft = { name: '', description: '', categoryId: '', variants: [{ size: 10, price: '', qty: '1' }], images: [] });
    const active = PM.state.categories.filter((c) => c.active);
    return `
      <div class="breadcrumb"><button data-go="products">${L('Sản phẩm', 'Products')}</button> / ${L('Tạo mới', 'Create')}</div>
      <div class="console-header-block"><div><div class="console-title">＋ ${L('Tạo sản phẩm', 'Create product')}</div>
        <div class="console-subtitle">${L('Sản phẩm mới là bản nháp chưa công khai. Không nhập giá bán trực tiếp: chỉ nhập giá đề xuất, Manager duyệt rồi mới áp dụng. Size và SKU cố định sau khi lưu.', 'A new product is an unpublished draft. Only proposed prices; sizes and SKUs are fixed after saving.')}</div></div></div>
      ${errorBox()}
      <form class="panel" data-form="create" style="display: flex; flex-direction: column; gap: 14px;">
        <div class="form-grid">
          <div class="field"><label>${L('Tên sản phẩm *', 'Name *')}</label><input name="name" maxlength="400" value="${esc(d.name)}"></div>
          <div class="field"><label>${L('Danh mục *', 'Category *')}</label><select name="categoryId"><option value="">${L('— Chọn danh mục đang hoạt động —', '— Active category —')}</option>
            ${active.map((c) => `<option value="${c.id}" ${String(c.id) === String(d.categoryId) ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select></div>
          <div class="field field-full"><label>${L('Mô tả *', 'Description *')}</label><textarea name="description">${esc(d.description)}</textarea></div>
        </div>
        <div class="panel-title">${L('Size, giá đề xuất và số lượng', 'Sizes, proposed price and quantity')}
          <button type="button" class="btn-secondary" data-action="create-add-size" ${d.variants.length >= 25 ? 'disabled' : ''}>＋ ${L('Thêm size', 'Add size')}</button></div>
        <table class="console-table"><thead><tr><th>Size</th><th>${L('Giá đề xuất (VND)', 'Proposed price (VND)')}</th><th>${L('Số lượng còn bán', 'Remaining quantity')}</th><th></th></tr></thead>
          <tbody>${d.variants.map((v, i) => `<tr>
            <td><select class="inline-input" name="size-${i}">${Array.from({ length: 25 }, (_, k) => k + 5).map((s) => `<option ${s === Number(v.size) ? 'selected' : ''}>${s}</option>`).join('')}</select></td>
            <td><input class="inline-input" name="price-${i}" value="${esc(v.price)}" placeholder="6500000" inputmode="numeric"></td>
            <td><input class="inline-input" name="qty-${i}" value="${esc(v.qty)}" inputmode="numeric"></td>
            <td><button type="button" class="btn-link" data-action="create-remove-size" data-index="${i}" ${d.variants.length <= 1 ? 'disabled' : ''}>${L('Bỏ', 'Remove')}</button></td></tr>`).join('')}</tbody></table>
        <div class="panel-title">${L('Ảnh (không bắt buộc khi tạo, cần ít nhất 1 ảnh để gửi duyệt)', 'Images (optional now, one is needed to submit)')}
          <span class="btn-row"><label class="btn-secondary">📤 ${L('Tải ảnh', 'Upload')}<input type="file" accept="image/jpeg,image/png,image/webp" data-upload="create" hidden></label>
          <button type="button" class="btn-secondary" data-action="sample-image" data-target="create">🖼️ ${L('Ảnh mẫu', 'Sample image')}</button></span></div>
        ${imageEditor(d.images, 'create')}
        <div class="btn-row"><button class="btn-primary" type="submit">${L('Lưu bản nháp', 'Save draft')}</button>
          <button class="btn-secondary" type="button" data-go="products">${L('Hủy', 'Cancel')}</button></div>
      </form>`;
  }

  function imageEditor(images, scope, product) {
    if (!images.length) return `<div class="empty-state" style="padding: 18px;"><div>${L('Chưa có ảnh.', 'No image yet.')}</div></div>`;
    return `<div class="image-grid">${images.map((img, i) => {
      const media = PM.state.media[img.mediaId];
      const unavailable = media && media.state !== 'READY';
      return `<div class="image-card ${unavailable ? 'unavailable' : ''}"><img src="${media ? media.src : ''}" alt="${esc(img.altText)}">
        <div class="image-meta">
          <span class="muted">${L('Vị trí', 'Position')} ${i}${unavailable ? ' · <span class="badge error">' + L('Không đọc được', 'Unavailable') + '</span>' : ''}</span>
          <input class="inline-input" data-alt="${scope}:${i}" value="${esc(img.altText)}" placeholder="${L('Mô tả ảnh *', 'Alt text *')}">
          <div class="btn-row">
            <button type="button" class="btn-link" data-move="${scope}:${i}:-1" ${i === 0 ? 'disabled' : ''}>←</button>
            <button type="button" class="btn-link" data-move="${scope}:${i}:1" ${i === images.length - 1 ? 'disabled' : ''}>→</button>
            <button type="button" class="btn-link" data-remove-image="${scope}:${i}">${L('Gỡ', 'Remove')}</button>
            ${product ? `<button type="button" class="btn-link" data-toggle-media="${img.mediaId}" title="${L('Giả lập ảnh lỗi phía Cloudinary', 'Simulate provider failure')}">${unavailable ? L('Khôi phục', 'Restore') : L('Giả lập lỗi', 'Break')}</button>` : ''}
          </div></div></div>`;
    }).join('')}</div>`;
  }

  // ---------------------------------------------------------------- product detail

  function productDetail() {
    const p = PM.product(ui.productId);
    if (!p) return `<div class="panel"><div class="empty-state">${L('Không tìm thấy sản phẩm.', 'Product not found.')}</div></div>`;
    const c = PM.category(p.categoryId);
    const tabs = [['overview', L('Tổng quan', 'Overview')], ['content', L('Nội dung', 'Content')], ['images', L('Ảnh', 'Images') + ` (${p.images.length})`],
      ['sizes', L('Size, giá & lượng bán', 'Sizes, prices & quantity')], ['review', L('Hồ sơ duyệt', 'Review')], ['history', L('Lịch sử', 'History')]];
    return `
      <div class="breadcrumb"><button data-go="products">${L('Sản phẩm', 'Products')}</button> / SP${String(p.id).padStart(6, '0')}</div>
      <div class="console-header-block">
        <div><div class="console-title">${esc(p.name)}</div>
          <div class="status-strip" style="margin-top: 6px;">${productStatus(p)}
            ${p.firstPublishedAt ? '' : reviewBadge(PM.currentReview(p).state, PM.currentReview(p).effective)}
            <span class="badge">${esc(c.name)}${c.active ? '' : ' · ' + L('ngừng', 'inactive')}</span>
            <span class="muted">v${p.version} · ${L('nội dung', 'content')} v${p.contentVersion}</span></div></div>
        ${p.deletedAt ? '' : `<div class="btn-row">
          ${p.published ? `<button class="btn-secondary" data-action="unpublish">🙈 ${L('Ẩn khỏi catalogue', 'Hide')}</button>`
            : `<button class="btn-primary btn-accent" data-action="publish">🚀 ${L(p.firstPublishedAt ? 'Công khai lại' : 'Công khai', p.firstPublishedAt ? 'Republish' : 'Publish')}</button>`}
          <button class="btn-danger" data-action="ask-delete">🗑️ ${L('Xóa', 'Delete')}</button></div>`}
      </div>
      ${p.deletedAt ? `<div class="alert error">${L('Sản phẩm đã bị xóa mềm lúc', 'Soft-deleted at')} ${date(p.deletedAt)} ${L('bởi', 'by')} ${esc(p.deletedBy)}. ${L('Không thể sửa, duyệt, áp dụng giá, công khai hay mở bán lại. Không có khôi phục.', 'No further changes; no restore.')}</div>` : ''}
      ${errorBox()}
      <div class="tabs">${tabs.map(([id, label]) => `<button class="tab-btn ${ui.tab === id ? 'active' : ''}" data-tab="${id}">${label}</button>`).join('')}</div>
      ${{ overview, content: contentTab, images: imagesTab, sizes: sizesTab, review: reviewTab, history: historyTab }[ui.tab](p)}`;
  }

  function overview(p) {
    const missing = p.deletedAt ? [] : PM.publicationMissing(p);
    const eligible = PM.publicEligible(p);
    return `<div class="form-grid">
      <div class="panel"><div class="panel-title">${L('Điều kiện công khai', 'Publication conditions')}</div>
        ${p.deletedAt ? `<div class="muted">—</div>` : missing.length ? `<div class="alert warning">${L('Còn thiếu:', 'Missing:')}<ul>${missing.map((f) => `<li>${esc(fieldText(f))}</li>`).join('')}</ul></div>`
          : `<div class="alert success">${L('Đủ điều kiện công khai.', 'Ready to publish.')}</div>`}
        <div class="muted" style="margin-top: 8px;">${L('Hiển thị trên catalogue công khai:', 'Visible in public catalogue:')} <strong>${eligible ? L('Có', 'Yes') : L('Không', 'No')}</strong></div>
        <div class="muted">${L('Size số lượng 0 không chặn công khai.', 'A size with quantity 0 does not block publication.')}</div></div>
      <div class="panel"><div class="panel-title">${L('Thông tin', 'Details')}</div><dl class="kv">
        <dt>${L('Tạo lúc', 'Created')}</dt><dd>${date(p.createdAt)}${p.source === 'IMPORT' ? ' · Import' : ''}</dd>
        <dt>${L('Công khai lần đầu', 'First published')}</dt><dd>${date(p.firstPublishedAt)}</dd>
        <dt>${L('Size', 'Sizes')}</dt><dd>${p.variants.map((v) => `<span class="code-mono">${esc(v.sku)}</span>`).join('<br>')}</dd></dl></div></div>`;
  }

  function contentTab(p) {
    const cats = PM.state.categories.filter((c) => c.active || c.id === p.categoryId);
    return `<form class="panel" data-form="content" style="display: flex; flex-direction: column; gap: 12px;">
      ${p.firstPublishedAt ? `<div class="alert info">${L('Sản phẩm đã từng công khai: sửa nội dung được lưu thẳng, không cần duyệt lại.', 'Already published once: edits are saved directly.')}</div>`
        : `<div class="alert warning">${L('Chưa công khai lần đầu: sửa nội dung sẽ đưa hồ sơ duyệt về nháp (revision mới) và phải gửi duyệt lại.', 'Before first publication an edit returns the review to draft.')}</div>`}
      <div class="form-grid">
        <div class="field"><label>${L('Tên sản phẩm *', 'Name *')}</label><input name="name" value="${esc(p.name)}" ${p.deletedAt ? 'disabled' : ''}></div>
        <div class="field"><label>${L('Danh mục *', 'Category *')}</label><select name="categoryId" ${p.deletedAt ? 'disabled' : ''}>
          ${cats.map((c) => `<option value="${c.id}" ${c.id === p.categoryId ? 'selected' : ''}>${esc(c.name)}${c.active ? '' : ' (' + L('ngừng', 'inactive') + ')'}</option>`).join('')}</select></div>
        <div class="field field-full"><label>${L('Mô tả *', 'Description *')}</label><textarea name="description" ${p.deletedAt ? 'disabled' : ''}>${esc(p.description)}</textarea></div>
      </div>
      <div class="muted">${L('Size, SKU, giá và số lượng không sửa ở đây.', 'Sizes, SKUs, prices and quantities are not edited here.')}</div>
      <div class="btn-row"><button class="btn-primary" type="submit" ${p.deletedAt ? 'disabled' : ''}>${L('Lưu nội dung', 'Save content')}</button></div></form>`;
  }

  function imagesTab(p) {
    if (!ui.imageDraft || ui.imageDraft.productId !== p.id) ui.imageDraft = { productId: p.id, images: p.images.map((img) => ({ ...img })) };
    const draft = ui.imageDraft.images;
    const dirty = JSON.stringify(draft.map((d, i) => ({ ...d, position: i }))) !== JSON.stringify(p.images);
    return `<div class="panel" style="display: flex; flex-direction: column; gap: 12px;">
      <div class="panel-title">${L('Ảnh sản phẩm (tối đa 10, JPEG/PNG/WebP ≤ 5MB)', 'Images (max 10, JPEG/PNG/WebP ≤ 5MB)')}
        <span class="btn-row"><label class="btn-secondary ${p.deletedAt || draft.length >= 10 ? 'disabled' : ''}">📤 ${L('Tải ảnh', 'Upload')}<input type="file" accept="image/jpeg,image/png,image/webp" data-upload="product" hidden ${p.deletedAt ? 'disabled' : ''}></label>
        <button class="btn-secondary" data-action="sample-image" data-target="product" ${p.deletedAt || draft.length >= 10 ? 'disabled' : ''}>🖼️ ${L('Ảnh mẫu', 'Sample image')}</button></span></div>
      <div class="muted">${L('Gỡ ảnh không xóa file; ảnh cũ vẫn được giữ cho lịch sử. "Giả lập lỗi" mô phỏng ảnh không đọc được từ Cloudinary để thử FR-036.', 'Removing does not delete the file. "Break" simulates an unreadable provider object.')}</div>
      ${imageEditor(draft, 'product', p)}
      <div class="btn-row"><button class="btn-primary" data-action="save-images" ${!dirty || p.deletedAt ? 'disabled' : ''}>${L('Lưu thay đổi ảnh', 'Save images')}</button>
        <button class="btn-secondary" data-action="reset-images" ${!dirty ? 'disabled' : ''}>${L('Bỏ thay đổi', 'Discard')}</button></div></div>`;
  }

  function proposalBadge(x, v) {
    if (x.applied && v.appliedProposalId === x.id) return `<span class="badge success">${L('Đang áp dụng', 'Applied')}</span>`;
    return reviewBadge(x.state, x.effective);
  }

  function sizesTab(p) {
    const published = !!p.firstPublishedAt;
    const live = !p.deletedAt;
    return `<div class="data-table-container"><div class="data-table-toolbar"><span class="muted">
      ${published ? L('Đổi giá: tạo đề xuất → gửi duyệt → Manager duyệt → áp dụng. Giá đang bán giữ nguyên trong lúc chờ.', 'Price change: propose → submit → Manager decides → apply.')
        : L('Trước lần công khai đầu, giá đề xuất nằm trong hồ sơ duyệt; sửa giá sẽ đưa hồ sơ về nháp.', 'Before first publication prices belong to the review.')}
      ${L('Prototype không có giữ hàng FE-08 nên số đang giữ luôn là 0.', 'No FE-08 holds in the prototype (held = 0).')}</span>
      ${live ? `<button class="btn-secondary" data-action="apply-all">${L('Áp dụng mọi giá đã duyệt', 'Apply all approved prices')}</button>` : ''}</div>
      <table class="console-table"><thead><tr><th>Size / SKU</th><th>${L('Còn bán', 'Remaining')}</th><th>${L('Bán', 'Sale')}</th><th class="num">${L('Giá đang bán', 'Applied')}</th><th>${L('Đề xuất giá (mới nhất trước)', 'Proposals (latest first)')}</th><th>${L('Đề xuất mới', 'New proposal')}</th></tr></thead>
      <tbody>${p.variants.map((v) => {
        const proposals = p.proposals.filter((x) => x.variantId === v.id).sort((a, b) => b.revision - a.revision || b.id - a.id);
        return `<tr>
          <td><strong>${v.size}</strong><br><span class="code-mono">${esc(v.sku)}</span></td>
          <td><form class="btn-row" data-form="quantity" data-variant="${v.id}"><input class="inline-input" style="width: 70px;" name="qty" value="${esc(v.remaining)}" ${live ? '' : 'disabled'}>
            <button class="btn-link" type="submit" ${live ? '' : 'disabled'}>${L('Lưu', 'Save')}</button></form>${v.remaining === '0' ? `<span class="badge warning">${L('Hết hàng', 'Sold out')}</span>` : ''}</td>
          <td><button class="btn-secondary" data-sale="${v.id}:${!v.saleEnabled}" ${live ? '' : 'disabled'}>${v.saleEnabled ? '🟢 ' + L('Đang bán', 'On sale') : '⏸️ ' + L('Ngừng bán', 'Stopped')}</button></td>
          <td class="num"><strong>${money(v.appliedPrice)}</strong><br><span class="muted">seq ${v.appliedSequence}</span></td>
          <td>${proposals.slice(0, 4).map((x) => `<div style="display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-bottom: 4px;">
              <span class="muted">r${x.revision}${x.reviewId ? ' · ' + L('hồ sơ', 'review') : ''}</span>
              <span class="${x.effective ? '' : 'strike'}">${money(x.proposed)}</span>${x.approved ? ` → <strong>${money(x.approved)}</strong>` : ''}
              ${proposalBadge(x, v)}${x.reason ? `<span class="muted" title="${esc(x.reason)}">“${esc(x.reason.slice(0, 30))}”</span>` : ''}
              ${live && x.effective && x.state === 'APPROVED' && !x.applied ? `<button class="btn-link" data-apply="${x.id}">${L('Áp dụng', 'Apply')}</button>` : ''}
              ${live && x.effective && !x.reviewId && x.state === 'DRAFT' ? `<button class="btn-link" data-submit-proposal="${x.id}">${L('Gửi duyệt', 'Submit')}</button>` : ''}
              ${live && isManager() && x.effective && !x.reviewId && x.state === 'PENDING' ? `<button class="btn-link" data-decide-proposal="${x.id}">${L('Quyết định', 'Decide')}</button>` : ''}
            </div>`).join('') || '<span class="muted">—</span>'}</td>
          <td><form class="btn-row" data-form="propose" data-variant="${v.id}"><input class="inline-input" name="amount" placeholder="VND" ${live ? '' : 'disabled'}>
            <button class="btn-link" type="submit" ${live ? '' : 'disabled'}>${v.pendingProposalId ? L('Sửa đề xuất', 'Revise') : L('Đề xuất', 'Propose')}</button></form>
            ${v.pendingProposalId ? `<span class="muted">${L('Đang có đề xuất chờ duyệt; sửa sẽ rút đề xuất đó.', 'Revising withdraws the pending one.')}</span>` : ''}</td></tr>`;
      }).join('')}</tbody></table></div>`;
  }

  function reviewTab(p) {
    const review = PM.currentReview(p);
    if (p.firstPublishedAt) {
      return `<div class="panel"><div class="alert info">${L('Sản phẩm đã qua lần duyệt đầu và đã công khai. Nội dung sửa trực tiếp; giá đổi qua đề xuất giá ở tab Size.', 'Initial review done; prices change through proposals.')}</div>${reviewHistory(p)}</div>`;
    }
    const proposals = PM.currentReviewProposals(p);
    return `<div class="panel" style="display: flex; flex-direction: column; gap: 12px;">
      <div class="panel-title">${L('Hồ sơ hiện hành', 'Current review')}: ${L('revision', 'revision')} ${review.revision} ${reviewBadge(review.state, review.effective)}</div>
      ${review.reason ? `<div class="alert error">${L('Lý do từ chối', 'Rejection reason')}: ${esc(review.reason)}</div>` : ''}
      <table class="console-table"><thead><tr><th>Size</th><th class="num">${L('Giá đề xuất', 'Proposed')}</th><th class="num">${L('Giá duyệt', 'Approved')}</th><th>${L('Trạng thái', 'State')}</th></tr></thead>
        <tbody>${proposals.map((x) => { const v = PM.variant(p, x.variantId); return `<tr><td>${v.size} <span class="code-mono">${esc(v.sku)}</span></td>
          <td class="num">${money(x.proposed)}</td><td class="num">${money(x.approved)}</td><td>${proposalBadge(x, v)}</td></tr>`; }).join('')}</tbody></table>
      <div class="btn-row">
        ${review.state === 'DRAFT' && !p.deletedAt ? `<button class="btn-primary" data-action="submit-review">📨 ${L('Gửi duyệt toàn bộ hồ sơ', 'Submit whole review')}</button>` : ''}
        ${review.state === 'PENDING' && !p.deletedAt ? (isManager() ? `<button class="btn-primary btn-accent" data-action="decide-review">⚖️ ${L('Duyệt / từ chối', 'Decide')}</button>`
          : `<span class="muted">${L('Chờ Manager quyết định (chuyển vai trò sang Manager để thử).', 'Waiting for a Manager (switch role to try).')}</span>`) : ''}
        ${review.state === 'APPROVED' ? `<span class="muted">${L('Đã duyệt: áp dụng giá ở tab Size rồi bấm Công khai. Manager được tự duyệt hồ sơ do mình tạo.', 'Approved: apply prices, then publish.')}</span>` : ''}
        ${review.state === 'REJECTED' ? `<span class="muted">${L('Sửa nội dung/ảnh/giá để tạo revision mới rồi gửi lại.', 'Edit to start a new revision, then resubmit.')}</span>` : ''}
      </div>${reviewHistory(p)}</div>`;
  }

  function reviewHistory(p) {
    return `<div class="panel-title" style="margin-top: 6px;">${L('Các revision', 'Revisions')}</div>
      <table class="console-table"><thead><tr><th>Rev</th><th>${L('Nội dung', 'Content')}</th><th>${L('Trạng thái', 'State')}</th><th>${L('Gửi', 'Submitted')}</th><th>${L('Quyết định', 'Decided')}</th></tr></thead>
      <tbody>${p.reviews.slice().reverse().map((r) => `<tr><td>${r.revision}</td><td>v${r.contentVersion}</td><td>${reviewBadge(r.state, r.effective)}</td>
        <td>${esc(r.submittedBy || '—')}<br><span class="muted">${date(r.submittedAt)}</span></td><td>${esc(r.decidedBy || '—')}<br><span class="muted">${date(r.decidedAt)}</span></td></tr>`).join('')}</tbody></table>`;
  }

  function historyTab(p) {
    return `<div class="panel"><ul class="timeline">${p.history.slice().reverse().map((h) => `<li><span class="muted">${date(h.at)}</span>
      <span><span class="code-mono">${esc(h.action)}</span> · ${esc(h.actor)} ${h.detail ? '· ' + esc(h.detail) : ''}</span></li>`).join('')}</ul></div>`;
  }

  // ---------------------------------------------------------------- approvals queue

  function approvals() {
    const reviews = PM.state.products.filter((p) => !p.deletedAt && !p.firstPublishedAt && PM.currentReview(p).state === 'PENDING');
    const prices = [];
    PM.state.products.filter((p) => !p.deletedAt).forEach((p) => p.variants.forEach((v) => {
      const x = v.pendingProposalId && PM.proposal(p, v.pendingProposalId);
      if (x && !x.reviewId) prices.push({ p, v, x });
    }));
    return `<div class="console-header-block"><div><div class="console-title">✅ ${L('Chờ duyệt', 'Pending approval')}</div>
      <div class="console-subtitle">${L('Hồ sơ sản phẩm mới được duyệt nguyên khối; đề xuất giá sau khi công khai duyệt theo từng size. Chỉ Manager quyết định.', 'New product reviews are decided as a whole; later price proposals per size. Manager only.')}</div></div></div>
      ${!isManager() ? `<div class="alert info">${L('Bạn đang là Staff: chỉ xem được, nút quyết định chỉ hiện với Manager.', 'Staff can only view; decisions are for Managers.')}</div>` : ''}
      ${errorBox()}
      <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Hồ sơ sản phẩm mới', 'New product reviews')} (${reviews.length})</strong></div>
        ${reviews.length ? `<table class="console-table"><thead><tr><th>${L('Sản phẩm', 'Product')}</th><th>Rev</th><th>${L('Người gửi', 'Submitted by')}</th><th>${L('Size và giá đề xuất', 'Sizes and prices')}</th><th></th></tr></thead><tbody>
          ${reviews.map((p) => { const r = PM.currentReview(p); return `<tr><td><button class="btn-link" data-open="${p.id}">${esc(p.name)}</button></td><td>${r.revision}</td>
            <td>${esc(r.submittedBy)}<br><span class="muted">${date(r.submittedAt)}</span></td>
            <td>${PM.currentReviewProposals(p).map((x) => `${PM.variant(p, x.variantId).size}: ${money(x.proposed)}`).join('<br>')}</td>
            <td>${isManager() ? `<button class="btn-primary btn-accent" data-decide-review="${p.id}">${L('Quyết định', 'Decide')}</button>` : ''}</td></tr>`; }).join('')}</tbody></table>`
          : `<div class="empty-state">${L('Không có hồ sơ chờ duyệt.', 'Nothing pending.')}</div>`}</div>
      <div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Đề xuất đổi giá', 'Price proposals')} (${prices.length})</strong></div>
        ${prices.length ? `<table class="console-table"><thead><tr><th>${L('Sản phẩm', 'Product')}</th><th>SKU</th><th class="num">${L('Giá đang bán', 'Applied')}</th><th class="num">${L('Đề xuất', 'Proposed')}</th><th></th></tr></thead><tbody>
          ${prices.map(({ p, v, x }) => `<tr><td><button class="btn-link" data-open="${p.id}">${esc(p.name)}</button></td><td class="code-mono">${esc(v.sku)}</td>
            <td class="num">${money(v.appliedPrice)}</td><td class="num"><strong>${money(x.proposed)}</strong></td>
            <td>${isManager() ? `<button class="btn-primary btn-accent" data-decide-proposal="${x.id}" data-product="${p.id}">${L('Quyết định', 'Decide')}</button>` : ''}</td></tr>`).join('')}</tbody></table>`
          : `<div class="empty-state">${L('Không có đề xuất giá chờ duyệt.', 'No pending price proposal.')}</div>`}</div>`;
  }

  // ---------------------------------------------------------------- categories

  function categoriesView() {
    return `<div class="console-header-block"><div><div class="console-title">🗂️ ${L('Danh mục', 'Categories')}</div>
      <div class="console-subtitle">${L('Tên duy nhất toàn hệ thống, không phân biệt hoa/thường, kể cả danh mục đã ngừng. Danh mục đã dùng chỉ ngừng hoạt động, không xóa. Staff và Manager đều quản lí được.', 'Unique names including inactive ones. Used categories are deactivated, never deleted.')}</div></div></div>
      ${errorBox()}
      <form class="panel" data-form="category-create"><div class="form-grid">
        <div class="field"><label>${L('Tên danh mục *', 'Name *')}</label><input name="name"></div>
        <div class="field"><label>${L('Mô tả', 'Description')}</label><input name="description"></div>
        <div class="field" style="justify-content: flex-end;"><button class="btn-primary" type="submit">＋ ${L('Tạo danh mục', 'Create category')}</button></div></div></form>
      <div class="data-table-container"><table class="console-table"><thead><tr><th>ID</th><th>${L('Tên', 'Name')}</th><th>${L('Mô tả', 'Description')}</th><th>${L('Sản phẩm', 'Products')}</th><th>${L('Trạng thái', 'State')}</th><th></th></tr></thead>
        <tbody>${PM.state.categories.slice().sort((a, b) => a.name.localeCompare(b.name, 'vi')).map((c) => `<tr>
          <td class="code-mono">${c.id}</td><td><strong>${esc(c.name)}</strong></td><td class="muted">${esc(c.description || '—')}</td>
          <td>${PM.categoryImpact(c.id).length}</td>
          <td>${c.active ? `<span class="badge success">${L('Hoạt động', 'Active')}</span>` : `<span class="badge warning">${L('Ngừng', 'Inactive')}</span>`}</td>
          <td class="btn-row"><button class="btn-link" data-edit-category="${c.id}">${L('Sửa', 'Edit')}</button>
            <button class="btn-link" data-impact="${c.id}">${c.active ? L('Ngừng hoạt động…', 'Deactivate…') : L('Kích hoạt lại…', 'Reactivate…')}</button></td></tr>`).join('')}</tbody></table></div>`;
  }

  // ---------------------------------------------------------------- import

  function importView() {
    let batch = null;
    if (ui.importBatchId) {
      try { batch = PM.importBatch(actor(), ui.importBatchId); } catch (e) { batch = null; }
    }
    const mine = PM.state.imports.filter((b) => b.actor === actor()).slice(0, 6);
    return `<div class="console-header-block"><div><div class="console-title">📥 ${L('Import sản phẩm từ Excel', 'Import products from Excel')}</div>
      <div class="console-subtitle">${L('Prototype không đọc file .xlsx: hãy copy vùng dữ liệu trong Excel (kể cả hàng tiêu đề) rồi dán vào ô dưới. Cùng "Mã nhóm" = một sản phẩm nhiều size. Sản phẩm import là bản nháp chưa có ảnh.', 'Paste the Excel range (with header). Same group key = one product with several sizes. Imported products are drafts without images.')}</div></div>
      <button class="btn-secondary" data-action="download-template">⬇️ ${L('Tải file mẫu (.csv)', 'Template (.csv)')}</button></div>
      ${errorBox()}
      <form class="panel" data-form="import" style="display: flex; flex-direction: column; gap: 10px;">
        <div class="form-grid"><div class="field"><label>${L('Tên file', 'File name')}</label><input name="fileName" value="${esc(ui.importFile)}"></div></div>
        <div class="field"><label>${L('Dữ liệu dán từ Excel (7 cột, cách nhau bằng Tab)', 'Pasted Excel data (7 tab-separated columns)')}</label>
          <textarea class="import-area" name="text" placeholder="${esc(PM.IMPORT_COLUMNS.join('\t'))}">${esc(ui.importText)}</textarea></div>
        <div class="btn-row"><button class="btn-primary" type="submit">🔎 ${L('Kiểm tra dữ liệu', 'Validate')}</button>
          <button class="btn-secondary" type="button" data-action="import-sample">${L('Điền dữ liệu mẫu', 'Fill sample')}</button>
          <button class="btn-secondary" type="button" data-action="import-sample-errors">${L('Điền mẫu có lỗi', 'Fill sample with errors')}</button></div></form>
      ${batch ? importResult(batch) : ''}
      ${mine.length ? `<div class="data-table-container"><div class="data-table-toolbar"><strong>${L('Các lô gần đây của bạn', 'Your recent batches')}</strong></div>
        <table class="console-table"><thead><tr><th>${L('Lô', 'Batch')}</th><th>File</th><th>${L('Trạng thái', 'State')}</th><th>${L('Dòng', 'Rows')}</th><th>${L('Lỗi', 'Errors')}</th><th>${L('Hết hạn', 'Expires')}</th></tr></thead>
        <tbody>${mine.map((b) => `<tr class="clickable" data-batch="${b.id}"><td class="code-mono">${esc(b.id)}</td><td>${esc(b.fileName)}</td><td>${batchBadge(b.state)}</td>
          <td>${b.rowCount}</td><td>${b.errors.length}</td><td class="muted">${date(b.expiresAt)}</td></tr>`).join('')}</tbody></table></div>` : ''}`;
  }

  function batchBadge(s) {
    const map = { VALIDATED: ['Hợp lệ', 'Validated', 'info'], REJECTED: ['Có lỗi', 'Rejected', 'error'], IMPORTED: ['Đã nhập', 'Imported', 'success'], EXPIRED: ['Hết hạn', 'Expired', 'warning'] };
    const [vi, en, cls] = map[s];
    return `<span class="badge ${cls}">${L(vi, en)}</span>`;
  }

  function importResult(b) {
    if (b.state === 'REJECTED') {
      return `<div class="data-table-container"><div class="data-table-toolbar"><strong>${batchBadge(b.state)} ${b.errors.length} ${L('lỗi — không tạo sản phẩm nào', 'errors — nothing created')}</strong>
        <button class="btn-secondary" data-action="download-errors">⬇️ ${L('Tải báo cáo lỗi', 'Error report')}</button></div>
        <table class="console-table"><thead><tr><th>${L('Dòng', 'Row')}</th><th>${L('Cột', 'Column')}</th><th>${L('Giá trị', 'Value')}</th><th>${L('Mã lỗi', 'Code')}</th><th>${L('Thông báo', 'Message')}</th></tr></thead>
        <tbody>${b.errors.map((e) => `<tr><td>${e.row}</td><td>${esc(e.column)}</td><td class="code-mono">${esc(e.value || '')}</td><td class="code-mono">${esc(e.code)}</td><td>${esc(e.message)}</td></tr>`).join('')}</tbody></table></div>`;
    }
    if (b.state === 'VALIDATED') {
      return `<div class="data-table-container"><div class="data-table-toolbar"><strong>${batchBadge(b.state)} ${b.preview.length} ${L('sản phẩm', 'products')} · ${b.preview.reduce((n, g) => n + g.variants.length, 0)} size</strong>
        <span class="btn-row"><button class="btn-secondary" data-action="import-expire">⏱️ ${L('Giả lập hết 60 phút', 'Simulate expiry')}</button>
        <button class="btn-primary btn-accent" data-action="import-confirm">${L('Xác nhận nhập…', 'Confirm import…')}</button></span></div>
        <table class="console-table"><thead><tr><th>${L('Mã nhóm', 'Group')}</th><th>${L('Tên', 'Name')}</th><th>${L('Danh mục', 'Category')}</th><th>${L('Size: giá đề xuất / số lượng', 'Size: price / qty')}</th></tr></thead>
        <tbody>${b.preview.map((g) => `<tr><td class="code-mono">${esc(g.groupKey)}</td><td>${esc(g.name)}</td><td>${esc(g.categoryName)}</td>
          <td>${g.variants.map((v) => `${v.size}: ${money(v.price)} / ${esc(v.qty)}`).join('<br>')}</td></tr>`).join('')}</tbody></table>
        <div class="pagination muted">${L('Chưa cấp mã sản phẩm hay SKU trước khi xác nhận. Hết hạn lúc', 'No product number or SKU yet. Expires')} ${date(b.expiresAt)}</div></div>`;
    }
    if (b.state === 'IMPORTED') {
      return `<div class="data-table-container"><div class="data-table-toolbar"><strong>${batchBadge(b.state)} ${L('Đã tạo', 'Created')} ${b.created.length} ${L('sản phẩm nháp. Thêm ảnh trước khi gửi duyệt.', 'drafts. Add images before submitting.')}</strong></div>
        <table class="console-table"><thead><tr><th>${L('Mã nhóm', 'Group')}</th><th>${L('Sản phẩm', 'Product')}</th><th>SKU</th></tr></thead>
        <tbody>${b.created.map((c) => `<tr><td class="code-mono">${esc(c.groupKey)}</td><td><button class="btn-link" data-open="${c.productId}">SP${String(c.productId).padStart(6, '0')}</button></td>
          <td>${c.skus.map((s) => `<span class="code-mono">${esc(s)}</span>`).join(' ')}</td></tr>`).join('')}</tbody></table></div>`;
    }
    return `<div class="alert warning">${batchBadge(b.state)} ${L('Bản xem trước đã hết hạn và dữ liệu đã bị xóa. Hãy kiểm tra lại dữ liệu.', 'The preview expired; validate again.')}</div>`;
  }

  // ---------------------------------------------------------------- public catalogue

  function publicCatalogue() {
    const f = ui.pub;
    let result = null;
    let error = null;
    try { result = PM.publicList(f); } catch (e) { if (!(e instanceof PM.DomainError)) throw e; error = e; }
    const active = PM.state.categories.filter((c) => c.active);
    return `<div class="console-header-block"><div><div class="console-title">💍 ${L('Sản phẩm đang bán', 'Products')}</div>
      <div class="console-subtitle">${L('Chỉ hiện sản phẩm đang công khai, chưa xóa, danh mục hoạt động, có ảnh và đã có giá áp dụng cho mọi size. Không cần đăng nhập.', 'Only published, eligible products. No sign-in needed.')}</div></div></div>
      <div class="catalogue-layout">
        <form class="panel filter-panel" data-form="public-filter">
          <div class="field"><label>${L('Từ khóa (tên, mô tả)', 'Keyword')}</label><input name="q" value="${esc(f.q)}" placeholder="${L('vd: vàng, kim cương', 'e.g. gold')}"></div>
          <div class="field"><label>${L('Danh mục', 'Category')}</label><select name="categoryId"><option value="">${L('Tất cả', 'All')}</option>
            ${active.map((c) => `<option value="${c.id}" ${String(c.id) === String(f.categoryId) ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select></div>
          <div class="field"><label>${L('Giá từ (VND)', 'Min price')}</label><input name="min" value="${esc(f.min)}" inputmode="numeric"></div>
          <div class="field"><label>${L('Giá đến (VND)', 'Max price')}</label><input name="max" value="${esc(f.max)}" inputmode="numeric"></div>
          <label style="display: flex; gap: 6px; align-items: center; font-size: 12.5px;"><input type="checkbox" name="canBuy" ${f.canBuy ? 'checked' : ''}> ${L('Chỉ size có thể mua', 'Can buy now')}</label>
          <div class="field"><label>${L('Sắp xếp', 'Sort')}</label><select name="sort">
            ${[['name-asc', 'Tên A–Z', 'Name A–Z'], ['price-asc', 'Giá tăng dần', 'Price ascending'], ['price-desc', 'Giá giảm dần', 'Price descending'], ['newest', 'Mới nhất', 'Newest']]
              .map(([v, vi, en]) => `<option value="${v}" ${f.sort === v ? 'selected' : ''}>${L(vi, en)}</option>`).join('')}</select></div>
          <div class="btn-row"><button class="btn-primary" type="submit">${L('Áp dụng', 'Apply')}</button><button class="btn-link" type="button" data-action="clear-public">${L('Xóa lọc', 'Clear')}</button></div>
        </form>
        <div>${error ? `<div class="alert error"><span class="code">${esc(error.code)}</span> — ${esc((ERRORS[error.code] || [error.code, error.code])[ui.lang === 'vi' ? 0 : 1])}
            ${error.fieldErrors.length ? `<ul>${error.fieldErrors.map((x) => `<li>${esc(fieldText(x))}</li>`).join('')}</ul>` : ''}</div>`
          : result.items.length ? `<div class="product-grid">${result.items.map((p) => `<button class="product-card" data-public="${p.id}">
              <img src="${p.images[0].src}" alt="${esc(p.images[0].altText)}">
              <div class="card-body"><span class="muted">${esc(p.category.name)}</span><span class="card-name">${esc(p.name)}</span>
                <span class="card-price">${money(p.priceRange.minimum)}${p.priceRange.minimum !== p.priceRange.maximum ? ' – ' + money(p.priceRange.maximum) : ''}</span>
                ${p.canBuy ? `<span class="badge success">${L('Có thể mua', 'Available')}</span>` : `<span class="badge">${L('Tạm hết hàng', 'Unavailable now')}</span>`}</div></button>`).join('')}</div>
            <div class="panel" style="padding: 0; margin-top: 12px;">${pagination(result, 'public')}</div>`
          : `<div class="panel"><div class="empty-state"><div class="icon">🔍</div><div>${L('Không có sản phẩm phù hợp. Thử bỏ bớt bộ lọc.', 'No matching product.')}</div></div></div>`}</div>
      </div>`;
  }

  function publicDetail() {
    let p;
    try { p = PM.publicDetail(ui.publicId); } catch (e) {
      return `<div class="breadcrumb"><button data-go="catalogue">${L('Sản phẩm', 'Products')}</button></div>
        <div class="panel"><div class="empty-state"><div class="icon">🫥</div><strong>404 — PRODUCT_NOT_FOUND</strong>
        <div>${L('Sản phẩm không tồn tại hoặc không còn được bán (đã ẩn, xóa, hoặc danh mục ngừng).', 'The product does not exist or is no longer sold.')}</div></div></div>`;
    }
    const selected = p.variants.find((v) => v.id === ui.selectedVariant) || null;
    const image = p.images[Math.min(ui.gallery, p.images.length - 1)];
    return `<div class="breadcrumb"><button data-go="catalogue">${L('Sản phẩm', 'Products')}</button> / ${esc(p.name)}</div>
      <div class="detail-layout">
        <div><img class="gallery-main" src="${image.src}" alt="${esc(image.altText)}">
          <div class="gallery-thumbs">${p.images.map((img, i) => `<button class="${i === ui.gallery ? 'active' : ''}" data-gallery="${i}"><img src="${img.src}" alt="${esc(img.altText)}"></button>`).join('')}</div></div>
        <div class="panel" style="display: flex; flex-direction: column; gap: 12px;">
          <span class="muted">${esc(p.category.name)}</span>
          <div class="console-title">${esc(p.name)}</div>
          <div style="font-size: 18px; font-weight: 700;">${selected ? money(selected.appliedPrice) : money(p.priceRange.minimum) + (p.priceRange.minimum !== p.priceRange.maximum ? ' – ' + money(p.priceRange.maximum) : '')}</div>
          <div>${esc(p.description)}</div>
          <div class="field"><label>${L('Chọn size', 'Choose size')}</label><div class="size-picker">
            ${p.variants.map((v) => `<button class="size-btn ${ui.selectedVariant === v.id ? 'active' : ''} ${v.saleEnabled ? '' : 'disabled-sale'}" data-size="${v.id}">
              ${v.size}<small>${!v.saleEnabled ? L('Ngừng bán', 'Stopped') : v.canBuy ? money(v.appliedPrice) : L('Hết hàng', 'Sold out')}</small></button>`).join('')}</div></div>
          ${selected ? `<div class="muted">SKU <span class="code-mono">${esc(selected.sku)}</span> · ${selected.canBuy ? L('Có thể mua', 'Available') : !selected.saleEnabled ? L('Size này đã ngừng bán', 'This size is not sold') : L('Size này tạm hết hàng', 'Sold out')}</div>` : ''}
          <div class="btn-row"><button class="btn-primary btn-accent" data-action="buy" ${selected && selected.canBuy ? '' : 'disabled'}>🛍️ ${L('Chọn mua', 'Buy')}</button>
            ${!selected ? `<span class="muted">${L('Hãy chọn size trước.', 'Choose a size first.')}</span>` : ''}</div>
          <div class="alert info">${L('Giỏ hàng, giữ hàng và thanh toán thuộc FE-08; FE-02 chỉ chuyển sản phẩm/size/số lượng sang FE-08.', 'Cart, holds and payment belong to FE-08.')}</div>
        </div></div>`;
  }

  // ---------------------------------------------------------------- modals

  function modal() {
    const m = ui.modal;
    const footer = (confirmLabel, action, danger) => `<div class="modal-footer"><button class="btn-secondary" data-action="close-modal">${L('Hủy', 'Cancel')}</button>
      <button class="${danger ? 'btn-danger' : 'btn-primary'}" data-action="${action}">${confirmLabel}</button></div>`;
    let body = '';
    if (m.type === 'delete') {
      const p = PM.product(m.productId);
      body = `<div class="modal-header">${L('Xóa sản phẩm?', 'Delete product?')}<button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body"><div><strong>${esc(p.name)}</strong> (SP${String(p.id).padStart(6, '0')})</div>
        <div class="alert warning">${L('Xóa mềm: sản phẩm biến khỏi catalogue và danh sách quản trị, giữ SKU/lịch sử, không trừ/hoàn số lượng, không hủy giao dịch đã chấp nhận. Không thể khôi phục.', 'Soft delete, no restore. History and SKUs are kept.')}</div></div>
        ${footer(L('Xóa vĩnh viễn khỏi catalogue', 'Delete'), 'confirm-delete', true)}`;
    } else if (m.type === 'decide-review') {
      const p = PM.product(m.productId);
      const proposals = PM.currentReviewProposals(p).filter((x) => x.state === 'PENDING');
      body = `<div class="modal-header">${L('Quyết định hồ sơ', 'Review decision')} — ${esc(p.name)}<button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body">
          <div class="muted">${L('Duyệt phải gồm mọi size; Manager có thể chỉnh giá cuối. Từ chối cần lý do và áp dụng cho cả hồ sơ. Duyệt xong chưa áp dụng giá, chưa công khai.', 'Approval covers every size; rejection needs a reason.')}</div>
          <table class="console-table"><thead><tr><th>Size</th><th class="num">${L('Đề xuất', 'Proposed')}</th><th>${L('Giá cuối (VND)', 'Final price')}</th></tr></thead>
          <tbody>${proposals.map((x) => `<tr><td>${PM.variant(p, x.variantId).size}</td><td class="num">${money(x.proposed)}</td>
            <td><input class="inline-input" data-final="${x.id}" value="${esc(x.proposed)}"></td></tr>`).join('')}</tbody></table>
          <div class="field"><label>${L('Lý do (bắt buộc khi từ chối)', 'Reason (required to reject)')}</label><textarea id="decisionReason"></textarea></div>
          ${errorBox()}</div>
        <div class="modal-footer"><button class="btn-secondary" data-action="close-modal">${L('Hủy', 'Cancel')}</button>
          <button class="btn-danger" data-action="reject-review">${L('Từ chối', 'Reject')}</button>
          <button class="btn-primary btn-accent" data-action="approve-review">${L('Duyệt toàn bộ', 'Approve all')}</button></div>`;
    } else if (m.type === 'decide-proposal') {
      const p = PM.product(m.productId);
      const x = PM.proposal(p, m.proposalId);
      const v = PM.variant(p, x.variantId);
      body = `<div class="modal-header">${L('Quyết định đề xuất giá', 'Price decision')} — <span class="code-mono">${esc(v.sku)}</span><button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body"><dl class="kv"><dt>${L('Giá đang bán', 'Applied')}</dt><dd>${money(v.appliedPrice)}</dd><dt>${L('Đề xuất', 'Proposed')}</dt><dd><strong>${money(x.proposed)}</strong></dd></dl>
          <div class="field"><label>${L('Giá duyệt (VND)', 'Approved price')}</label><input class="inline-input" id="approvedPrice" value="${esc(x.proposed)}"></div>
          <div class="field"><label>${L('Lý do (khi từ chối)', 'Reason (to reject)')}</label><textarea id="decisionReason"></textarea></div>
          <div class="muted">${L('Duyệt xong vẫn cần bấm Áp dụng để đổi giá bán.', 'Apply separately after approval.')}</div>${errorBox()}</div>
        <div class="modal-footer"><button class="btn-secondary" data-action="close-modal">${L('Hủy', 'Cancel')}</button>
          <button class="btn-danger" data-action="reject-proposal">${L('Từ chối', 'Reject')}</button>
          <button class="btn-primary btn-accent" data-action="approve-proposal">${L('Duyệt', 'Approve')}</button></div>`;
    } else if (m.type === 'impact') {
      const c = PM.category(m.categoryId);
      const affected = PM.categoryImpact(c.id);
      body = `<div class="modal-header">${c.active ? L('Ngừng hoạt động', 'Deactivate') : L('Kích hoạt lại', 'Reactivate')}: ${esc(c.name)}<button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body"><div class="alert ${c.active ? 'warning' : 'info'}">${c.active
          ? L(affected.length + ' sản phẩm sẽ bị loại khỏi catalogue công khai và không mua mới được. Cờ công khai, lịch sử và giao dịch cũ giữ nguyên.', affected.length + ' products leave the public catalogue.')
          : L('Chỉ sản phẩm còn công khai, chưa xóa và đủ điều kiện mới hiện lại. Sản phẩm đã ẩn không tự hiện.', 'Only still-published eligible products reappear.')}</div>
          ${affected.length ? `<table class="console-table"><thead><tr><th>${L('Sản phẩm', 'Product')}</th><th>${L('Công khai', 'Publication')}</th></tr></thead>
            <tbody>${affected.slice(0, 20).map((p) => `<tr><td>${esc(p.name)}</td><td>${productStatus(p)}</td></tr>`).join('')}</tbody></table>` : `<div class="muted">${L('Không có sản phẩm nào.', 'No product.')}</div>`}
          ${errorBox()}</div>
        ${footer(c.active ? L('Xác nhận ngừng', 'Deactivate') : L('Xác nhận kích hoạt', 'Reactivate'), 'confirm-activation', c.active)}`;
    } else if (m.type === 'edit-category') {
      const c = PM.category(m.categoryId);
      body = `<div class="modal-header">${L('Sửa danh mục', 'Edit category')}<button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body"><div class="field"><label>${L('Tên *', 'Name *')}</label><input id="categoryName" value="${esc(c.name)}"></div>
          <div class="field"><label>${L('Mô tả', 'Description')}</label><textarea id="categoryDescription">${esc(c.description || '')}</textarea></div>${errorBox()}</div>
        ${footer(L('Lưu', 'Save'), 'save-category')}`;
    } else if (m.type === 'confirm-import') {
      const b = PM.importBatch(actor(), ui.importBatchId);
      body = `<div class="modal-header">${L('Xác nhận nhập', 'Confirm import')}<button class="close-btn" data-action="close-modal">×</button></div>
        <div class="modal-body"><div>${L('Tạo', 'Create')} <strong>${b.preview ? b.preview.length : 0}</strong> ${L('sản phẩm nháp cùng lúc. Có lỗi thì không tạo sản phẩm nào.', 'drafts at once. Any error creates nothing.')}</div>
          <div class="muted">${L('Thử: ngừng một danh mục trong lô ở màn Danh mục rồi xác nhận để thấy IMPORT_PREVIEW_STALE.', 'Tip: deactivate a category first to see IMPORT_PREVIEW_STALE.')}</div>${errorBox()}</div>
        ${footer(L('Xác nhận nhập', 'Confirm'), 'confirm-import')}`;
    }
    return `<div class="modal-backdrop" data-backdrop="1"><div class="modal ${m.type === 'decide-review' ? 'wide' : ''}">${body}</div></div>`;
  }

  // ---------------------------------------------------------------- events

  const currentProduct = () => PM.product(ui.productId);

  function readFile(file, onLoad) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast(L('Chỉ nhận JPEG, PNG hoặc WebP (MEDIA_TYPE_UNSUPPORTED).', 'JPEG, PNG or WebP only.'), true);
      return;
    }
    if (file.size > 5000000) {
      toast(L('Ảnh quá 5MB (MEDIA_TOO_LARGE).', 'Image over 5MB.'), true);
      return;
    }
    if (file.size > 1500000) toast(L('Ảnh lớn có thể làm đầy bộ nhớ trình duyệt của prototype.', 'Large images may fill browser storage.'));
    const reader = new FileReader();
    reader.onload = () => onLoad(reader.result);
    reader.readAsDataURL(file);
  }

  function addImage(target, src, label) {
    const r = run(() => PM.uploadMedia(ui.role, actor(), src, label));
    if (!r.ok) return;
    const list = target === 'create' ? ui.createDraft.images : ui.imageDraft.images;
    if (list.length >= PM.MAX_IMAGES) { toast(L('Tối đa 10 ảnh.', 'At most 10 images.'), true); return; }
    list.push({ mediaId: r.result, altText: label, position: list.length });
    render();
  }

  function syncCreateForm(form) {
    const d = ui.createDraft;
    d.name = form.name.value;
    d.description = form.description.value;
    d.categoryId = form.categoryId.value;
    d.variants = d.variants.map((v, i) => ({ size: Number(form['size-' + i].value), price: form['price-' + i].value, qty: form['qty-' + i].value }));
  }

  document.addEventListener('input', (e) => {
    const alt = e.target.getAttribute('data-alt');
    if (alt) {
      const [scope, index] = alt.split(':');
      const list = scope === 'create' ? ui.createDraft.images : ui.imageDraft.images;
      list[Number(index)].altText = e.target.value;
    }
    if (e.target.name === 'text' && e.target.closest('[data-form="import"]')) ui.importText = e.target.value;
  });

  document.addEventListener('change', (e) => {
    const upload = e.target.getAttribute('data-upload');
    if (upload && e.target.files[0]) {
      if (upload === 'create') syncCreateForm(e.target.closest('form'));
      const file = e.target.files[0];
      readFile(file, (src) => addImage(upload, src, file.name.replace(/\.[^.]+$/, '').slice(0, 200)));
    }
  });

  document.addEventListener('submit', (e) => {
    const form = e.target;
    const kind = form.getAttribute('data-form');
    if (!kind) return;
    e.preventDefault();
    const p = currentProduct();
    if (kind === 'list-filter') {
      ui.list = { q: form.q.value, categoryId: form.categoryId.value, published: form.published.value, page: 0 };
      saveUi(); render();
    } else if (kind === 'public-filter') {
      ui.pub = { q: form.q.value, categoryId: form.categoryId.value, min: form.min.value, max: form.max.value, canBuy: form.canBuy.checked, sort: form.sort.value, page: 0 };
      saveUi(); render();
    } else if (kind === 'create') {
      syncCreateForm(form);
      const d = ui.createDraft;
      const r = run(() => PM.createDraft(ui.role, actor(), {
        name: d.name, description: d.description, categoryId: d.categoryId,
        variants: d.variants, images: d.images.map((img, i) => ({ ...img, position: i }))
      }), L('Đã lưu bản nháp sản phẩm.', 'Draft saved.'));
      if (r.ok) { ui.createDraft = null; go('product', { productId: r.result.id, tab: 'overview' }); }
    } else if (kind === 'content') {
      run(() => PM.updateContent(ui.role, actor(), p.id, p.version, { name: form.name.value, description: form.description.value, categoryId: form.categoryId.value }),
        (changed) => (changed ? L('Đã lưu nội dung.', 'Content saved.') : L('Không có thay đổi.', 'No change.')));
    } else if (kind === 'quantity') {
      run(() => PM.setQuantity(ui.role, actor(), p.id, form.getAttribute('data-variant'), form.qty.value),
        (changed) => (changed ? L('Đã cập nhật số lượng.', 'Quantity updated.') : L('Không có thay đổi.', 'No change.')));
    } else if (kind === 'propose') {
      const variantId = Number(form.getAttribute('data-variant'));
      const v = PM.variant(p, variantId);
      run(() => (v.pendingProposalId ? PM.reviseProposal(ui.role, actor(), p.id, v.pendingProposalId, form.amount.value)
        : PM.createProposal(ui.role, actor(), p.id, variantId, form.amount.value)),
        L(p.firstPublishedAt ? 'Đã tạo đề xuất nháp. Bấm "Gửi duyệt".' : 'Đã đổi giá đề xuất; hồ sơ trở về nháp.', 'Proposal saved.'));
    } else if (kind === 'category-create') {
      run(() => PM.createCategory(ui.role, form.name.value, form.description.value), L('Đã tạo danh mục.', 'Category created.'));
    } else if (kind === 'import') {
      ui.importText = form.text.value;
      ui.importFile = form.fileName.value;
      const r = run(() => PM.validateImport(ui.role, actor(), form.fileName.value, form.text.value),
        (b) => (b.state === 'VALIDATED' ? L('Dữ liệu hợp lệ. Kiểm tra bản xem trước rồi xác nhận.', 'Valid. Review and confirm.') : L('Có lỗi trong dữ liệu.', 'The data has errors.')));
      if (r.ok) { ui.importBatchId = r.result.id; saveUi(); render(); }
    }
  });

  document.addEventListener('click', (e) => {
    const el = e.target.closest('button, tr[data-open], tr[data-batch], [data-backdrop]');
    if (!el) return;
    if (el.hasAttribute('data-backdrop') && e.target === el) { ui.modal = null; ui.error = null; render(); return; }
    const data = (name) => el.getAttribute(name);
    const p = currentProduct();

    if (data('data-go')) { go(data('data-go')); return; }
    if (data('data-role')) { ui.role = data('data-role'); ui.error = null; ui.modal = null; saveUi(); render(); return; }
    if (data('data-lang')) { ui.lang = data('data-lang'); saveUi(); render(); return; }
    if (data('data-tab')) { ui.tab = data('data-tab'); ui.error = null; saveUi(); render(); return; }
    if (data('data-open')) { go('product', { productId: Number(data('data-open')), tab: 'overview', imageDraft: null }); return; }
    if (data('data-public')) { go('publicDetail', { publicId: Number(data('data-public')), selectedVariant: null, gallery: 0 }); return; }
    if (data('data-batch')) { ui.importBatchId = data('data-batch'); ui.error = null; saveUi(); render(); return; }
    if (data('data-gallery')) { ui.gallery = Number(data('data-gallery')); render(); return; }
    if (data('data-size')) { ui.selectedVariant = Number(data('data-size')); render(); return; }
    if (data('data-page')) {
      const [scope, n] = data('data-page').split(':');
      if (scope === 'list') ui.list.page = Number(n); else ui.pub.page = Number(n);
      saveUi(); render(); return;
    }
    if (data('data-move')) {
      const [scope, index, delta] = data('data-move').split(':').map((x, i) => (i ? Number(x) : x));
      if (scope === 'create') syncCreateForm(el.closest('form'));
      const list = scope === 'create' ? ui.createDraft.images : ui.imageDraft.images;
      const [item] = list.splice(index, 1);
      list.splice(index + delta, 0, item);
      render(); return;
    }
    if (data('data-remove-image')) {
      const [scope, index] = data('data-remove-image').split(':');
      if (scope === 'create') syncCreateForm(el.closest('form'));
      (scope === 'create' ? ui.createDraft.images : ui.imageDraft.images).splice(Number(index), 1);
      render(); return;
    }
    if (data('data-toggle-media')) {
      run(() => PM.toggleMediaAvailability(p.id, data('data-toggle-media')), (s) => (s === 'READY' ? L('Ảnh đọc được trở lại.', 'Image restored.') : L('Ảnh giả lập không đọc được.', 'Image unavailable.')));
      return;
    }
    if (data('data-sale')) {
      const [variantId, enabled] = data('data-sale').split(':');
      run(() => PM.setSaleEnabled(ui.role, actor(), p.id, variantId, enabled === 'true'), enabled === 'true' ? L('Đã mở bán lại size.', 'Size on sale.') : L('Đã ngừng bán size; số lượng giữ nguyên.', 'Size stopped.'));
      return;
    }
    if (data('data-apply')) { run(() => PM.applyPrice(ui.role, actor(), p.id, data('data-apply')), (c) => (c ? L('Đã áp dụng giá đã duyệt.', 'Price applied.') : L('Giá này đã được áp dụng.', 'Already applied.'))); return; }
    if (data('data-submit-proposal')) { run(() => PM.submitProposal(ui.role, actor(), p.id, data('data-submit-proposal')), L('Đã gửi đề xuất giá cho Manager.', 'Proposal submitted.')); return; }
    if (data('data-decide-proposal')) {
      ui.modal = { type: 'decide-proposal', productId: Number(data('data-product') || p.id), proposalId: Number(data('data-decide-proposal')) };
      ui.error = null; render(); return;
    }
    if (data('data-decide-review')) { ui.modal = { type: 'decide-review', productId: Number(data('data-decide-review')) }; ui.error = null; render(); return; }
    if (data('data-impact')) { ui.modal = { type: 'impact', categoryId: Number(data('data-impact')) }; ui.error = null; render(); return; }
    if (data('data-edit-category')) { ui.modal = { type: 'edit-category', categoryId: Number(data('data-edit-category')) }; ui.error = null; render(); return; }

    const action = data('data-action');
    if (!action) return;
    const m = ui.modal;
    switch (action) {
      case 'reset':
        if (confirm(L('Khôi phục toàn bộ dữ liệu mẫu? Mọi thay đổi trong prototype sẽ mất.', 'Reset all sample data?'))) {
          PM.reset();
          go('products', { productId: null, importBatchId: null, imageDraft: null, createDraft: null });
          toast(L('Đã khôi phục dữ liệu mẫu.', 'Sample data restored.'));
        }
        break;
      case 'clear-list': ui.list = { q: '', categoryId: '', published: '', page: 0 }; saveUi(); render(); break;
      case 'clear-public': ui.pub = { q: '', categoryId: '', min: '', max: '', canBuy: false, sort: 'name-asc', page: 0 }; saveUi(); render(); break;
      case 'create-add-size': {
        syncCreateForm(el.closest('form'));
        const used = new Set(ui.createDraft.variants.map((v) => v.size));
        const free = Array.from({ length: 25 }, (_, k) => k + 5).find((s) => !used.has(s)) || 5;
        ui.createDraft.variants.push({ size: free, price: '', qty: '0' });
        render(); break;
      }
      case 'create-remove-size':
        syncCreateForm(el.closest('form'));
        ui.createDraft.variants.splice(Number(data('data-index')), 1);
        render(); break;
      case 'sample-image': {
        const target = data('data-target');
        if (target === 'create') syncCreateForm(el.closest('form'));
        const n = (target === 'create' ? ui.createDraft.images : ui.imageDraft.images).length + 1;
        const hue = Math.floor(Math.random() * 360);
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400"><rect width="400" height="400" fill="hsl(${hue},40%,88%)"/>
          <circle cx="200" cy="200" r="95" fill="none" stroke="hsl(${hue},55%,40%)" stroke-width="24"/><text x="200" y="370" text-anchor="middle" font-family="Arial" font-size="26" fill="hsl(${hue},45%,25%)">${L('Ảnh', 'Image')} ${n}</text></svg>`;
        addImage(target, 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg), L('Ảnh ', 'Image ') + n);
        break;
      }
      case 'save-images':
        run(() => PM.replaceImages(ui.role, actor(), p.id, p.version, ui.imageDraft.images.map((img, i) => ({ ...img, position: i }))),
          (c) => (c ? L('Đã lưu ảnh.', 'Images saved.') : L('Không có thay đổi.', 'No change.')));
        if (!ui.error) ui.imageDraft = null;
        render(); break;
      case 'reset-images': ui.imageDraft = null; render(); break;
      case 'publish': run(() => PM.setPublication(ui.role, actor(), p.id, true), L('Sản phẩm đã công khai.', 'Published.')); break;
      case 'unpublish': run(() => PM.setPublication(ui.role, actor(), p.id, false), L('Đã ẩn sản phẩm khỏi catalogue.', 'Hidden.')); break;
      case 'ask-delete': ui.modal = { type: 'delete', productId: p.id }; render(); break;
      case 'confirm-delete': {
        const r = run(() => PM.deleteProduct(ui.role, actor(), m.productId), L('Đã xóa mềm sản phẩm.', 'Product deleted.'));
        if (r.ok) { ui.modal = null; render(); }
        break;
      }
      case 'apply-all': {
        const approved = p.proposals.filter((x) => x.effective && x.state === 'APPROVED' && !x.applied);
        if (!approved.length) { toast(L('Không có giá đã duyệt nào chờ áp dụng.', 'No approved price to apply.'), true); break; }
        run(() => approved.forEach((x) => PM.applyPrice(ui.role, actor(), p.id, x.id)), L('Đã áp dụng ' + approved.length + ' giá.', approved.length + ' prices applied.'));
        break;
      }
      case 'submit-review': run(() => PM.submitReview(ui.role, actor(), p.id), L('Đã gửi toàn bộ hồ sơ cho Manager.', 'Review submitted.')); break;
      case 'decide-review': ui.modal = { type: 'decide-review', productId: p.id }; ui.error = null; render(); break;
      case 'approve-review': case 'reject-review': {
        const prices = {};
        document.querySelectorAll('[data-final]').forEach((input) => { prices[input.getAttribute('data-final')] = input.value; });
        const reason = document.getElementById('decisionReason').value;
        const r = run(() => PM.decideReview(ui.role, actor(), m.productId, action === 'approve-review' ? { decision: 'APPROVE', prices } : { decision: 'REJECT', reason }),
          action === 'approve-review' ? L('Đã duyệt hồ sơ. Tiếp theo: áp dụng giá rồi công khai.', 'Approved.') : L('Đã từ chối hồ sơ.', 'Rejected.'));
        if (r.ok) { ui.modal = null; render(); }
        break;
      }
      case 'approve-proposal': case 'reject-proposal': {
        const r = run(() => PM.decideProposal(ui.role, actor(), m.productId, m.proposalId, action === 'approve-proposal'
          ? { decision: 'APPROVE', approvedPrice: document.getElementById('approvedPrice').value }
          : { decision: 'REJECT', reason: document.getElementById('decisionReason').value }),
          action === 'approve-proposal' ? L('Đã duyệt giá. Bấm Áp dụng để đổi giá bán.', 'Approved; apply it next.') : L('Đã từ chối đề xuất.', 'Rejected.'));
        if (r.ok) { ui.modal = null; render(); }
        break;
      }
      case 'confirm-activation': {
        const c = PM.category(m.categoryId);
        const r = run(() => PM.setCategoryActive(ui.role, c.id, c.version, !c.active), c.active ? L('Đã ngừng danh mục.', 'Deactivated.') : L('Đã kích hoạt lại.', 'Reactivated.'));
        if (r.ok) { ui.modal = null; render(); }
        break;
      }
      case 'save-category': {
        const c = PM.category(m.categoryId);
        const r = run(() => PM.updateCategory(ui.role, c.id, c.version, document.getElementById('categoryName').value, document.getElementById('categoryDescription').value),
          (changed) => (changed ? L('Đã lưu danh mục.', 'Saved.') : L('Không có thay đổi.', 'No change.')));
        if (r.ok) { ui.modal = null; render(); }
        break;
      }
      case 'import-sample': ui.importText = PM.importTemplateText(); render(); break;
      case 'import-sample-errors':
        ui.importText = PM.importTemplateText() + '\n' + ['N3', 'Nhẫn lỗi', 'Thiếu danh mục', 'Không có', '4', '1.5', '-1'].join('\t')
          + '\n' + ['N1', 'Nhẫn bạc Lá khác tên', 'Bạc S925 họa tiết lá.', PM.state.categories.find((c) => c.active).name, '7', '720000', '1'].join('\t')
          + '\n' + ['N4', '=HYPERLINK("x")', 'Công thức', PM.state.categories.find((c) => c.active).name, '10', '100000', '1'].join('\t');
        render(); break;
      case 'import-confirm': ui.modal = { type: 'confirm-import' }; ui.error = null; render(); break;
      case 'confirm-import': {
        const b = PM.importBatch(actor(), ui.importBatchId);
        const r = run(() => PM.confirmImport(ui.role, actor(), b.id, b.version), L('Đã nhập sản phẩm.', 'Imported.'));
        ui.modal = null; render();
        void r;
        break;
      }
      case 'import-expire': run(() => PM.expireBatchNow(actor(), ui.importBatchId), L('Đã giả lập hết hạn.', 'Expired.')); break;
      case 'download-template': download('mau-import-san-pham.csv', PM.IMPORT_COLUMNS.join(',') + '\n'); break;
      case 'download-errors': {
        const b = PM.importBatch(actor(), ui.importBatchId);
        // Cells starting with = + - @ are neutralized like the real error report.
        const cell = (v) => { let s = String(v == null ? '' : v); if (/^[=+\-@]/.test(s)) s = "'" + s; return '"' + s.replace(/"/g, '""') + '"'; };
        download('bao-cao-loi.csv', [['Dòng', 'Cột', 'Giá trị', 'Mã lỗi', 'Thông báo'].map(cell).join(',')]
          .concat(b.errors.map((x) => [x.row, x.column, x.value, x.code, x.message].map(cell).join(','))).join('\n'));
        break;
      }
      case 'buy': {
        const v = PM.publicDetail(ui.publicId).variants.find((x) => x.id === ui.selectedVariant);
        toast(L('Chuyển sang FE-08 (chưa có trong prototype): sản phẩm ' + ui.publicId + ', size ' + v.size + ', số lượng 1.', 'Hand-off to FE-08 (not in this prototype).'));
        break;
      }
      case 'close-modal': ui.modal = null; ui.error = null; render(); break;
      default: break;
    }
  });

  function download(name, textContent) {
    const blob = new Blob(['﻿' + textContent], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  render();
})();
