import { describe, expect, it } from 'vitest';
import {
  createDefaultDraft,
  validateDraftClient,
  normalizeDraftForSave,
} from '../domain/designDraft';
import {
  derivePreviewModel,
  findNextAvailableAngle,
  getDifficultyMeta,
  getBaseMaterialPrice,
  estimateExcessMaterialCost,
  estimateTotalMaterialCost,
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

  it('validates aggregate gemstone limit (maximum 10)', () => {
    const draftWithinLimit = {
      packageReference: 'PKG-1',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: '8',
      components: [
        { reference: 'GEM-CZ-WHITE-2MM', quantity: 6 },
        { reference: 'GEM-CZ-BLUE-2MM', quantity: 4 },
      ],
    };
    expect(validateDraftClient(draftWithinLimit).isValid).toBe(true);

    const draftExceedingLimit = {
      ...draftWithinLimit,
      components: [
        { reference: 'GEM-CZ-WHITE-2MM', quantity: 6 },
        { reference: 'GEM-CZ-BLUE-2MM', quantity: 5 },
      ],
    };
    const res = validateDraftClient(draftExceedingLimit);
    expect(res.isValid).toBe(false);
    expect(res.errors.components).toContain('không được vượt quá 10 viên');
  });

  it('validates independent engraving quotas (TEXT <= 2, SYMBOL <= 2, CUSTOM_IMAGE <= 2)', () => {
    const baseDraft = {
      packageReference: 'PKG-1',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: '8',
    };

    // 3 TEXT items should fail
    const draft3Text = {
      ...baseDraft,
      engraving: {
        enabled: true,
        items: [
          { type: 'TEXT', content: 'A' },
          { type: 'TEXT', content: 'B' },
          { type: 'TEXT', content: 'C' },
        ],
      },
    };
    const resText = validateDraftClient(draft3Text);
    expect(resText.isValid).toBe(false);
    expect(resText.errors.engravingText).toContain('chữ khắc không được vượt quá 2 mục');

    // 3 SYMBOL items should fail
    const draft3Symbol = {
      ...baseDraft,
      engraving: {
        enabled: true,
        items: [
          { type: 'SYMBOL', content: '♥' },
          { type: 'SYMBOL', content: '★' },
          { type: 'SYMBOL', content: '∞' },
        ],
      },
    };
    const resSymbol = validateDraftClient(draft3Symbol);
    expect(resSymbol.isValid).toBe(false);
    expect(resSymbol.errors.engravingSymbol).toContain('biểu tượng khắc không được vượt quá 2 mục');

    // 3 custom images should fail
    const draft3Images = {
      ...baseDraft,
      engraving: {
        enabled: true,
        text: 'Valid',
        customImages: [
          { imageUrl: 'data:1' },
          { imageUrl: 'data:2' },
          { imageUrl: 'data:3' },
        ],
      },
    };
    const resImages = validateDraftClient(draft3Images);
    expect(resImages.isValid).toBe(false);
    expect(resImages.errors.customImages).toContain('không được vượt quá 2 ảnh');
  });

  it('validates laser image preprocessing slider parameters (contrast and sharpness 0..100)', () => {
    const baseDraft = {
      packageReference: 'PKG-1',
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: '8',
    };

    const draftInvalidSlider = {
      ...baseDraft,
      engraving: {
        enabled: true,
        text: 'Valid',
        customImages: [
          { imageUrl: 'data:1', contrast: 120, sharpness: 50 },
        ],
      },
    };
    const res = validateDraftClient(draftInvalidSlider);
    expect(res.isValid).toBe(false);
    expect(res.errors.customImages).toContain('từ 0 đến 100');

    const draftValidSliders = {
      ...baseDraft,
      engraving: {
        enabled: true,
        text: 'Valid',
        customImages: [
          { imageUrl: 'data:1', contrast: 70, sharpness: 30, cropX: 10, cropY: 15 },
        ],
      },
    };
    expect(validateDraftClient(draftValidSliders).isValid).toBe(true);

    const normalized = normalizeDraftForSave(draftValidSliders);
    expect(normalized.engraving.customImages[0].contrast).toBe(70);
    expect(normalized.engraving.customImages[0].sharpness).toBe(30);
    expect(normalized.engraving.customImages[0].cropX).toBe(10);
    expect(normalized.engraving.customImages[0].cropY).toBe(15);
  });
});

