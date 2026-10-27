/**
 * PPSWBS Prototype 002 — Product Management (FE-02): mock domain.
 *
 * Simulates the rules of ppswbs-spec/features/002-product-management in the browser so the screens can be
 * tried without a backend. Data lives in localStorage. This is NOT the backend and proves nothing about it:
 * no authentication, no FE-08 holds (held quantity is always 0 here), no Cloudinary, no Excel parsing.
 * Error codes mirror contracts/interfaces.md so the messages look like the real API ones.
 */
(function (global) {
  'use strict';

  const STATE_KEY = 'ppswbs_prototype_002_v1';
  const MAX_IMAGES = 10;
  const MIN_SIZE = 5;
  const MAX_SIZE = 29;
  const MAX_SIZES = 25;
  const PAGE_SIZE = 20;
  const IMPORT_TTL_MS = 60 * 60 * 1000;
  const IMPORT_COLUMNS = ['Mã nhóm', 'Tên sản phẩm', 'Mô tả', 'Danh mục', 'Size', 'Giá đề xuất (VND)', 'Số lượng'];
  const AMOUNT = /^[1-9][0-9]{0,18}$/;
  const QUANTITY = /^(0|[1-9][0-9]{0,18})$/;

  class DomainError extends Error {
    constructor(code, fieldErrors) {
      super(code);
      this.code = code;
      this.fieldErrors = fieldErrors || [];
    }
  }

  const fail = (code, fieldErrors) => { throw new DomainError(code, fieldErrors); };
  const invalid = (field, message) => fail('VALIDATION_ERROR', [{ field, message }]);
  const now = () => new Date().toISOString();
  const trim = (v) => (v == null ? '' : String(v).replace(/^[\s ﻿]+|[\s ﻿]+$/g, ''));
  const codePoints = (s) => Array.from(s).length;
  const nameKey = (name) => trim(name).normalize('NFC').toLowerCase();
  const pad = (n, w) => String(n).padStart(w, '0');

  function text(field, raw, max, required) {
    const value = trim(raw);
    if (!value) {
      if (required) invalid(field, 'required');
      return null;
    }
    if (codePoints(value) > max) invalid(field, 'max ' + max);
    return value;
  }
  function amount(field, raw) {
    const value = trim(raw);
    if (!AMOUNT.test(value)) invalid(field, 'amount');
    return value;
  }
  function quantity(field, raw) {
    const value = trim(raw);
    if (!QUANTITY.test(value)) invalid(field, 'quantity');
    return value;
  }
  const big = (s) => BigInt(s);

  // ---------------------------------------------------------------- state

  let state = null;

  function svgImage(label, hue) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400">
      <defs><radialGradient id="g" cx="50%" cy="40%" r="70%"><stop offset="0" stop-color="hsl(${hue},45%,92%)"/>
      <stop offset="1" stop-color="hsl(${hue},30%,70%)"/></radialGradient></defs>
      <rect width="400" height="400" fill="url(#g)"/>
      <circle cx="200" cy="215" r="92" fill="none" stroke="hsl(${hue},60%,38%)" stroke-width="22"/>
      <circle cx="200" cy="215" r="92" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="5"/>
      <path d="M176 110 L200 78 L224 110 L200 132 Z" fill="#fff" stroke="hsl(${hue},40%,45%)" stroke-width="3"/>
      <text x="200" y="372" text-anchor="middle" font-family="Inter,Arial" font-size="24" font-weight="700"
        fill="hsl(${hue},45%,25%)">${label}</text></svg>`;
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }

  function nextId(kind) {
    state.seq[kind] = (state.seq[kind] || 0) + 1;
    return state.seq[kind];
  }

  function history(product, actor, action, detail) {
    product.history.push({ at: now(), actor, action, detail: detail || '' });
  }

  function newMedia(uploadedBy, src, label) {
    const id = 'm' + nextId('media') + '-' + Math.random().toString(16).slice(2, 8);
    state.media[id] = { id, uploadedBy, src, label: label || '', state: 'READY', createdAt: now() };
    return id;
  }

  function seed() {
    state = {
      seq: {},
      categories: [],
      products: [],
      media: {},
      imports: [],
      receipts: {}
    };
    const cat = (name, description, active) => {
      const c = { id: nextId('category'), name, description, active, version: 0, key: nameKey(name) };
      state.categories.push(c);
      return c;
    };
    const wedding = cat('Nhẫn cưới', 'Nhẫn đôi cho ngày cưới.', true);
    const engagement = cat('Nhẫn đính hôn', 'Nhẫn cầu hôn đính đá.', true);
    const fashion = cat('Nhẫn thời trang', null, true);
    const legacy = cat('Bộ sưu tập 2023', 'Danh mục cũ đã ngừng.', false);

    const make = (actor, name, description, category, sizes, imageLabels, hue) => {
      const images = imageLabels.map((label, i) => ({
        mediaId: newMedia(actor, svgImage(label, hue + i * 18), label), altText: label, position: i
      }));
      const p = createDraft(actor, { name, description, categoryId: category.id, variants: sizes, images }, 'MANUAL');
      return p;
    };
    const publishAll = (p) => {
      submitReview('staff.lan', p.id);
      decideReview('manager.huy', p.id, { decision: 'APPROVE', prices: null });
      currentReviewProposals(p).forEach((proposal) => applyPrice('staff.lan', p.id, proposal.id));
      setPublication('staff.lan', p.id, true);
    };

    const p1 = make('staff.lan', 'Nhẫn cưới Vàng 18K Trơn', 'Nhẫn cưới vàng 18K bản trơn 3mm, hoàn thiện bóng gương.',
      wedding, [{ size: 9, price: '6500000', qty: '4' }, { size: 12, price: '6900000', qty: '2' }, { size: 15, price: '7200000', qty: '0' }],
      ['Mặt trước', 'Mặt bên'], 45);
    publishAll(p1);
    const p2 = make('staff.lan', 'Nhẫn đính hôn Kim cương Solitaire', 'Ổ chấu 6 chấu, kim cương tấm 5.4ly, vàng trắng 14K.',
      engagement, [{ size: 7, price: '28500000', qty: '1' }, { size: 10, price: '28500000', qty: '1' }],
      ['Solitaire'], 205);
    publishAll(p2);
    const p3 = make('staff.minh', 'Nhẫn bạc Xoắn Minimal', 'Bạc S925 xoắn mảnh, đeo hằng ngày.',
      fashion, [{ size: 6, price: '650000', qty: '10' }, { size: 8, price: '650000', qty: '6' }],
      ['Xoắn'], 300);
    publishAll(p3);
    // Independent price proposal waiting for a Manager after publication.
    const v3 = p3.variants[0];
    const draft = createProposal('staff.minh', p3.id, v3.id, '590000');
    submitProposal('staff.minh', p3.id, draft.id);

    const p4 = make('staff.minh', 'Nhẫn Ruby Halo', 'Ruby trung tâm, viền kim cương tấm halo.',
      engagement, [{ size: 8, price: '15900000', qty: '2' }], ['Ruby'], 350);
    submitReview('staff.minh', p4.id);

    make('staff.lan', 'Nhẫn đôi Khắc tên', 'Mẫu nháp chưa có ảnh, chưa gửi duyệt.', wedding,
      [{ size: 10, price: '4200000', qty: '3' }, { size: 16, price: '4400000', qty: '3' }], [], 0);

    const p6 = make('staff.lan', 'Nhẫn Ngọc trai Cổ điển', 'Ngọc trai Akoya, đã ẩn khỏi catalogue.',
      fashion, [{ size: 7, price: '3900000', qty: '2' }], ['Ngọc trai'], 30);
    publishAll(p6);
    setPublication('staff.lan', p6.id, false);

    const p7 = make('staff.lan', 'Nhẫn Sapphire 2023', 'Thuộc danh mục cũ đã ngừng hoạt động.',
      engagement, [{ size: 9, price: '12000000', qty: '1' }], ['Sapphire'], 230);
    publishAll(p7);
    p7.categoryId = legacy.id;
    void legacy;
    state.products.forEach((p) => { p.history.forEach((h) => { h.seeded = true; }); });
  }

  function load() {
    try {
      const saved = localStorage.getItem(STATE_KEY);
      if (saved) {
        state = JSON.parse(saved);
        return;
      }
    } catch (e) { /* fall back to seed */ }
    seed();
    save();
  }

  function save() {
    try {
      localStorage.setItem(STATE_KEY, JSON.stringify(state));
    } catch (e) {
      // Storage full (large uploaded images): keep working in memory.
      console.warn('Prototype storage full', e);
    }
  }

  function reset() {
    seed();
    save();
  }

  // ---------------------------------------------------------------- lookups

  const category = (id) => state.categories.find((c) => c.id === Number(id));
  const product = (id) => state.products.find((p) => p.id === Number(id));
  function requireProduct(id) {
    const p = product(id);
    if (!p) fail('PRODUCT_NOT_FOUND');
    return p;
  }
  function requireAlive(id) {
    const p = requireProduct(id);
    if (p.deletedAt) fail('PRODUCT_DELETED');
    return p;
  }
  const variant = (p, id) => p.variants.find((v) => v.id === Number(id));
  const proposal = (p, id) => p.proposals.find((x) => x.id === Number(id));
  const currentReview = (p) => p.reviews[p.reviews.length - 1];
  const currentReviewProposals = (p) => {
    const review = currentReview(p);
    return p.proposals.filter((x) => x.reviewId === review.id && x.effective);
  };
  const latestProposal = (p, variantId) => p.proposals
    .filter((x) => x.variantId === variantId)
    .reduce((a, b) => (!a || b.revision > a.revision || (b.revision === a.revision && b.id > a.id) ? b : a), null);
  const sku = (productId, size) => 'SP' + pad(productId, 6) + '-S' + pad(size, 2);
  const usableImages = (p) => p.images.filter((img) => state.media[img.mediaId] && state.media[img.mediaId].state === 'READY');

  function requireManage(role) {
    if (role !== 'STAFF' && role !== 'MANAGER') fail('CATALOGUE_ACCESS_DENIED');
  }
  function requireManager(role) {
    if (role !== 'MANAGER') fail('CATALOGUE_ACCESS_DENIED');
  }

  // ---------------------------------------------------------------- products

  function validateImages(images) {
    if (images.length > MAX_IMAGES) invalid('images', 'max 10');
    const ids = new Set();
    const positions = new Set();
    return images.map((img, i) => {
      if (!img.mediaId || ids.has(img.mediaId)) invalid('images[' + i + '].mediaId', 'duplicate');
      ids.add(img.mediaId);
      const position = Number(img.position);
      if (!Number.isInteger(position) || position < 0 || position >= MAX_IMAGES || positions.has(position)) {
        invalid('images[' + i + '].position', 'position');
      }
      positions.add(position);
      return { mediaId: img.mediaId, altText: text('images[' + i + '].altText', img.altText, 200, true), position };
    }).sort((a, b) => a.position - b.position);
  }

  function attachable(actor, mediaId) {
    const media = state.media[mediaId];
    if (!media || media.state !== 'READY' || media.uploadedBy !== actor) fail('MEDIA_NOT_FOUND');
  }

  /** Shared core of manual creation and import confirmation (CreateProductService.createDraft). */
  function createDraft(actor, input, source) {
    const name = text('name', input.name, 200, true);
    const description = text('description', input.description, 5000, true);
    const cat = category(input.categoryId);
    if (!cat || !cat.active) invalid('categoryId', 'inactive');
    const sizes = input.variants || [];
    if (sizes.length < 1 || sizes.length > MAX_SIZES) invalid('variants', '1-25');
    const seen = new Set();
    const variants = sizes.map((v, i) => {
      const size = Number(v.size);
      if (!Number.isInteger(size) || size < MIN_SIZE || size > MAX_SIZE) invalid('variants[' + i + '].size', '5-29');
      if (seen.has(size)) invalid('variants[' + i + '].size', 'duplicate');
      seen.add(size);
      return { size, price: amount('variants[' + i + '].proposedPrice', v.price), qty: quantity('variants[' + i + '].quantity', v.qty) };
    }).sort((a, b) => a.size - b.size);
    const images = validateImages(input.images || []);
    images.forEach((img) => attachable(actor, img.mediaId));

    const id = nextId('product');
    const p = {
      id, name, description, categoryId: cat.id, published: false, firstPublishedAt: null,
      deletedAt: null, deletedBy: null, contentVersion: 0, version: 0, createdAt: now(), source,
      images, variants: [], reviews: [], proposals: [], applications: [], history: []
    };
    p.variants = variants.map((v) => ({
      id: nextId('variant'), size: v.size, sku: sku(id, v.size), remaining: v.qty, saleEnabled: true,
      appliedPrice: null, appliedProposalId: null, appliedSequence: 0, pendingProposalId: null, version: 0
    }));
    const review = { id: nextId('review'), revision: 1, contentVersion: 0, state: 'DRAFT', effective: true };
    p.reviews.push(review);
    p.variants.forEach((v, i) => p.proposals.push(newProposal(v, review.id, 1, variants[i].price, null)));
    state.products.push(p);
    history(p, actor, 'product.create', source === 'IMPORT' ? 'Import Excel' : '');
    return p;
  }

  function newProposal(v, reviewId, revision, proposed, supersedesId) {
    return {
      id: nextId('proposal'), variantId: v.id, reviewId, revision, state: 'DRAFT', effective: true,
      proposed: String(proposed), approved: null, baseSequence: v.appliedSequence, reason: null,
      supersedesId, version: 0, decidedBy: null, applied: false
    };
  }

  /** ProductReviewInvalidationService: before the first publication a change starts a new DRAFT revision. */
  function invalidateBeforeFirstPublication(p, priceChanges) {
    if (p.firstPublishedAt) return;
    const current = currentReview(p);
    const stillDraft = current.state === 'DRAFT' && current.effective;
    let target = current;
    if (stillDraft) {
      current.contentVersion = p.contentVersion;
    } else {
      current.effective = false;
      target = { id: nextId('review'), revision: current.revision + 1, contentVersion: p.contentVersion, state: 'DRAFT', effective: true };
      p.reviews.push(target);
    }
    p.variants.forEach((v) => {
      const old = p.proposals.filter((x) => x.reviewId === current.id && x.variantId === v.id && x.effective)
        .sort((a, b) => b.revision - a.revision)[0];
      const changed = priceChanges[v.id];
      const priceChanged = changed != null && (!old || old.proposed !== String(changed));
      if (stillDraft && !priceChanged) return;
      const value = changed != null ? changed : old && old.proposed;
      if (value == null) return;
      if (old) old.effective = false;
      v.pendingProposalId = null;
      p.proposals.push(newProposal(v, target.id, old ? old.revision + 1 : 1, value, old ? old.id : null));
    });
  }

  function updateContent(role, actor, productId, expectedVersion, input) {
    requireManage(role);
    const p = requireAlive(productId);
    if (p.version !== expectedVersion) fail('STALE_VERSION');
    const name = text('name', input.name, 200, true);
    const description = text('description', input.description, 5000, true);
    const target = category(input.categoryId);
    if (!target) fail('CATEGORY_NOT_FOUND');
    const moving = target.id !== p.categoryId;
    if (moving && !target.active) fail('CATEGORY_NOT_FOUND');
    if (name === p.name && description === p.description && !moving) return false;
    p.name = name;
    p.description = description;
    p.categoryId = target.id;
    p.contentVersion += 1;
    p.version += 1;
    invalidateBeforeFirstPublication(p, {});
    history(p, actor, 'product.edit', moving ? 'category → ' + target.name : '');
    return true;
  }

  function replaceImages(role, actor, productId, expectedVersion, images) {
    requireManage(role);
    const p = requireAlive(productId);
    if (p.version !== expectedVersion) fail('STALE_VERSION');
    const wanted = validateImages(images);
    const same = wanted.length === p.images.length && wanted.every((w, i) =>
      w.mediaId === p.images[i].mediaId && w.altText === p.images[i].altText && w.position === p.images[i].position);
    if (same) return false;
    const currentIds = new Set(p.images.map((img) => img.mediaId));
    wanted.filter((w) => !currentIds.has(w.mediaId)).forEach((w) => attachable(actor, w.mediaId));
    if (p.published && !wanted.some((w) => state.media[w.mediaId] && state.media[w.mediaId].state === 'READY')) {
      fail('PUBLICATION_PREREQUISITES_MISSING', [{ field: 'images', message: 'published needs an image' }]);
    }
    p.images = wanted;
    p.contentVersion += 1;
    p.version += 1;
    invalidateBeforeFirstPublication(p, {});
    history(p, actor, 'product.images', wanted.length + ' ảnh');
    return true;
  }

  function uploadMedia(role, actor, dataUrl, label) {
    requireManage(role);
    return newMedia(actor, dataUrl, label);
  }

  // ---------------------------------------------------------------- review

  function submitReview(actor, productId) {
    const p = requireAlive(productId);
    if (p.firstPublishedAt) fail('REVIEW_NOT_CURRENT');
    const review = currentReview(p);
    if (review.state !== 'DRAFT' || !review.effective) fail('REVIEW_NOT_CURRENT');
    const missing = [];
    const cat = category(p.categoryId);
    if (!cat || !cat.active) missing.push({ field: 'categoryId', message: 'category' });
    if (usableImages(p).length === 0) missing.push({ field: 'images', message: 'images' });
    const drafts = currentReviewProposals(p).filter((x) => x.state === 'DRAFT');
    p.variants.forEach((v) => {
      if (v.pendingProposalId) fail('PENDING_PRICE_PROPOSAL_EXISTS');
      if (!drafts.some((x) => x.variantId === v.id)) missing.push({ field: 'variants[' + v.sku + ']', message: 'price' });
    });
    if (missing.length) fail('VALIDATION_ERROR', missing);
    review.state = 'PENDING';
    review.submittedBy = actor;
    review.submittedAt = now();
    review.snapshot = { name: p.name, categoryName: cat.name, images: p.images.length };
    drafts.forEach((x) => {
      x.state = 'PENDING';
      variant(p, x.variantId).pendingProposalId = x.id;
    });
    history(p, actor, 'product.review.submit', 'rev ' + review.revision);
  }

  function decideReview(actor, productId, command) {
    const p = requireAlive(productId);
    const review = currentReview(p);
    if (review.state !== 'PENDING' || !review.effective || review.contentVersion !== p.contentVersion) fail('REVIEW_NOT_CURRENT');
    const pending = currentReviewProposals(p).filter((x) => x.state === 'PENDING');
    if (command.decision === 'APPROVE') {
      const finals = {};
      pending.forEach((x) => {
        const raw = command.prices ? command.prices[x.id] : x.proposed;
        finals[x.id] = amount('prices[' + variant(p, x.variantId).sku + ']', raw);
      });
      review.state = 'APPROVED';
      pending.forEach((x) => {
        x.state = 'APPROVED';
        x.approved = finals[x.id];
        x.decidedBy = actor;
        variant(p, x.variantId).pendingProposalId = null;
      });
      history(p, actor, 'product.review.approve', 'rev ' + review.revision);
    } else {
      const reason = text('reason', command.reason, 2000, true);
      review.state = 'REJECTED';
      review.reason = reason;
      pending.forEach((x) => {
        x.state = 'REJECTED';
        x.reason = reason;
        x.decidedBy = actor;
        variant(p, x.variantId).pendingProposalId = null;
      });
      history(p, actor, 'product.review.reject', reason);
    }
    review.decidedBy = actor;
    review.decidedAt = now();
  }

  // ---------------------------------------------------------------- prices

  function applyPrice(actor, productId, proposalId) {
    const p = requireAlive(productId);
    const x = proposal(p, proposalId);
    if (!x) fail('PROPOSAL_NOT_FOUND');
    if (x.applied) return false;
    if (!x.effective || x.state !== 'APPROVED') fail('PRICE_NOT_APPROVED');
    if (!p.firstPublishedAt) {
      const review = p.reviews.find((r) => r.id === x.reviewId);
      if (!review || review.state !== 'APPROVED' || !review.effective || review.contentVersion !== p.contentVersion) {
        fail('REVIEW_NOT_CURRENT');
      }
    }
    const v = variant(p, x.variantId);
    if (x.baseSequence !== v.appliedSequence) fail('PRICE_APPLICATION_STALE');
    p.applications.push({ variantId: v.id, proposalId: x.id, oldAmount: v.appliedPrice, newAmount: x.approved, at: now(), actor });
    v.appliedPrice = x.approved;
    v.appliedProposalId = x.id;
    v.appliedSequence += 1;
    x.applied = true;
    history(p, actor, 'price.apply', v.sku + ': ' + x.approved);
    return true;
  }

  function createProposal(actor, productId, variantId, proposed) {
    const p = requireAlive(productId);
    const v = variant(p, variantId);
    if (!v) fail('VARIANT_NOT_FOUND');
    const value = amount('proposedPrice', proposed);
    if (v.pendingProposalId) fail('PENDING_PRICE_PROPOSAL_EXISTS');
    return newRevision(actor, p, v, latestProposal(p, v.id), value);
  }

  function reviseProposal(actor, productId, proposalId, proposed) {
    const p = requireAlive(productId);
    const x = proposal(p, proposalId);
    if (!x) fail('PROPOSAL_NOT_FOUND');
    const value = amount('proposedPrice', proposed);
    if (latestProposal(p, x.variantId) !== x || !x.effective) fail('STALE_VERSION');
    return newRevision(actor, p, variant(p, x.variantId), x, value);
  }

  function newRevision(actor, p, v, previous, value) {
    if (!p.firstPublishedAt) {
      invalidateBeforeFirstPublication(p, { [v.id]: value });
      history(p, actor, 'price.propose', v.sku + ': ' + value + ' (hồ sơ duyệt)');
      return latestProposal(p, v.id);
    }
    if (previous && previous.effective && previous.id !== v.appliedProposalId) previous.effective = false;
    v.pendingProposalId = null;
    const created = newProposal(v, null, previous ? previous.revision + 1 : 1, value, previous ? previous.id : null);
    p.proposals.push(created);
    history(p, actor, 'price.propose', v.sku + ': ' + value);
    return created;
  }

  function submitProposal(actor, productId, proposalId) {
    const p = requireAlive(productId);
    const x = proposal(p, proposalId);
    if (!x) fail('PROPOSAL_NOT_FOUND');
    if (x.reviewId != null) fail('REVIEW_NOT_CURRENT');
    const v = variant(p, x.variantId);
    if (latestProposal(p, v.id) !== x || !x.effective || x.state !== 'DRAFT') fail('STALE_VERSION');
    if (v.pendingProposalId) fail('PENDING_PRICE_PROPOSAL_EXISTS');
    x.state = 'PENDING';
    v.pendingProposalId = x.id;
    history(p, actor, 'price.submit', v.sku + ': ' + x.proposed);
  }

  function decideProposal(actor, productId, proposalId, command) {
    const p = requireAlive(productId);
    const x = proposal(p, proposalId);
    if (!x) fail('PROPOSAL_NOT_FOUND');
    if (x.reviewId != null) fail('REVIEW_NOT_CURRENT');
    const v = variant(p, x.variantId);
    if (!x.effective || x.state !== 'PENDING' || v.pendingProposalId !== x.id) fail('STALE_VERSION');
    if (command.decision === 'APPROVE') {
      x.approved = amount('approvedPrice', command.approvedPrice);
      x.state = 'APPROVED';
    } else {
      x.reason = text('reason', command.reason, 2000, true);
      x.state = 'REJECTED';
    }
    x.decidedBy = actor;
    v.pendingProposalId = null;
    history(p, actor, 'price.decide', v.sku + ': ' + x.state + (x.approved ? ' ' + x.approved : ''));
  }

  // ---------------------------------------------------------------- publication, sale, quantity, delete

  function publicationMissing(p) {
    const missing = [];
    const cat = category(p.categoryId);
    if (!cat || !cat.active) missing.push({ field: 'categoryId', message: 'category' });
    if (usableImages(p).length === 0) missing.push({ field: 'images', message: 'images' });
    let review = null;
    if (!p.firstPublishedAt) {
      const r = currentReview(p);
      review = r.state === 'APPROVED' && r.effective && r.contentVersion === p.contentVersion ? r : null;
      if (!review) missing.push({ field: 'review', message: 'review' });
    }
    p.variants.forEach((v) => {
      const applied = v.appliedProposalId && proposal(p, v.appliedProposalId);
      const ok = v.appliedPrice && applied && applied.state === 'APPROVED'
        && (p.firstPublishedAt || (review && applied.reviewId === review.id));
      if (!ok) missing.push({ field: 'variants[' + v.sku + '].appliedPrice', message: 'price' });
    });
    return missing;
  }

  function setPublication(actor, productId, published) {
    const p = requireAlive(productId);
    if (p.published === published) return false;
    if (published) {
      const missing = publicationMissing(p);
      if (missing.length) fail('PUBLICATION_PREREQUISITES_MISSING', missing);
      p.published = true;
      if (!p.firstPublishedAt) p.firstPublishedAt = now();
    } else {
      p.published = false;
    }
    p.version += 1;
    history(p, actor, published ? 'product.publish' : 'product.unpublish');
    return true;
  }

  function setSaleEnabled(role, actor, productId, variantId, enabled) {
    requireManage(role);
    const p = requireAlive(productId);
    const v = variant(p, variantId);
    if (v.saleEnabled === enabled) return false;
    if (enabled && p.firstPublishedAt && !v.appliedPrice) {
      fail('PUBLICATION_PREREQUISITES_MISSING', [{ field: 'variants[' + v.sku + '].appliedPrice', message: 'price' }]);
    }
    v.saleEnabled = enabled;
    v.version += 1;
    history(p, actor, 'variant.sale-status', v.sku + ': ' + (enabled ? 'ENABLED' : 'DISABLED'));
    return true;
  }

  function setQuantity(role, actor, productId, variantId, raw) {
    requireManage(role);
    const p = requireAlive(productId);
    const v = variant(p, variantId);
    const value = quantity('quantity', raw);
    if (v.remaining === value) return false;
    // Prototype: FE-08 holds do not exist, so the held quantity is 0. The real backend refuses a value
    // below the held quantity (QUANTITY_BELOW_HELD) and returns 503 while FE-08 is unavailable.
    const old = v.remaining;
    v.remaining = value;
    v.version += 1;
    history(p, actor, 'variant.quantity', v.sku + ': ' + old + ' → ' + value);
    return true;
  }

  function deleteProduct(role, actor, productId) {
    requireManage(role);
    const p = requireProduct(productId);
    if (p.deletedAt) return false;
    p.deletedAt = now();
    p.deletedBy = actor;
    p.published = false;
    p.version += 1;
    history(p, actor, 'product.delete');
    return true;
  }

  // ---------------------------------------------------------------- categories

  function createCategory(role, name, description) {
    requireManage(role);
    const value = text('name', name, 200, true);
    const desc = text('description', description, 5000, false);
    if (state.categories.some((c) => c.key === nameKey(value))) fail('DUPLICATE_CATEGORY_NAME');
    const c = { id: nextId('category'), name: value, description: desc, active: true, version: 0, key: nameKey(value) };
    state.categories.push(c);
    return c;
  }

  function updateCategory(role, id, expectedVersion, name, description) {
    requireManage(role);
    const c = category(id);
    if (!c) fail('CATEGORY_NOT_FOUND');
    if (c.version !== expectedVersion) fail('STALE_VERSION');
    const value = text('name', name, 200, true);
    const desc = text('description', description, 5000, false);
    if (nameKey(value) !== c.key && state.categories.some((o) => o.key === nameKey(value))) fail('DUPLICATE_CATEGORY_NAME');
    if (value === c.name && desc === c.description) return false;
    c.name = value;
    c.description = desc;
    c.key = nameKey(value);
    c.version += 1;
    return true;
  }

  function categoryImpact(id) {
    return state.products.filter((p) => !p.deletedAt && p.categoryId === Number(id));
  }

  function setCategoryActive(role, id, expectedVersion, active) {
    requireManage(role);
    const c = category(id);
    if (!c) fail('CATEGORY_NOT_FOUND');
    if (c.active === active) return false;
    if (c.version !== expectedVersion) fail('STALE_VERSION');
    c.active = active;
    c.version += 1;
    return true;
  }

  // ---------------------------------------------------------------- import (pasted rows instead of .xlsx)

  function importTemplateText() {
    const activeNames = state.categories.filter((c) => c.active).map((c) => c.name);
    return [IMPORT_COLUMNS.join('\t'),
      ['N1', 'Nhẫn bạc Lá nguyệt quế', 'Bạc S925 họa tiết lá.', activeNames[2] || activeNames[0], '7', '720000', '5'].join('\t'),
      ['N1', 'Nhẫn bạc Lá nguyệt quế', 'Bạc S925 họa tiết lá.', activeNames[2] || activeNames[0], '9', '720000', '3'].join('\t'),
      ['N2', 'Nhẫn cưới Vàng hồng', 'Vàng hồng 14K bản 2mm.', activeNames[0], '11', '5200000', '2'].join('\t')
    ].join('\n');
  }

  function validateImport(role, actor, fileName, rawText) {
    requireManage(role);
    const lines = String(rawText || '').replace(/\r/g, '').split('\n');
    while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
    if (!lines.length) fail('IMPORT_FILE_INVALID');
    const header = lines[0].split('\t').map(trim);
    if (header.length !== IMPORT_COLUMNS.length || header.some((h, i) => h !== IMPORT_COLUMNS[i])) fail('IMPORT_FILE_INVALID');
    const rows = lines.slice(1);
    if (rows.length > 500) fail('IMPORT_ROW_LIMIT_EXCEEDED');

    const errors = [];
    const err = (row, column, value, code, message) => errors.push({ row, column, value: value == null ? null : String(value).slice(0, 200), code, message });
    const activeByKey = {};
    state.categories.filter((c) => c.active).forEach((c) => { activeByKey[c.key] = c; });
    const groups = new Map();
    rows.forEach((line, index) => {
      const rowNumber = index + 2;
      const cells = line.split('\t');
      if (!line.trim()) {
        err(rowNumber, IMPORT_COLUMNS[0], null, 'EMPTY_ROW_IN_DATA', 'Dòng trống giữa dữ liệu.');
        return;
      }
      const before = errors.length;
      const cell = (i, max) => {
        const value = trim(cells[i]);
        if (!value) { err(rowNumber, IMPORT_COLUMNS[i], null, 'REQUIRED', 'Bắt buộc.'); return null; }
        if (value.startsWith('=')) { err(rowNumber, IMPORT_COLUMNS[i], value, 'FORMULA_NOT_SUPPORTED', 'Không hỗ trợ công thức.'); return null; }
        if (codePoints(value) > max) { err(rowNumber, IMPORT_COLUMNS[i], value, 'TOO_LONG', 'Tối đa ' + max + ' ký tự.'); return null; }
        return value;
      };
      const groupKey = cell(0, 100);
      const name = cell(1, 200);
      const description = cell(2, 5000);
      const catName = cell(3, 200);
      const sizeRaw = cell(4, 20);
      const priceRaw = cell(5, 25);
      const qtyRaw = cell(6, 25);
      let cat = null;
      if (catName) {
        cat = activeByKey[nameKey(catName)];
        if (!cat) err(rowNumber, IMPORT_COLUMNS[3], catName, 'CATEGORY_NOT_ACTIVE_OR_UNKNOWN', 'Danh mục không tồn tại hoặc đã ngừng.');
      }
      let size = null;
      if (sizeRaw) {
        if (!/^(0|[1-9][0-9]*)$/.test(sizeRaw)) err(rowNumber, IMPORT_COLUMNS[4], sizeRaw, 'INVALID_NUMBER', 'Số nguyên.');
        else if (Number(sizeRaw) < MIN_SIZE || Number(sizeRaw) > MAX_SIZE) err(rowNumber, IMPORT_COLUMNS[4], sizeRaw, 'OUT_OF_RANGE', 'Size 5–29.');
        else size = Number(sizeRaw);
      }
      if (priceRaw) {
        if (!QUANTITY.test(priceRaw)) err(rowNumber, IMPORT_COLUMNS[5], priceRaw, 'INVALID_NUMBER', 'Số nguyên VND.');
        else if (!AMOUNT.test(priceRaw)) err(rowNumber, IMPORT_COLUMNS[5], priceRaw, 'OUT_OF_RANGE', 'Giá phải lớn hơn 0.');
      }
      if (qtyRaw && !QUANTITY.test(qtyRaw)) err(rowNumber, IMPORT_COLUMNS[6], qtyRaw, 'INVALID_NUMBER', 'Số nguyên không âm.');
      if (errors.length > before || !groupKey) return;
      let group = groups.get(groupKey);
      if (!group) {
        group = { groupKey, name, description, categoryId: cat.id, categoryName: cat.name, variants: [] };
        groups.set(groupKey, group);
      } else if (group.name !== name || group.description !== description || group.categoryId !== cat.id) {
        err(rowNumber, IMPORT_COLUMNS[0], groupKey, 'GROUP_FIELD_MISMATCH', 'Các dòng cùng nhóm phải trùng tên/mô tả/danh mục.');
        return;
      }
      if (group.variants.some((v) => v.size === size)) {
        err(rowNumber, IMPORT_COLUMNS[4], size, 'DUPLICATE_SIZE_IN_GROUP', 'Size trùng trong nhóm.');
        return;
      }
      if (group.variants.length >= MAX_SIZES) {
        err(rowNumber, IMPORT_COLUMNS[4], size, 'TOO_MANY_SIZES', 'Tối đa 25 size mỗi nhóm.');
        return;
      }
      group.variants.push({ row: rowNumber, size, price: priceRaw, qty: qtyRaw });
    });

    const batch = {
      id: 'b' + nextId('import') + '-' + Math.random().toString(16).slice(2, 10),
      actor, fileName: trim(fileName) || 'san-pham.xlsx', createdAt: now(),
      expiresAt: new Date(Date.now() + IMPORT_TTL_MS).toISOString(),
      rowCount: rows.length, errors, version: 0,
      state: errors.length ? 'REJECTED' : 'VALIDATED',
      preview: errors.length ? null : Array.from(groups.values()),
      created: null
    };
    state.imports.unshift(batch);
    return batch;
  }

  function importBatch(actor, batchId) {
    const batch = state.imports.find((b) => b.id === batchId && b.actor === actor);
    if (!batch) fail('IMPORT_BATCH_NOT_FOUND');
    if (batch.state === 'VALIDATED' && Date.now() >= Date.parse(batch.expiresAt)) {
      batch.state = 'EXPIRED';
      batch.preview = null;
      batch.errors = [];
    }
    return batch;
  }

  function confirmImport(role, actor, batchId, expectedVersion) {
    requireManage(role);
    const batch = importBatch(actor, batchId);
    if (batch.state === 'IMPORTED') return batch;
    if (batch.state === 'REJECTED') fail('IMPORT_HAS_ERRORS');
    if (batch.state === 'EXPIRED') fail('IMPORT_BATCH_EXPIRED');
    if (batch.version !== expectedVersion) fail('STALE_VERSION');
    const stale = [];
    batch.preview.forEach((g) => {
      const c = category(g.categoryId);
      if (!c || !c.active) g.variants.forEach((v) => stale.push({ row: v.row, column: IMPORT_COLUMNS[3], value: g.categoryName, code: 'CATEGORY_NOT_ACTIVE_OR_UNKNOWN', message: 'Danh mục đã ngừng sau bước kiểm tra.' }));
    });
    if (stale.length) {
      batch.state = 'REJECTED';
      batch.errors = stale;
      batch.preview = null;
      batch.version += 1;
      fail('IMPORT_PREVIEW_STALE');
    }
    batch.created = batch.preview.map((g) => {
      const p = createDraft(actor, {
        name: g.name, description: g.description, categoryId: g.categoryId,
        variants: g.variants.map((v) => ({ size: v.size, price: v.price, qty: v.qty })), images: []
      }, 'IMPORT');
      return { groupKey: g.groupKey, productId: p.id, skus: p.variants.map((v) => v.sku) };
    });
    batch.state = 'IMPORTED';
    batch.importedAt = now();
    batch.preview = null;
    batch.version += 1;
    return batch;
  }

  function expireBatchNow(actor, batchId) {
    const batch = importBatch(actor, batchId);
    batch.expiresAt = new Date(Date.now() - 1000).toISOString();
    return importBatch(actor, batchId);
  }

  // ---------------------------------------------------------------- management list & public catalogue

  function managementList(filter) {
    const q = trim(filter.q).toLowerCase();
    const upper = q.toUpperCase();
    const number = /^(SP)?0*([1-9][0-9]*)$/.exec(upper);
    const items = state.products.filter((p) => !p.deletedAt)
      .filter((p) => !filter.categoryId || p.categoryId === Number(filter.categoryId))
      .filter((p) => filter.published == null || filter.published === '' || String(p.published) === filter.published)
      .filter((p) => !q || p.name.toLowerCase().includes(q) || (number && p.id === Number(number[2]))
        || p.variants.some((v) => v.sku === upper))
      .sort((a, b) => (b.createdAt.localeCompare(a.createdAt)) || b.id - a.id);
    return page(items, filter.page);
  }

  function page(items, pageNumber) {
    const n = Math.max(0, Number(pageNumber) || 0);
    return { items: items.slice(n * PAGE_SIZE, n * PAGE_SIZE + PAGE_SIZE), page: n, total: items.length, pages: Math.max(1, Math.ceil(items.length / PAGE_SIZE)) };
  }

  function publicEligible(p) {
    const c = category(p.categoryId);
    return p.published && !p.deletedAt && c && c.active && usableImages(p).length > 0 && p.variants.every((v) => v.appliedPrice);
  }

  // Prototype: no FE-08 holds, so available = remaining unsold.
  const canBuyVariant = (v) => v.saleEnabled && big(v.remaining) > 0n;

  function publicList(filter) {
    const q = trim(filter.q);
    if (codePoints(q) > 200) invalid('q', 'max 200');
    if (filter.categoryId) {
      const c = category(filter.categoryId);
      if (!c || !c.active) invalid('categoryId', 'inactive');
    }
    const min = trim(filter.min) ? quantity('minimumPrice', filter.min) : null;
    const max = trim(filter.max) ? quantity('maximumPrice', filter.max) : null;
    if (min != null && max != null && big(min) > big(max)) invalid('minimumPrice', 'min > max');
    const sort = filter.sort || 'name-asc';
    if (!['name-asc', 'price-asc', 'price-desc', 'newest'].includes(sort)) fail('INVALID_SORT');
    const needle = q.toLowerCase();
    const collator = new Intl.Collator('vi', { sensitivity: 'accent' });
    const rows = [];
    state.products.filter(publicEligible)
      .filter((p) => !filter.categoryId || p.categoryId === Number(filter.categoryId))
      .filter((p) => !needle || p.name.toLowerCase().includes(needle) || p.description.toLowerCase().includes(needle))
      .forEach((p) => {
        const matching = p.variants.filter((v) => (min == null || big(v.appliedPrice) >= big(min))
          && (max == null || big(v.appliedPrice) <= big(max)) && (!filter.canBuy || canBuyVariant(v)));
        if (!matching.length) return;
        const lowest = matching.map((v) => big(v.appliedPrice)).reduce((a, b) => (b < a ? b : a));
        rows.push({ p, lowest });
      });
    const byId = (a, b) => a.p.id - b.p.id;
    const comparators = {
      'name-asc': (a, b) => collator.compare(a.p.name, b.p.name) || byId(a, b),
      'price-asc': (a, b) => (a.lowest < b.lowest ? -1 : a.lowest > b.lowest ? 1 : 0) || byId(a, b),
      'price-desc': (a, b) => (a.lowest > b.lowest ? -1 : a.lowest < b.lowest ? 1 : 0) || byId(a, b),
      newest: (a, b) => b.p.createdAt.localeCompare(a.p.createdAt) || b.p.id - a.p.id
    };
    rows.sort(comparators[sort]);
    return page(rows.map((r) => publicView(r.p)), filter.page);
  }

  function publicView(p) {
    const prices = p.variants.map((v) => big(v.appliedPrice));
    const min = prices.reduce((a, b) => (b < a ? b : a));
    const maxPrice = prices.reduce((a, b) => (b > a ? b : a));
    const variants = p.variants.map((v) => ({ id: v.id, size: v.size, sku: v.sku, appliedPrice: v.appliedPrice, saleEnabled: v.saleEnabled, canBuy: canBuyVariant(v) }));
    return {
      id: p.id, name: p.name, description: p.description,
      category: { id: p.categoryId, name: category(p.categoryId).name },
      images: usableImages(p).map((img) => ({ src: state.media[img.mediaId].src, altText: img.altText, position: img.position })),
      variants, priceRange: { minimum: String(min), maximum: String(maxPrice) },
      canBuy: variants.some((v) => v.canBuy), createdAt: p.createdAt
    };
  }

  function publicDetail(id) {
    const p = product(id);
    if (!p || !publicEligible(p)) fail('PRODUCT_NOT_FOUND');
    return publicView(p);
  }

  function toggleMediaAvailability(productId, mediaId) {
    const media = state.media[mediaId];
    if (!media) fail('MEDIA_NOT_FOUND');
    media.state = media.state === 'READY' ? 'UNAVAILABLE' : 'READY';
    return media.state;
  }

  global.PM = {
    DomainError, PAGE_SIZE, MAX_IMAGES, IMPORT_COLUMNS,
    load, save, reset, get state() { return state; },
    category, product, variant, proposal, currentReview, currentReviewProposals, latestProposal, usableImages,
    publicationMissing, publicEligible,
    createDraft: (role, actor, input) => { requireManage(role); return createDraft(actor, input, 'MANUAL'); },
    updateContent, replaceImages, uploadMedia,
    submitReview: (role, actor, id) => { requireManage(role); return submitReview(actor, id); },
    decideReview: (role, actor, id, command) => { requireManager(role); return decideReview(actor, id, command); },
    applyPrice: (role, actor, id, proposalId) => { requireManage(role); return applyPrice(actor, id, proposalId); },
    createProposal: (role, actor, id, variantId, value) => { requireManage(role); return createProposal(actor, id, variantId, value); },
    reviseProposal: (role, actor, id, proposalId, value) => { requireManage(role); return reviseProposal(actor, id, proposalId, value); },
    submitProposal: (role, actor, id, proposalId) => { requireManage(role); return submitProposal(actor, id, proposalId); },
    decideProposal: (role, actor, id, proposalId, command) => { requireManager(role); return decideProposal(actor, id, proposalId, command); },
    setPublication: (role, actor, id, published) => { requireManage(role); return setPublication(actor, id, published); },
    setSaleEnabled, setQuantity, deleteProduct,
    createCategory, updateCategory, categoryImpact, setCategoryActive,
    importTemplateText, validateImport, importBatch, confirmImport, expireBatchNow,
    managementList, publicList, publicDetail, toggleMediaAvailability
  };
})(window);
