/**
 * PPSWBS Prototype 006 — Loyalty & Promotion (FE-06): mock domain.
 *
 * Simulates the rules of ppswbs-spec/features/006-loyalty-promotion in the browser so the screens can be tried
 * without a backend. Data lives in localStorage. This is NOT the backend and proves nothing about it: no
 * authentication, no FE-08 checkout or VNPay, no FE-03 booking or deposit, no FE-02 catalogue. The "source owner"
 * outcomes (paid, expired, deposit verified, completed, cancelled) are buttons that stand in for FE-08/FE-03.
 * Error codes mirror contracts/interfaces.md and LoyaltyErrorCode so the messages look like the real API ones.
 */
(function (global) {
  'use strict';

  const STATE_KEY = 'ppswbs_prototype_006_v1';
  const PAGE_SIZE = 20;
  const DAY = 86400000;
  const AMOUNT = /^(0|[1-9][0-9]{0,15})$/;
  const POSITIVE = /^[1-9][0-9]{0,15}$/;
  const PERCENT = /^(100|[1-9][0-9]?)(\.[0-9]{1,2})?$|^0\.([1-9][0-9]?|0[1-9])$/;

  class DomainError extends Error {
    constructor(code, fieldErrors, detail) {
      super(code);
      this.code = code;
      this.fieldErrors = fieldErrors || [];
      this.detail = detail || null;
    }
  }

  const fail = (code, fieldErrors, detail) => { throw new DomainError(code, fieldErrors, detail); };
  const invalid = (field, message) => fail('INVALID_INPUT', [{ field, message }]);
  const nowIso = () => new Date().toISOString();
  const trim = (v) => (v == null ? '' : String(v).replace(/^[\s ﻿]+|[\s ﻿]+$/g, ''));
  const codeKey = (code) => trim(code).toUpperCase();
  const floorDiv = (a, b) => Math.floor(a / b);

  // ---------------------------------------------------------------- state

  let state = null;

  function nextId(kind) {
    state.seq[kind] = (state.seq[kind] || 0) + 1;
    return state.seq[kind];
  }

  function seed() {
    const t = Date.now();
    const iso = (offsetDays) => new Date(t + offsetDays * DAY).toISOString();
    const day = (offsetDays) => new Date(t + offsetDays * DAY).toISOString().slice(0, 10);
    state = {
      seq: { program: 8, revision: 8, benefit: 0, ledger: 3, policy: 1, receipt: 0, source: 0 },
      // Read-only stand-ins for FE-02 (catalogue) and FE-03 (workshop packages); FE-06 does not own them.
      products: [
        { id: 1, name: 'Nhẫn cưới Vàng 18K Trơn', public: true, deleted: false, variants: [
          { id: 101, sku: 'SP000001-S09', size: 9, price: 2500000, onSale: true },
          { id: 102, sku: 'SP000001-S12', size: 12, price: 2600000, onSale: true }] },
        { id: 2, name: 'Nhẫn bạc Lá', public: true, deleted: false, variants: [
          { id: 103, sku: 'SP000002-S07', size: 7, price: 720000, onSale: true },
          { id: 104, sku: 'SP000002-S10', size: 10, price: 720000, onSale: true }] },
        { id: 3, name: 'Nhẫn Ruby Halo', public: false, deleted: false, variants: [
          { id: 105, sku: 'SP000003-S10', size: 10, price: 5800000, onSale: true }] },
        { id: 4, name: 'Nhẫn bạc Trơn (đã xóa)', public: false, deleted: true, variants: [
          { id: 106, sku: 'SP000004-S08', size: 8, price: 350000, onSale: false }] },
        { id: 5, name: 'Nhẫn đôi Bạc khắc tên', public: true, deleted: false, variants: [
          { id: 107, sku: 'SP000005-S11', size: 11, price: 1200000, onSale: false },
          { id: 108, sku: 'SP000005-S13', size: 13, price: 1200000, onSale: true }] }
      ],
      branches: [{ id: 1, name: 'Chi nhánh Hà Nội' }, { id: 2, name: 'Chi nhánh TP.HCM' }],
      packages: [
        { id: 1, name: 'Tự làm nhẫn bạc', fee: 1000000, sessions: [
          { id: 11, branchId: 1, date: day(5), time: '09:00' }, { id: 12, branchId: 2, date: day(6), time: '14:00' }] },
        { id: 2, name: 'Nặn đất sét', fee: 450000, sessions: [{ id: 21, branchId: 1, date: day(4), time: '15:00' }] },
        { id: 3, name: 'Làm sáp', fee: 650000, sessions: [{ id: 31, branchId: 2, date: day(8), time: '10:00' }] }
      ],
      members: { 'member.mai': { id: 9001, name: 'Mai' }, 'member.binh': { id: 9002, name: 'Bình' } },
      policies: [{ id: 1, revision: 1, earnUnitAmount: 10000, redeemPointValue: 100, minRedeemPoints: 10,
        maxRedeemPercent: 10, roundingMode: 'FLOOR', createdAt: iso(-60), createdBy: 'system' }],
      currentPolicyId: 1,
      policyVersion: 0,
      programs: [],
      memberPrograms: [],
      wallets: { 9001: { balance: 200, held: 0 }, 9002: { balance: 1500, held: 0 } },
      ledger: [
        { id: 1, member: 9001, kind: 'EARN', sourceKind: 'RETAIL', sourceRef: 'DH-2026-0912', balanceChange: 120, heldChange: 0, at: iso(-30), originalDebitId: null },
        { id: 2, member: 9001, kind: 'EARN', sourceKind: 'WORKSHOP', sourceRef: 'BK-2026-0418', balanceChange: 80, heldChange: 0, at: iso(-12), originalDebitId: null },
        { id: 3, member: 9002, kind: 'EARN', sourceKind: 'RETAIL', sourceRef: 'DH-2026-0777', balanceChange: 1500, heldChange: 0, at: iso(-20), originalDebitId: null }
      ],
      benefits: [],
      audit: [],
      receipts: {}
    };
    const program = (id, data) => {
      const p = { id, version: 0, revision: 1, status: 'ENABLED', heldCount: 0, consumedCount: 0, committedDiscount: 0,
        acceptedCount: 0, createdAt: iso(-40 + id), createdBy: 'manager.huy', ...data };
      p.revisions = [revisionOf(p, 1, p.createdAt)];
      state.programs.push(p);
      return p;
    };
    program(1, { kind: 'VOUCHER', scope: 'PRODUCT', code: 'SP10', name: 'Giảm 10% nhẫn cưới và nhẫn bạc', startAt: iso(-10), endAt: iso(40),
      discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: 50000 }, minimumEligibleAmount: 500000, allowPoints: true,
      targets: { productIds: [1, 2] }, totalLimit: 100, perMemberLimit: 2 });
    program(2, { kind: 'VOUCHER', scope: 'PRODUCT', code: 'LA50K', name: 'Giảm 50.000đ nhẫn bạc Lá size 10', startAt: iso(-5), endAt: iso(20),
      discount: { kind: 'FIXED', amount: 50000, maxDiscountAmount: 50000 }, minimumEligibleAmount: 0, allowPoints: false,
      targets: { variantIds: [104] }, totalLimit: 5, perMemberLimit: 1 });
    program(3, { kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', code: null, name: 'Tự động giảm 30.000đ nhẫn bạc Lá', startAt: iso(-15), endAt: iso(30),
      discount: { kind: 'FIXED', amount: 30000, maxDiscountAmount: 30000 }, minimumEligibleAmount: 0, allowPoints: true,
      targets: { productIds: [2] }, totalLimit: null, perMemberLimit: null });
    program(4, { kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', code: null, name: 'Tự động giảm 5% nhẫn cưới', startAt: iso(-15), endAt: iso(30),
      discount: { kind: 'PERCENT', percentage: '5', maxDiscountAmount: 100000 }, minimumEligibleAmount: 0, allowPoints: true,
      targets: { productIds: [1] }, totalLimit: null, perMemberLimit: null });
    program(5, { kind: 'VOUCHER', scope: 'WORKSHOP', code: 'WS100', name: 'Giảm 100.000đ workshop tự làm nhẫn', startAt: iso(-3), endAt: iso(25),
      discount: { kind: 'FIXED', amount: 100000, maxDiscountAmount: 100000 }, minimumEligibleAmount: 500000, allowPoints: true,
      targets: { packageIds: [1], branchIds: [1, 2] }, totalLimit: 50, perMemberLimit: 1 });
    program(6, { kind: 'VOUCHER', scope: 'PRODUCT', code: 'TET10', name: 'Tết giảm 10% (đã hết hạn)', startAt: iso(-90), endAt: iso(-60),
      discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: 200000 }, minimumEligibleAmount: 0, allowPoints: true,
      targets: { productIds: [1, 2, 5] }, totalLimit: 200, perMemberLimit: 1 });
    program(7, { kind: 'VOUCHER', scope: 'PRODUCT', code: 'RUBY20', name: 'Giảm 20% nhẫn Ruby (chưa công khai)', startAt: iso(-2), endAt: iso(30),
      discount: { kind: 'PERCENT', percentage: '20', maxDiscountAmount: 500000 }, minimumEligibleAmount: 0, allowPoints: true,
      targets: { productIds: [3] }, totalLimit: 10, perMemberLimit: 1 });
    program(8, { kind: 'VOUCHER', scope: 'WORKSHOP', code: 'SET15', name: 'Giảm 15% nặn đất sét (đang tạm dừng)', startAt: iso(-7), endAt: iso(20),
      discount: { kind: 'PERCENT', percentage: '15', maxDiscountAmount: 100000 }, minimumEligibleAmount: 0, allowPoints: true,
      targets: { packageIds: [2] }, totalLimit: 30, perMemberLimit: 1, status: 'PAUSED', version: 1 });
    // Mai saved three vouchers earlier (saving holds nothing).
    [1, 6, 7].forEach((programId) => state.memberPrograms.push({ programId, member: 9001, held: 0, consumed: 0, savedAt: iso(-1) }));
  }

  function revisionOf(p, revision, at) {
    return { revision, at, name: p.name, startAt: p.startAt, endAt: p.endAt, discount: { ...p.discount },
      minimumEligibleAmount: p.minimumEligibleAmount, allowPoints: p.allowPoints, totalLimit: p.totalLimit, perMemberLimit: p.perMemberLimit };
  }

  function load() {
    try {
      const saved = localStorage.getItem(STATE_KEY);
      if (saved) { state = JSON.parse(saved); return; }
    } catch (e) { /* fall through to seed */ }
    seed();
    save();
  }

  function save() {
    try {
      localStorage.setItem(STATE_KEY, JSON.stringify(state));
    } catch (e) { /* storage full or blocked: keep in memory */ }
  }

  function reset() {
    seed();
    save();
  }

  // ---------------------------------------------------------------- authority (FR-001, FR-040)

  const requireManager = (role) => {
    if (role === 'GUEST') fail('AUTHENTICATION_REQUIRED');
    if (role !== 'MANAGER') fail('ACCESS_DENIED');
  };
  const requireMember = (role, actor) => {
    if (role === 'GUEST') fail('AUTHENTICATION_REQUIRED');
    if (role !== 'MEMBER' || !state.members[actor]) fail('ACCESS_DENIED');
    return state.members[actor].id;
  };

  // ---------------------------------------------------------------- lookups

  const program = (id) => state.programs.find((p) => p.id === Number(id)) || fail('PROMOTION_NOT_FOUND');
  const policy = () => state.policies.find((p) => p.id === state.currentPolicyId);
  const policyById = (id) => state.policies.find((p) => p.id === id);
  const product = (id) => state.products.find((p) => p.id === Number(id));
  const variant = (id) => {
    for (const p of state.products) {
      const v = p.variants.find((x) => x.id === Number(id));
      if (v) return { ...v, product: p };
    }
    return null;
  };
  const pkg = (id) => state.packages.find((p) => p.id === Number(id));
  const session = (id) => {
    for (const p of state.packages) {
      const s = p.sessions.find((x) => x.id === Number(id));
      if (s) return { ...s, package: p };
    }
    return null;
  };
  const branch = (id) => state.branches.find((b) => b.id === Number(id));
  const wallet = (member) => state.wallets[member] || (state.wallets[member] = { balance: 0, held: 0 });
  const memberProgram = (programId, member) => {
    let mp = state.memberPrograms.find((x) => x.programId === programId && x.member === member);
    if (!mp) { mp = { programId, member, held: 0, consumed: 0, savedAt: null }; state.memberPrograms.push(mp); }
    return mp;
  };

  /** Window status of a programme now: ACTIVE, SCHEDULED, EXPIRED or PAUSED. */
  function availability(p, at) {
    const t = at || Date.now();
    if (p.status === 'PAUSED') return 'PAUSED';
    if (t < Date.parse(p.startAt)) return 'SCHEDULED';
    if (t >= Date.parse(p.endAt)) return 'EXPIRED';
    return 'ACTIVE';
  }

  /** Products of a product programme that are public now (FR-011): what the public card may list. */
  function publicTargets(p) {
    if (p.scope !== 'PRODUCT') return [];
    const ids = new Set(p.targets.productIds || []);
    (p.targets.variantIds || []).forEach((vid) => { const v = variant(vid); if (v) ids.add(v.product.id); });
    return Array.from(ids).map(product).filter((x) => x && x.public && !x.deleted);
  }

  /** Targets that are no longer usable (hidden, unpublished or deleted): a warning for the Manager. */
  function targetWarnings(p) {
    if (p.scope !== 'PRODUCT') return [];
    const out = [];
    (p.targets.productIds || []).forEach((id) => {
      const x = product(id);
      if (x.deleted) out.push({ name: x.name, state: 'DELETED' });
      else if (!x.public) out.push({ name: x.name, state: 'NOT_PUBLIC' });
    });
    (p.targets.variantIds || []).forEach((id) => {
      const v = variant(id);
      if (v.product.deleted) out.push({ name: v.sku, state: 'DELETED' });
      else if (!v.product.public) out.push({ name: v.sku, state: 'NOT_PUBLIC' });
      else if (!v.onSale) out.push({ name: v.sku, state: 'NOT_ON_SALE' });
    });
    return out;
  }

  const inScope = (p, v) => p.scope === 'PRODUCT'
    && ((p.targets.productIds || []).includes(v.product.id) || (p.targets.variantIds || []).includes(v.id));

  // ---------------------------------------------------------------- programme management (US1, US2)

  function readDiscount(raw) {
    const kind = raw.kind;
    if (kind === 'PERCENT') {
      const pct = trim(raw.percentage);
      if (!PERCENT.test(pct) || Number(pct) <= 0 || Number(pct) > 100) invalid('discount.percentage', 'percent');
      const max = trim(raw.maxDiscountAmount);
      if (!POSITIVE.test(max)) invalid('discount.maxDiscountAmount', 'positive');
      return { kind, percentage: String(Number(pct)), maxDiscountAmount: Number(max) };
    }
    if (kind === 'FIXED') {
      const amount = trim(raw.amount);
      if (!POSITIVE.test(amount)) invalid('discount.amount', 'positive');
      return { kind, amount: Number(amount), maxDiscountAmount: Number(amount) };
    }
    return invalid('discount.kind', 'kind');
  }

  function readWindow(startRaw, endRaw) {
    const startAt = Date.parse(startRaw);
    const endAt = Date.parse(endRaw);
    if (Number.isNaN(startAt)) invalid('startAt', 'required');
    if (Number.isNaN(endAt)) invalid('endAt', 'required');
    if (startAt >= endAt) invalid('endAt', 'after');
    return { startAt: new Date(startAt).toISOString(), endAt: new Date(endAt).toISOString() };
  }

  function readLimit(field, raw) {
    const value = trim(raw);
    if (!POSITIVE.test(value)) invalid(field, 'positive');
    return Number(value);
  }

  function readTargets(scope, raw) {
    if (scope === 'PRODUCT') {
      const productIds = (raw.productIds || []).map(Number);
      const variantIds = (raw.variantIds || []).map(Number);
      if (!productIds.length && !variantIds.length) invalid('targets', 'required');
      productIds.forEach((id) => {
        const x = product(id);
        if (!x) invalid('targets.productIds', 'missing');
        if (x.deleted) invalid('targets.productIds', 'deleted');
      });
      variantIds.forEach((id) => {
        const v = variant(id);
        if (!v) invalid('targets.variantIds', 'missing');
        if (v.product.deleted) invalid('targets.variantIds', 'deleted');
        // Product and size targets combine with AND (spec clarification 2026-10-26).
        if (productIds.length && !productIds.includes(v.product.id)) invalid('targets.variantIds', 'parent');
      });
      return { ...(productIds.length ? { productIds } : {}), ...(variantIds.length ? { variantIds } : {}) };
    }
    const packageIds = (raw.packageIds || []).map(Number);
    if (!packageIds.length) invalid('targets.packageIds', 'required');
    packageIds.forEach((id) => { if (!pkg(id)) invalid('targets.packageIds', 'missing'); });
    const branchIds = (raw.branchIds || []).map(Number);
    branchIds.forEach((id) => { if (!branch(id)) invalid('targets.branchIds', 'missing'); });
    const workshopDates = (raw.workshopDates || []).map(trim).filter(Boolean);
    workshopDates.forEach((d) => { if (!/^\d{4}-\d{2}-\d{2}$/.test(d)) invalid('targets.workshopDates', 'date'); });
    const out = { packageIds };
    if (branchIds.length) out.branchIds = branchIds;
    if (workshopDates.length) out.workshopDates = workshopDates;
    return out;
  }

  function createProgram(role, actor, input) {
    requireManager(role);
    const kind = input.kind;
    const scope = input.scope;
    if (!['VOUCHER', 'AUTO_DISCOUNT'].includes(kind)) invalid('kind', 'kind');
    if (!['PRODUCT', 'WORKSHOP'].includes(scope)) invalid('scope', 'scope');
    if (kind === 'AUTO_DISCOUNT' && scope !== 'PRODUCT') invalid('scope', 'auto-workshop');
    const name = trim(input.name);
    if (!name || name.length > 200) invalid('name', 'required');
    let code = null;
    if (kind === 'VOUCHER') {
      code = codeKey(input.code);
      if (!/^[A-Z0-9_-]{3,40}$/.test(code)) invalid('code', 'format');
      if (state.programs.some((p) => p.code && codeKey(p.code) === code)) fail('CODE_EXISTS', [{ field: 'code', message: 'exists' }]);
    }
    const win = readWindow(input.startAt, input.endAt);
    const discount = readDiscount(input.discount);
    const minimum = trim(input.minimumEligibleAmount || '0');
    if (!AMOUNT.test(minimum)) invalid('minimumEligibleAmount', 'amount');
    const targets = readTargets(scope, input.targets || {});
    const p = {
      id: nextId('program'), kind, scope, code, name, ...win, discount, minimumEligibleAmount: Number(minimum),
      // Automatic discounts always combine with points; Managers cannot turn that off (clarification 2026-10-26).
      allowPoints: kind === 'AUTO_DISCOUNT' ? true : !!input.allowPoints,
      targets,
      totalLimit: kind === 'VOUCHER' ? readLimit('totalLimit', input.totalLimit) : null,
      perMemberLimit: kind === 'VOUCHER' ? readLimit('perMemberLimit', input.perMemberLimit) : null,
      status: 'ENABLED', version: 0, revision: 1, heldCount: 0, consumedCount: 0, committedDiscount: 0, acceptedCount: 0,
      createdAt: nowIso(), createdBy: actor
    };
    p.revisions = [revisionOf(p, 1, p.createdAt)];
    state.programs.push(p);
    audit(actor, 'program.create', p.id, { code });
    return p;
  }

  function updateProgram(role, actor, id, expectedVersion, input) {
    requireManager(role);
    const p = program(id);
    if (p.version !== Number(expectedVersion)) fail('STALE_VERSION');
    const next = { ...p };
    if (input.name != null) { const n = trim(input.name); if (!n || n.length > 200) invalid('name', 'required'); next.name = n; }
    const win = readWindow(input.startAt || p.startAt, input.endAt || p.endAt);
    // The form shows minutes only: an unchanged minute keeps the stored instant, so it is not a change.
    const sameMinute = (a, b) => Math.floor(Date.parse(a) / 60000) === Math.floor(Date.parse(b) / 60000);
    next.startAt = sameMinute(win.startAt, p.startAt) ? p.startAt : win.startAt;
    next.endAt = sameMinute(win.endAt, p.endAt) ? p.endAt : win.endAt;
    if (Date.parse(next.startAt) >= Date.parse(next.endAt)) invalid('endAt', 'after');
    if (input.discount) next.discount = readDiscount(input.discount);
    if (input.minimumEligibleAmount != null) {
      const m = trim(input.minimumEligibleAmount);
      if (!AMOUNT.test(m)) invalid('minimumEligibleAmount', 'amount');
      next.minimumEligibleAmount = Number(m);
    }
    if (p.kind === 'VOUCHER') {
      if (input.allowPoints != null) next.allowPoints = !!input.allowPoints;
      if (input.totalLimit != null) next.totalLimit = readLimit('totalLimit', input.totalLimit);
      if (input.perMemberLimit != null) next.perMemberLimit = readLimit('perMemberLimit', input.perMemberLimit);
      // Limits never drop below what is used plus held now (US2.3, US2.6); holds are never removed to force a save.
      if (next.totalLimit < p.heldCount + p.consumedCount) fail('QUOTA_LIMIT_CONFLICT', [{ field: 'totalLimit', message: 'min ' + (p.heldCount + p.consumedCount) }]);
      const busiest = state.memberPrograms.filter((x) => x.programId === p.id).reduce((m, x) => Math.max(m, x.held + x.consumed), 0);
      if (next.perMemberLimit < busiest) fail('QUOTA_LIMIT_CONFLICT', [{ field: 'perMemberLimit', message: 'min ' + busiest }]);
    }
    const keys = ['name', 'startAt', 'endAt', 'minimumEligibleAmount', 'allowPoints', 'totalLimit', 'perMemberLimit'];
    const changed = keys.some((k) => next[k] !== p[k]) || JSON.stringify(next.discount) !== JSON.stringify(p.discount);
    if (!changed) return { program: p, changed: false };
    Object.assign(p, next, { version: p.version + 1, revision: p.revision + 1 });
    p.revisions.push(revisionOf(p, p.revision, nowIso()));
    audit(actor, 'program.update', p.id, { revision: p.revision });
    return { program: p, changed: true };
  }

  function setStatus(role, actor, id, expectedVersion, paused) {
    requireManager(role);
    const p = program(id);
    if (p.version !== Number(expectedVersion)) fail('STALE_VERSION');
    if (paused && p.status === 'PAUSED') fail('BENEFIT_STATE_CONFLICT');
    if (!paused && p.status === 'ENABLED') fail('BENEFIT_STATE_CONFLICT');
    // Same programme, code, counters and window; resume neither extends nor resets (US2.8).
    p.status = paused ? 'PAUSED' : 'ENABLED';
    p.version += 1;
    audit(actor, paused ? 'program.pause' : 'program.resume', p.id, {});
    return p;
  }

  function managementList(role, filter) {
    requireManager(role);
    const items = state.programs
      .filter((p) => !filter.scope || p.scope === filter.scope)
      .filter((p) => !filter.status || p.status === filter.status)
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id);
    return paginate(items, filter.page);
  }

  function usage(role, id) {
    requireManager(role);
    const p = program(id);
    const history = state.benefits.filter((b) => b.programId === p.id).sort((a, b) => b.id - a.id);
    return { program: p, history };
  }

  // ---------------------------------------------------------------- public cards and saved vouchers (US3)

  function publicList(scope) {
    return state.programs
      .filter((p) => availability(p) === 'ACTIVE')
      .filter((p) => !scope || p.scope === scope)
      .filter((p) => p.scope !== 'PRODUCT' || publicTargets(p).length > 0)
      .sort((a, b) => Date.parse(a.endAt) - Date.parse(b.endAt) || a.id - b.id);
  }

  function publicDetail(id) {
    const p = program(id);
    if (availability(p) !== 'ACTIVE' || (p.scope === 'PRODUCT' && !publicTargets(p).length)) fail('PROMOTION_NOT_FOUND');
    return p;
  }

  function saveVoucher(role, actor, id) {
    const member = requireMember(role, actor);
    const p = publicDetail(id);
    if (p.kind !== 'VOUCHER') fail('PROMOTION_INELIGIBLE');
    const mp = memberProgram(p.id, member);
    const already = !!mp.savedAt;
    if (!already) mp.savedAt = nowIso();
    return { program: p, already };
  }

  function savedVouchers(role, actor) {
    const member = requireMember(role, actor);
    return state.memberPrograms.filter((x) => x.member === member && x.savedAt)
      .sort((a, b) => Date.parse(b.savedAt) - Date.parse(a.savedAt))
      .map((x) => {
        const p = program(x.programId);
        const status = availability(p);
        return { program: p, savedAt: x.savedAt,
          availability: status === 'ACTIVE' && p.scope === 'PRODUCT' && !publicTargets(p).length ? 'NO_PUBLIC_TARGET' : status };
      });
  }

  // ---------------------------------------------------------------- calculation (US4, US5, FR-014–FR-025)

  function discountOf(p, base) {
    if (base <= 0) return 0;
    const raw = p.discount.kind === 'PERCENT' ? floorDiv(base * Number(p.discount.percentage), 100) : p.discount.amount;
    return Math.min(raw, p.discount.maxDiscountAmount, base);
  }

  /** Why a voucher cannot be used for this member now, or null. Quota counts holds of other transactions. */
  function voucherProblem(p, member, scope) {
    if (p.kind !== 'VOUCHER') return 'PROMOTION_INELIGIBLE';
    if (p.scope !== scope) return 'WRONG_SCOPE';
    const a = availability(p);
    if (a !== 'ACTIVE') return a;
    if (p.heldCount + p.consumedCount >= p.totalLimit) return 'SOLD_OUT';
    const mp = state.memberPrograms.find((x) => x.programId === p.id && x.member === member);
    if (mp && mp.held + mp.consumed >= p.perMemberLimit) return 'MEMBER_LIMIT';
    return null;
  }

  function findByCode(code) {
    const key = codeKey(code);
    return key ? state.programs.find((p) => p.code && codeKey(p.code) === key) || null : null;
  }

  function pointsPart(member, afterProgram, allowPoints, requested, pol) {
    const w = wallet(member);
    const available = w.balance - w.held;
    const cap = floorDiv(floorDiv(afterProgram * pol.maxRedeemPercent, 100), pol.redeemPointValue);
    const max = Math.max(0, Math.min(available, cap));
    let reason = null;
    if (!allowPoints) reason = 'NOT_ALLOWED';
    else if (max < pol.minRedeemPoints) reason = 'BELOW_MINIMUM';
    const usable = !reason;
    const want = Number(requested || 0);
    if (want && !usable) fail('POINTS_USE_NOT_ALLOWED', [{ field: 'points', message: reason }]);
    if (want && (!Number.isInteger(want) || want < pol.minRedeemPoints || want > max)) invalid('points', 'range ' + pol.minRedeemPoints + '-' + max);
    return { available, max: usable ? max : 0, min: pol.minRedeemPoints, reason, points: want, amount: want * pol.redeemPointValue };
  }

  /**
   * Retail quote (FE-08 checkout stand-in). lines: [{variantId, quantity}], shipping in VND, selection:
   * { mode: 'auto' | 'voucher' | 'none', code }, points. Returns the full breakdown shown before acceptance.
   */
  function retailQuote(role, actor, lines, shipping, selection, points) {
    const member = requireMember(role, actor);
    const pol = policy();
    const rows = lines.map((l) => {
      const v = variant(l.variantId);
      // Only lines that can still be bought count (FR-014): hidden, stopped or deleted ones never count.
      const purchasable = v && v.product.public && !v.product.deleted && v.onSale;
      return { ...l, v, purchasable, total: v.price * l.quantity };
    });
    const merchandise = rows.reduce((s, r) => s + r.total, 0);
    const baseFor = (p) => rows.filter((r) => r.purchasable && inScope(p, r.v)).reduce((s, r) => s + r.total, 0);
    let chosen = null;
    let programDiscount = 0;
    let voucherIssue = null;
    const autos = state.programs.filter((p) => p.kind === 'AUTO_DISCOUNT' && p.scope === 'PRODUCT' && availability(p) === 'ACTIVE')
      .map((p) => ({ p, base: baseFor(p) }))
      .filter((x) => x.base > 0 && x.base >= x.p.minimumEligibleAmount)
      .map((x) => ({ ...x, discount: discountOf(x.p, x.base) }))
      // Largest discount wins; on a tie the programme created first (FR-015).
      .sort((a, b) => b.discount - a.discount || Date.parse(a.p.createdAt) - Date.parse(b.p.createdAt) || a.p.id - b.p.id);
    if (selection.mode === 'voucher') {
      const p = findByCode(selection.code);
      if (!p) voucherIssue = 'NOT_FOUND';
      else {
        const problem = voucherProblem(p, member, 'PRODUCT');
        const base = baseFor(p);
        if (problem) voucherIssue = problem;
        else if (base <= 0) voucherIssue = 'NO_ELIGIBLE_LINE';
        else if (base < p.minimumEligibleAmount) voucherIssue = 'BELOW_THRESHOLD';
        else { chosen = { p, base }; programDiscount = discountOf(p, base); }
      }
      if (voucherIssue) fail('PROMOTION_INELIGIBLE', [{ field: 'code', message: voucherIssue }], { code: selection.code, issue: voucherIssue });
    } else if (selection.mode === 'auto' && autos.length) {
      chosen = autos[0];
      programDiscount = autos[0].discount;
    }
    const afterProgram = merchandise - programDiscount;
    const pts = pointsPart(member, afterProgram, chosen ? chosen.p.allowPoints : true, points, pol);
    const merchandiseDue = afterProgram - pts.amount;
    return {
      sourceKind: 'RETAIL', rows, merchandise, shipping, program: chosen ? chosen.p : null, eligibleBase: chosen ? chosen.base : 0,
      programDiscount, autos: autos.map((a) => ({ id: a.p.id, name: a.p.name, discount: a.discount })),
      afterProgram, points: pts, payable: merchandiseDue + shipping,
      earnPreview: floorDiv(merchandiseDue, pol.earnUnitAmount), policy: pol
    };
  }

  /** Workshop quote (FE-03 booking stand-in): voucher first, then points, on the package fee (FR-019–FR-022). */
  function workshopQuote(role, actor, sessionId, participants, code, points) {
    const member = requireMember(role, actor);
    const pol = policy();
    const s = session(sessionId);
    if (!s) invalid('sessionId', 'required');
    const count = Number(participants || 1);
    if (!Number.isInteger(count) || count < 1 || count > 10) invalid('participants', 'range');
    const fee = s.package.fee * count;
    let chosen = null;
    let programDiscount = 0;
    if (trim(code)) {
      const p = findByCode(code);
      let issue = null;
      if (!p) issue = 'NOT_FOUND';
      else {
        issue = voucherProblem(p, member, 'WORKSHOP');
        const t = p.targets;
        if (!issue && !(t.packageIds || []).includes(s.package.id)) issue = 'WRONG_PACKAGE';
        if (!issue && t.branchIds && !t.branchIds.includes(s.branchId)) issue = 'WRONG_BRANCH';
        if (!issue && t.workshopDates && !t.workshopDates.includes(s.date)) issue = 'WRONG_DATE';
        if (!issue && fee < p.minimumEligibleAmount) issue = 'BELOW_THRESHOLD';
      }
      if (issue) fail('PROMOTION_INELIGIBLE', [{ field: 'code', message: issue }], { code, issue });
      chosen = p;
      // One voucher per booking; the cap applies to the whole booking, not per participant.
      programDiscount = discountOf(p, fee);
    }
    const afterProgram = fee - programDiscount;
    const pts = pointsPart(member, afterProgram, chosen ? chosen.allowPoints : true, points, pol);
    const post = afterProgram - pts.amount;
    if (post <= 0) fail('WORKSHOP_NET_AMOUNT_INVALID');
    return {
      sourceKind: 'WORKSHOP', session: s, participants: count, fee, program: chosen, programDiscount, afterProgram, points: pts,
      postPromotionAmount: post,
      // Shown for the Member only: 003.1 computes and stores the deposit and the remainder, not FE-06.
      deposit: Math.ceil(post / 2), remaining: post - Math.ceil(post / 2),
      earnPreview: floorDiv(post, pol.earnUnitAmount), policy: pol
    };
  }

  const digestOf = (q) => JSON.stringify([q.program ? q.program.id : null, q.programDiscount, q.points.points, q.points.amount,
    q.sourceKind === 'RETAIL' ? q.payable : q.postPromotionAmount, q.policy.id]);

  // ---------------------------------------------------------------- accept, settle, earn, release, restore (US4–US7)

  function ledgerEntry(member, kind, b, balanceChange, heldChange, originalDebitId) {
    const id = nextId('ledger');
    state.ledger.push({ id, member, kind, sourceKind: b.sourceKind, sourceRef: b.sourceRef, balanceChange, heldChange, at: nowIso(), originalDebitId: originalDebitId || null });
    return id;
  }

  /**
   * Source accept: re-checks the quote under the "locks" and holds quota and points together with the source record.
   * shownDigest is what the Member agreed to; a different result is QUOTE_CHANGED and nothing is held (FR-017, FR-021).
   */
  function accept(role, actor, shownDigest, recompute) {
    const member = requireMember(role, actor);
    let fresh;
    try {
      fresh = recompute();
    } catch (e) {
      // The points chosen no longer fit the new limit: the Member must choose again (POINTS_LIMIT_CHANGED).
      if (e instanceof DomainError && e.fieldErrors.some((f) => f.field === 'points')) fail('POINTS_LIMIT_CHANGED', e.fieldErrors);
      throw e;
    }
    if (digestOf(fresh) !== shownDigest) fail('QUOTE_CHANGED', [], { quote: fresh });
    const p = fresh.program;
    const sourceRef = (fresh.sourceKind === 'RETAIL' ? 'DH-' : 'BK-') + new Date().getFullYear() + '-' + String(9000 + nextId('source')).padStart(4, '0');
    const b = {
      id: nextId('benefit'), member, sourceKind: fresh.sourceKind, sourceRef, programId: p ? p.id : null, programKind: p ? p.kind : null,
      programName: p ? p.name : null, programRevision: p ? p.revision : null, discount: fresh.programDiscount,
      points: fresh.points.points, pointsAmount: fresh.points.amount, policyId: fresh.policy.id,
      payable: fresh.sourceKind === 'RETAIL' ? fresh.payable : fresh.postPromotionAmount,
      earnBase: fresh.sourceKind === 'RETAIL' ? fresh.payable - fresh.shipping : fresh.postPromotionAmount,
      summary: fresh.sourceKind === 'RETAIL'
        ? fresh.rows.map((r) => r.v.sku + ' × ' + r.quantity).join(', ')
        : fresh.session.package.name + ' · ' + fresh.session.date + ' ' + fresh.session.time + ' · ' + fresh.participants + ' người',
      state: 'HELD', settled: false, earned: false, completed: false, earnedPoints: 0, acceptedAt: nowIso(),
      expiresAt: new Date(Date.now() + (fresh.sourceKind === 'RETAIL' ? 15 * 60000 : 30 * 60000)).toISOString(), events: []
    };
    if (p && p.kind === 'VOUCHER') {
      p.heldCount += 1;
      memberProgram(p.id, member).held += 1;
    }
    if (b.points) {
      wallet(member).held += b.points;
      b.holdLedgerId = ledgerEntry(member, 'HOLD', b, 0, b.points);
    }
    b.events.push({ at: nowIso(), kind: 'ACCEPTED' });
    state.benefits.push(b);
    return b;
  }

  const benefit = (id) => state.benefits.find((b) => b.id === Number(id)) || fail('PROMOTION_NOT_FOUND');

  /** Verified paid (retail), verified deposit (workshop) or valid 0đ order: consume held components once. */
  function settle(b) {
    if (b.state === 'SETTLED') return false;
    if (b.state !== 'HELD') fail('BENEFIT_STATE_CONFLICT');
    const p = b.programId ? program(b.programId) : null;
    if (p && p.kind === 'VOUCHER') {
      p.heldCount -= 1;
      p.consumedCount += 1;
      const mp = memberProgram(p.id, b.member);
      mp.held -= 1;
      mp.consumed += 1;
    }
    if (p) { p.committedDiscount += b.discount; if (p.kind === 'AUTO_DISCOUNT') p.acceptedCount += 1; }
    if (b.points) {
      const w = wallet(b.member);
      w.held -= b.points;
      w.balance -= b.points;
      b.consumeLedgerId = ledgerEntry(b.member, 'CONSUME', b, -b.points, -b.points);
    }
    b.state = 'SETTLED';
    b.settled = true;
    b.events.push({ at: nowIso(), kind: 'SETTLED' });
    return true;
  }

  /** Earn once, under the policy recorded at acceptance (FR-027, FR-030). */
  function earn(b) {
    if (b.earned) return false;
    const pol = policyById(b.policyId);
    const points = floorDiv(b.earnBase, pol.earnUnitAmount);
    b.earned = true;
    b.earnedPoints = points;
    if (points > 0) {
      wallet(b.member).balance += points;
      ledgerEntry(b.member, 'EARN', b, points, 0);
    }
    b.events.push({ at: nowIso(), kind: 'EARNED', points });
    return true;
  }

  /** Confirmed unpaid expiry, or valid cancellation while only held: return what is held, once (FR-033). */
  function release(b) {
    if (b.state === 'RELEASED') return false;
    if (b.state !== 'HELD') fail('BENEFIT_STATE_CONFLICT');
    const p = b.programId ? program(b.programId) : null;
    if (p && p.kind === 'VOUCHER') {
      p.heldCount -= 1;
      memberProgram(p.id, b.member).held -= 1;
    }
    if (b.points) {
      wallet(b.member).held -= b.points;
      ledgerEntry(b.member, 'RELEASE', b, 0, -b.points, b.holdLedgerId);
    }
    b.state = 'RELEASED';
    b.events.push({ at: nowIso(), kind: 'RELEASED' });
    return true;
  }

  /** Valid workshop cancellation after settlement: give back the same points and the voucher use (FR-034, FR-035). */
  function restore(b) {
    if (b.state === 'RESTORED') return false;
    if (b.state !== 'SETTLED' || b.completed) fail('BENEFIT_STATE_CONFLICT');
    const p = b.programId ? program(b.programId) : null;
    if (p && p.kind === 'VOUCHER') {
      p.consumedCount -= 1;
      memberProgram(p.id, b.member).consumed -= 1;
    }
    if (p) p.committedDiscount -= b.discount;
    if (b.points) {
      wallet(b.member).balance += b.points;
      ledgerEntry(b.member, 'RESTORE', b, b.points, 0, b.consumeLedgerId);
    }
    b.state = 'RESTORED';
    b.events.push({ at: nowIso(), kind: 'RESTORED' });
    return true;
  }

  /**
   * Source-owner outcome stand-ins. Repeating one is a no-op; a conflicting one is refused, so paid and expired can
   * never both apply (FR-038).
   */
  function outcome(role, actor, id, kind) {
    const member = requireMember(role, actor);
    const b = benefit(id);
    if (b.member !== member) fail('PROMOTION_NOT_FOUND');
    let changed = false;
    if (b.sourceKind === 'RETAIL') {
      if (kind === 'PAID') { changed = settle(b); if (changed || !b.earned) changed = earn(b) || changed; }
      else if (kind === 'EXPIRED') changed = release(b);
      else fail('BENEFIT_STATE_CONFLICT');
    } else if (kind === 'DEPOSIT') changed = settle(b);
    else if (kind === 'COMPLETED_PAID') {
      if (b.state !== 'SETTLED') fail('BENEFIT_STATE_CONFLICT');
      if (!b.completed) { b.completed = true; changed = true; }
      changed = earn(b) || changed;
    } else if (kind === 'CANCELLED') {
      if (b.completed) fail('BENEFIT_STATE_CONFLICT');
      changed = b.state === 'HELD' || b.state === 'RELEASED' ? release(b) : restore(b);
    } else if (kind === 'EXPIRED') changed = release(b);
    else fail('BENEFIT_STATE_CONFLICT');
    return { benefit: b, changed };
  }

  function myBenefits(role, actor) {
    const member = requireMember(role, actor);
    return state.benefits.filter((b) => b.member === member).sort((a, b) => b.id - a.id);
  }

  // ---------------------------------------------------------------- My Loyalty (US6)

  function account(role, actor, page) {
    const member = requireMember(role, actor);
    const w = wallet(member);
    const history = state.ledger.filter((e) => e.member === member).sort((a, b) => Date.parse(b.at) - Date.parse(a.at) || b.id - a.id);
    return { balance: w.balance, held: w.held, available: w.balance - w.held, policy: policy(), history: paginate(history, page) };
  }

  // ---------------------------------------------------------------- points policy (US8)

  function updatePolicy(role, actor, expectedVersion, input) {
    requireManager(role);
    if (Number(expectedVersion) !== state.policyVersion) fail('STALE_VERSION');
    const errors = [];
    const read = (field, re) => { const v = trim(input[field]); if (!re.test(v) || Number(v) <= 0) errors.push({ field, message: 'positive' }); return Number(v); };
    const earnUnitAmount = read('earnUnitAmount', POSITIVE);
    const redeemPointValue = read('redeemPointValue', POSITIVE);
    const minRedeemPoints = read('minRedeemPoints', POSITIVE);
    const pct = trim(input.maxRedeemPercent);
    if (!PERCENT.test(pct) || Number(pct) <= 0 || Number(pct) > 100) errors.push({ field: 'maxRedeemPercent', message: 'percent' });
    if (errors.length) fail('INVALID_INPUT', errors);
    const cur = policy();
    if (cur.earnUnitAmount === earnUnitAmount && cur.redeemPointValue === redeemPointValue
      && cur.minRedeemPoints === minRedeemPoints && cur.maxRedeemPercent === Number(pct)) return { policy: cur, changed: false };
    // A new revision for transactions accepted from now on; accepted ones keep the policy they recorded (FR-027).
    const next = { id: nextId('policy'), revision: cur.revision + 1, earnUnitAmount, redeemPointValue, minRedeemPoints,
      maxRedeemPercent: Number(pct), roundingMode: 'FLOOR', createdAt: nowIso(), createdBy: actor };
    state.policies.push(next);
    state.currentPolicyId = next.id;
    state.policyVersion += 1;
    audit(actor, 'policy.update', next.id, {});
    return { policy: next, changed: true };
  }

  // ---------------------------------------------------------------- fault stand-ins (prototype only)

  /** Another Manager edits the programme just before this save: the next save gets 409. */
  function simulateExternalProgramEdit(id) {
    const p = program(id);
    p.version += 1;
    p.revision += 1;
    p.name = p.name.replace(/ \(sửa bởi manager\.linh\)$/, '') + ' (sửa bởi manager.linh)';
    p.revisions.push(revisionOf(p, p.revision, nowIso()));
    audit('manager.linh', 'program.update', p.id, {});
  }

  function simulateExternalPolicyEdit() {
    const cur = policy();
    const next = { ...cur, id: nextId('policy'), revision: cur.revision + 1, createdAt: nowIso(), createdBy: 'manager.linh' };
    state.policies.push(next);
    state.currentPolicyId = next.id;
    state.policyVersion += 1;
  }

  /** The Member's points are used on another device between quote and confirmation: the next accept sees a new quote. */
  function simulateOtherDeviceHold(actor) {
    const member = state.members[actor].id;
    const w = wallet(member);
    const take = Math.max(0, w.balance - w.held - 5);
    if (!take) return;
    const b = { sourceKind: 'RETAIL', sourceRef: 'DH-KHAC-' + String(nextId('source')).padStart(4, '0') };
    w.held += take;
    state.benefits.push({ ...b, id: nextId('benefit'), member, programId: null, programKind: null, programName: null, discount: 0,
      points: take, pointsAmount: take * policy().redeemPointValue, policyId: policy().id, payable: 0, earnBase: 0,
      summary: 'Đơn khác trên điện thoại', state: 'HELD', settled: false, earned: false, completed: false, earnedPoints: 0,
      acceptedAt: nowIso(), expiresAt: new Date(Date.now() + 15 * 60000).toISOString(), events: [{ at: nowIso(), kind: 'ACCEPTED' }],
      holdLedgerId: ledgerEntry(member, 'HOLD', b, 0, take) });
  }

  // ---------------------------------------------------------------- receipts, audit, paging

  function receipt(key) { return state.receipts[key] || null; }
  function recordReceipt(key, result) { state.receipts[key] = { at: nowIso(), result }; }
  function audit(actor, action, resourceId, safe) { state.audit.push({ at: nowIso(), actor, action, resourceId, ...safe }); }

  function paginate(items, page) {
    const pages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
    const p = Math.min(Math.max(0, Number(page || 0)), pages - 1);
    return { items: items.slice(p * PAGE_SIZE, (p + 1) * PAGE_SIZE), total: items.length, page: p, pages };
  }

  global.LP = {
    DomainError, PAGE_SIZE, STATE_KEY,
    load, save, reset, get state() { return state; },
    program, policy, policyById, product, variant, pkg, session, branch, availability, publicTargets, targetWarnings, wallet,
    createProgram, updateProgram, setStatus, managementList, usage,
    publicList, publicDetail, saveVoucher, savedVouchers,
    retailQuote, workshopQuote, digestOf, accept, outcome, myBenefits, account, updatePolicy,
    simulateExternalProgramEdit, simulateExternalPolicyEdit, simulateOtherDeviceHold, receipt, recordReceipt
  };
})(window);