describe('Design Preview Model Derivation', () => {
  it('returns fallback model when draft is null or undefined', () => {
    const preview = derivePreviewModel(null);
    expect(preview.isNeutral).toBe(true);
    expect(preview.material).toEqual({ ...MATERIAL_PALETTES['MAT-SILVER-925'], label: 'Bạc Ý 925' });
    expect(preview.shape).toEqual({ ...SHAPE_STROKES['SHP-CLASSIC-ROUND'], label: 'Tròn cổ điển' });
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
    expect(getErrorMessage(null)).toBe('Đã xảy ra lỗi không xác định.');
  });
});

describe('Difficulty Score Classification & Meta', () => {
  it('formats score to 1 decimal place and classifies 1-3 as Easy (Dễ)', () => {
    const meta1 = getDifficultyMeta(1);
    expect(meta1.score).toBe('1.0');
    expect(meta1.label).toBe('Dễ');
    expect(meta1.note).toBe('Dễ');
    expect(meta1.isVeryHard).toBe(false);
    expect(meta1.advice).toBeNull();

    const meta2 = getDifficultyMeta('2.456');
    expect(meta2.score).toBe('2.5');
    expect(meta2.label).toBe('Dễ');

    const meta3 = getDifficultyMeta(3.0);
    expect(meta3.score).toBe('3.0');
    expect(meta3.label).toBe('Dễ');
  });

  it('classifies > 3 to 6 as Medium (Trung bình)', () => {
    const meta1 = getDifficultyMeta(3.1);
    expect(meta1.score).toBe('3.1');
    expect(meta1.label).toBe('Trung bình');
    expect(meta1.isVeryHard).toBe(false);

    const meta2 = getDifficultyMeta(6.0);
    expect(meta2.score).toBe('6.0');
    expect(meta2.label).toBe('Trung bình');
  });

  it('classifies > 6 to 9 as Hard (Khó)', () => {
    const meta1 = getDifficultyMeta(6.1);
    expect(meta1.score).toBe('6.1');
    expect(meta1.label).toBe('Khó');
    expect(meta1.isVeryHard).toBe(false);

    const meta2 = getDifficultyMeta(9.0);
    expect(meta2.score).toBe('9.0');
    expect(meta2.label).toBe('Khó');
  });

  it('classifies > 9 as Very Hard (Rất khó) with user change recommendation', () => {
    const meta1 = getDifficultyMeta(9.1);
    expect(meta1.score).toBe('9.1');
    expect(meta1.label).toBe('Rất khó');
    expect(meta1.isVeryHard).toBe(true);
    expect(meta1.note).toContain('Khuyến cáo người dùng đổi lại mẫu thiết kế');
    expect(meta1.advice).toContain('Khuyến cáo người dùng đổi lại mẫu thiết kế');

    const meta2 = getDifficultyMeta('9.8');
    expect(meta2.score).toBe('9.8');
    expect(meta2.label).toBe('Rất khó');
    expect(meta2.isVeryHard).toBe(true);
  });

  it('classifies > 11 as Too Risky (Quá rủi ro) without workshop recommendation', () => {
    const meta = getDifficultyMeta(11.5);
    expect(meta.score).toBe('11.5');
    expect(meta.level).toBe('TOO_RISKY');
    expect(meta.label).toBe('Quá rủi ro');
    expect(meta.isTooRisky).toBe(true);
    expect(meta.note).toContain('Mẫu nhẫn quá rủi ro để người dùng tự tay chế tạo');
    expect(meta.advice).toContain('Mẫu nhẫn quá rủi ro để người dùng tự tay chế tạo');
  });
});

