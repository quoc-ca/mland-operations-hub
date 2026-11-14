import { describe, expect, it } from 'vitest';
import {
  createDefaultDraft,
  validateDraftClient,
  normalizeDraftForSave,
} from '../domain/designDraft';
import {
  derivePreviewModel,
  MATERIAL_PALETTES,
  SHAPE_STROKES,
} from '../domain/designPreview';
import { getErrorMessage } from '../domain/designErrors';

describe('Design Draft Domain Logic', () => {
  it('creates default blank draft when no source model is provided', () => {
    const draft = createDefaultDraft('PKG-WORKSHOP-01');

    expect(draft.packageReference).toBe('PKG-WORKSHOP-01');
    expect(draft.sourceModelReference).toBeNull();
    expect(draft.materialReference).toBe('MAT-SILVER-925');
    expect(draft.shapeReference).toBe('SHP-CLASSIC-ROUND');
    expect(draft.ringSize).toBe('8');
    expect(draft.components).toEqual([]);
    expect(draft.attachments).toEqual([]);
    expect(draft.engraving.enabled).toBe(false);
  });

  it('creates draft populated from source model template', () => {
    const model = {
      modelReference: 'MOD-VINTAGE-TWIST',
      materialReference: 'MAT-GOLD-14K',
      shapeReference: 'SHP-TWISTED',
      defaultComponents: {
        components: [{ reference: 'GEM-CZ-WHITE-2MM', quantity: 1, placement: 'CENTER' }],
        attachments: [{ reference: 'ATT-MILGRAIN', quantity: 1, placement: 'OUTER_SURFACE' }],
      },
    };

    const draft = createDefaultDraft('PKG-WORKSHOP-02', model);

    expect(draft.packageReference).toBe('PKG-WORKSHOP-02');
    expect(draft.sourceModelReference).toBe('MOD-VINTAGE-TWIST');
    expect(draft.materialReference).toBe('MAT-GOLD-14K');
    expect(draft.shapeReference).toBe('SHP-TWISTED');
    expect(draft.components).toHaveLength(1);
    expect(draft.attachments).toHaveLength(1);
  });

  it('validates required fields on client side', () => {
    const invalidDraft = {
      packageReference: '',
      materialReference: '',
      shapeReference: '',
      ringSize: '',
    };

    const res = validateDraftClient(invalidDraft);
    expect(res.isValid).toBe(false);
    expect(res.errors.packageReference).toBeDefined();
    expect(res.errors.materialReference).toBeDefined();
    expect(res.errors.shapeReference).toBeDefined();
    expect(res.errors.ringSize).toBeDefined();
  });

  it('validates engraving constraints when enabled', () => {
    const draftWithEmptyEngraving = {
      packageReference: 'PKG-1',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: '8',
      engraving: { enabled: true, text: '   ' },
    };

    const res1 = validateDraftClient(draftWithEmptyEngraving);
    expect(res1.isValid).toBe(false);
    expect(res1.errors.engravingText).toContain('Vui lòng nhập nội dung khắc');

    const draftWithLongEngraving = {
      ...draftWithEmptyEngraving,
      engraving: { enabled: true, text: 'A'.repeat(51) },
    };

    const res2 = validateDraftClient(draftWithLongEngraving);
    expect(res2.isValid).toBe(false);
    expect(res2.errors.engravingText).toContain('tối đa 50 ký tự');

    const draftWithValidEngraving = {
      ...draftWithEmptyEngraving,
      engraving: { enabled: true, text: 'Forever Love' },
    };

    const res3 = validateDraftClient(draftWithValidEngraving);
    expect(res3.isValid).toBe(true);
  });

  it('normalizes draft payload for backend persistence', () => {
    const draft = {
      packageReference: 'PKG-TEST',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: 10,
      components: [{ reference: 'GEM-CZ-WHITE-2MM', quantity: 2, placement: 'CENTER' }],
      attachments: [{ reference: 'ATT-SATIN-FINISH', quantity: '1', placement: 'OUTER_SURFACE' }],
      engraving: {
        enabled: true,
        text: '  Anh & Em  ',
        placement: 'INNER_BAND',
      },
    };

    const normalized = normalizeDraftForSave(draft);

    expect(normalized.packageReference).toBe('PKG-TEST');
    expect(normalized.ringSize).toBe('10');
    expect(normalized.components[0].quantity).toBe(2);
    expect(normalized.attachments[0].quantity).toBe(1);
    expect(normalized.engraving.enabled).toBe(true);
    expect(normalized.engraving.text).toBe('Anh & Em');

    // When engraving is disabled, text and placement are cleared
    draft.engraving.enabled = false;
    const normalizedDisabled = normalizeDraftForSave(draft);
    expect(normalizedDisabled.engraving.enabled).toBe(false);
    expect(normalizedDisabled.engraving.text).toBeNull();
    expect(normalizedDisabled.engraving.placement).toBeNull();
  });

  it('handles multi-item engraving and custom images in draft normalization and pricing', () => {
    const draft = {
      packageReference: 'PKG-TEST',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-BALL-PEEN',
      ringSize: '8',
      thicknessMm: 1.8,
      bandWidthMm: 3.5,
      components: [],
      attachments: [],
      engraving: {
        enabled: true,
        items: [
          { id: '1', content: 'Forever', placement: 'INNER_BAND' },
          { id: '2', content: '2026', placement: 'OUTER_BAND' },
        ],
        customImages: [
          { id: 'img-1', imageUrl: 'data:image/png;base64,...', cropX: 10, cropY: 20, placement: 'OUTER_BAND' },
        ],
      },
    };

    const normalized = normalizeDraftForSave(draft);
    expect(normalized.engraving.enabled).toBe(true);
    expect(normalized.engraving.items).toHaveLength(2);
    expect(normalized.engraving.customImages).toHaveLength(1);
    expect(normalized.engraving.text).toBe('Forever | 2026');

    const preview = derivePreviewModel(draft);
    expect(preview.standardEngravingCost).toBe(80000);
    expect(preview.customImagesCost).toBe(100000);
    expect(preview.totalEngravingCost).toBe(180000);
    expect(preview.isTexturedSurface).toBe(true);
    expect(preview.hasOuterEngraving).toBe(true);
    expect(preview.showSurfaceTextureWarning).toBe(true);
  });
});