describe('Gemstone Non-overlapping Angle Distribution', () => {
  it('assigns 270 as initial angle when ring is empty', () => {
    expect(findNextAvailableAngle([])).toBe(270);
  });

  it('assigns non-overlapping distinct angles as more stones are added', () => {
    const p1 = [{ id: 'gem-1', angleDeg: 270 }];
    const a2 = findNextAvailableAngle(p1);
    expect(a2).not.toBe(270);
    expect(Math.abs(a2 - 270)).toBeGreaterThanOrEqual(16);

    const p2 = [...p1, { id: 'gem-2', angleDeg: a2 }];
    const a3 = findNextAvailableAngle(p2);
    expect(a3).not.toBe(270);
    expect(a3).not.toBe(a2);
    expect(Math.abs(a3 - 270)).toBeGreaterThanOrEqual(16);
    expect(Math.abs(a3 - a2)).toBeGreaterThanOrEqual(16);
  });

  it('prevents overlap in derivePreviewModel when multiple different gemstones share initial positions', () => {
    const draft = {
      materialReference: 'MAT-SILVER-925',
      shapeReference: 'SHP-CLASSIC-ROUND',
      ringSize: '8',
      components: [
        { reference: 'GEM-CZ-WHITE-2MM', quantity: 1 },
        { reference: 'GEM-CZ-BLUE-2MM', quantity: 1 },
        { reference: 'GEM-CZ-RED-2MM', quantity: 1 },
      ],
      gemstonePositions: [
        { id: 'gem-GEM-CZ-WHITE-2MM-0', reference: 'GEM-CZ-WHITE-2MM', index: 0, angleDeg: 270 },
        { id: 'gem-GEM-CZ-BLUE-2MM-0', reference: 'GEM-CZ-BLUE-2MM', index: 0, angleDeg: 270 },
        { id: 'gem-GEM-CZ-RED-2MM-0', reference: 'GEM-CZ-RED-2MM', index: 0, angleDeg: 270 },
      ],
    };

    const preview = derivePreviewModel(draft);
    expect(preview.gemstones).toHaveLength(3);

    const angles = preview.stones.map((g) => g.angleDeg);
    const uniqueAngles = new Set(angles);
    expect(uniqueAngles.size).toBe(3);
  });

  describe('Ring Material Pricing and Excess Weight Formula', () => {
    it('fixes base price for ring mass <= 3.0g (Silver: 900k, Copper/Steel: 750k)', () => {
      // Silver: 900k
      expect(getBaseMaterialPrice('MAT-SILVER-925')).toBe(900000);
      expect(estimateExcessMaterialCost(1.0, 'MAT-SILVER-925')).toBe(0);
      expect(estimateExcessMaterialCost(2.5, 'MAT-SILVER-925')).toBe(0);
      expect(estimateExcessMaterialCost(3.0, 'MAT-SILVER-925')).toBe(0);
      expect(estimateTotalMaterialCost(1.0, 'MAT-SILVER-925')).toBe(900000);
      expect(estimateTotalMaterialCost(3.0, 'MAT-SILVER-925')).toBe(900000);

      // Copper: 750k
      expect(getBaseMaterialPrice('MAT-COPPER')).toBe(750000);
      expect(estimateExcessMaterialCost(2.0, 'MAT-COPPER')).toBe(0);
      expect(estimateExcessMaterialCost(3.0, 'MAT-COPPER')).toBe(0);
      expect(estimateTotalMaterialCost(2.0, 'MAT-COPPER')).toBe(750000);
      expect(estimateTotalMaterialCost(3.0, 'MAT-COPPER')).toBe(750000);

      // Steel: 750k
      expect(getBaseMaterialPrice('MAT-STAINLESS-STEEL')).toBe(750000);
      expect(estimateExcessMaterialCost(3.0, 'MAT-STAINLESS-STEEL')).toBe(0);
      expect(estimateTotalMaterialCost(3.0, 'MAT-STAINLESS-STEEL')).toBe(750000);
    });

    it('calculates excess cost as 120,000 * (totalMass - 3) when mass > 3.0g', () => {
      // Silver: 4.0g -> excess 1.0g -> +120k -> total 1,020,000 VND
      expect(estimateExcessMaterialCost(4.0, 'MAT-SILVER-925')).toBe(120000);
      expect(estimateTotalMaterialCost(4.0, 'MAT-SILVER-925')).toBe(1020000);

      // Silver: 3.5g -> excess 0.5g -> +60k -> total 960,000 VND
      expect(estimateExcessMaterialCost(3.5, 'MAT-SILVER-925')).toBe(60000);
      expect(estimateTotalMaterialCost(3.5, 'MAT-SILVER-925')).toBe(960000);

      // Copper: 4.0g -> excess 1.0g -> +120k -> total 870,000 VND
      expect(estimateExcessMaterialCost(4.0, 'MAT-COPPER')).toBe(120000);
      expect(estimateTotalMaterialCost(4.0, 'MAT-COPPER')).toBe(870000);

      // Steel: 3.5g -> excess 0.5g -> +60k -> total 810,000 VND
      expect(estimateExcessMaterialCost(3.5, 'MAT-STAINLESS-STEEL')).toBe(60000);
      expect(estimateTotalMaterialCost(3.5, 'MAT-STAINLESS-STEEL')).toBe(810000);
    });
  });
});