describe('Design Preview Model Derivation', () => {
  it('returns fallback model when draft is null or undefined', () => {
    const preview = derivePreviewModel(null);
    expect(preview.isNeutral).toBe(true);
    expect(preview.material).toEqual(MATERIAL_PALETTES['MAT-SILVER-925']);
    expect(preview.shape).toEqual(SHAPE_STROKES['SHP-CLASSIC-ROUND']);
  });

  it('derives accurate visual tokens and gemstone glows for selected options', () => {
    const draft = {
      materialReference: 'MAT-GOLD-14K',
      shapeReference: 'SHP-TWISTED',
      ringSize: '9',
      components: [
        { reference: 'GEM-CZ-BLUE-2MM', quantity: 2, placement: 'CENTER' },
      ],
      attachments: [
        { reference: 'ATT-MILGRAIN', quantity: 1, placement: 'OUTER_SURFACE' },
      ],
      engraving: {
        enabled: true,
        text: 'LOVE',
      },
    };

    const preview = derivePreviewModel(draft);

    expect(preview.isNeutral).toBe(false);
    expect(preview.material.primary).toBe(MATERIAL_PALETTES['MAT-GOLD-14K'].primary);
    expect(preview.shape.style).toBe('twisted');
    expect(preview.gemstones).toHaveLength(1);
    expect(preview.gemstones[0].fill).toBe('#38BDF8');
    expect(preview.attachments).toHaveLength(1);
    expect(preview.engravingText).toBe('LOVE');
  });
});

describe('Design Error Formatting', () => {
  it('maps known error codes to friendly localized messages', () => {
    expect(getErrorMessage({ code: 'DESIGN_INPUT_INVALID' })).toContain('Thông tin cấu hình nhẫn không hợp lệ');
    expect(getErrorMessage({ code: 'DESIGN_BRANCH_INCOMPATIBLE' })).toContain('Chi nhánh workshop được chọn chưa hỗ trợ');
    expect(getErrorMessage({ code: 'DESIGN_SESSION_EXPIRED' })).toContain('Phiên làm việc của bạn đã hết hạn');
    expect(getErrorMessage({ code: 'DESIGN_VERSION_CONFLICT' })).toContain('Mẫu thiết kế đã được cập nhật từ một phiên làm việc khác');
    expect(getErrorMessage({ code: 'DESIGN_LOCKED' })).toContain('Mẫu thiết kế đã được gắn vào lịch đặt workshop');
    expect(getErrorMessage({ code: 'IDEMPOTENCY_CONFLICT' })).toContain('Yêu cầu trùng lặp');
  });

  it('falls back to error.message or default text when code is unknown', () => {
    expect(getErrorMessage({ message: 'Custom server error' })).toBe('Custom server error');
    expect(getErrorMessage(null, 'Mặc định')).toBe('Mặc định');
  });
});

