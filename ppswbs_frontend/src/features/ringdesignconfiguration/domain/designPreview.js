// Derives a 2D local preview model for the interactive SVG RingPreview

export const MATERIAL_PALETTES = {
  'MAT-SILVER-925': {
    primary: '#D6D8DC',
    highlight: '#F3F4F6',
    shadow: '#9CA3AF',
    label: 'Bạc Ý 925',
  },
  'MAT-COPPER': {
    primary: '#C86D51',
    highlight: '#F3A683',
    shadow: '#8C3D26',
    label: 'Đồng (Copper)',
  },
  'MAT-STAINLESS-STEEL': {
    primary: '#94A3B8',
    highlight: '#F1F5F9',
    shadow: '#475569',
    label: 'Thép không gỉ 316L',
  },
  // Fallbacks for historical or legacy data
  'MAT-GOLD-14K': {
    primary: '#EAB308',
    highlight: '#FEF08A',
    shadow: '#CA8A04',
    label: 'Vàng 14K (Ngừng hỗ trợ)',
  },
  'MAT-ROSE-GOLD-14K': {
    primary: '#E0A899',
    highlight: '#FCE7F3',
    shadow: '#B45309',
    label: 'Vàng Hồng 14K (Ngừng hỗ trợ)',
  },
  'MAT-WHITE-GOLD-14K': {
    primary: '#E2E8F0',
    highlight: '#FFFFFF',
    shadow: '#64748B',
    label: 'Vàng Trắng 14K (Ngừng hỗ trợ)',
  },
};

export const SHAPE_STROKES = {
  'SHP-CLASSIC-ROUND': { strokeWidth: 14, style: 'round', label: 'Tròn cổ điển' },
  'SHP-COMFORT-FIT': { strokeWidth: 18, style: 'comfort', label: 'Vát cong lòng trong' },
  'SHP-TWISTED': { strokeWidth: 15, style: 'twisted', label: 'Dáng xoắn vô cực' },
  'SHP-HAMMERED': { strokeWidth: 16, style: 'hammered', label: 'Búa gõ thủ công' },
  'SHP-SINGLE-TWIST': { strokeWidth: 15, style: 'single-twist', label: 'Dáng xoắn đơn' },
  'SHP-WAVE': { strokeWidth: 15, style: 'wave', label: 'Dáng lượn sóng' },
  'SHP-BALL-PEEN': { strokeWidth: 16, style: 'ball-peen', label: 'Bề mặt búa bi hạt tròn' },
  'SHP-FLAT-BAND': { strokeWidth: 14, style: 'flat', label: 'Đai phẳng công thái học' },
};

export const GEMSTONE_PALETTES = {
  'GEM-CZ-WHITE-2MM': { fill: '#F8FAFC', stroke: '#94A3B8', glow: '#E2E8F0', label: 'CZ Trắng 2mm' },
  'GEM-CZ-BLUE-2MM': { fill: '#38BDF8', stroke: '#0284C7', glow: '#BAE6FD', label: 'CZ Lam 2mm' },
  'GEM-CZ-RED-2MM': { fill: '#F43F5E', stroke: '#BE123C', glow: '#FECDD3', label: 'CZ Đỏ 2mm' },
  'GEM-MOISSANITE-3MM': { fill: '#FFFFFF', stroke: '#6366F1', glow: '#C7D2FE', label: 'Moissanite 3mm' },
};

export function getRingCircumferenceMm(size) {
  const s = parseInt(size, 10);
  if (isNaN(s)) return 48;
  return s + 40; // Size 6 = 46mm, Size 10 = 50mm, Size 20 = 60mm, Size 29 = 69mm
}

export function getRingDiameterMm(size) {
  const circ = getRingCircumferenceMm(size);
  return (circ / Math.PI).toFixed(1);
}

export const MATERIAL_DENSITIES = {
  'MAT-SILVER-925': 10.36,
  'MAT-COPPER': 8.96,
  'MAT-STAINLESS-STEEL': 7.99,
  'MAT-GOLD-14K': 13.0,
  'MAT-ROSE-GOLD-14K': 13.0,
  'MAT-WHITE-GOLD-14K': 13.0,
};

export const BASE_WEIGHT_LIMIT_GRAMS = 3.0;
export const EXCESS_RATE_PER_GRAM_SILVER = 120000;
export const EXCESS_RATE_PER_GRAM_COPPER = 40000;
export const EXCESS_RATE_PER_GRAM_STEEL = 50000;

export function calculateRingVolumeMm3(size, thicknessMm = 1.8, bandWidthMm = 3.5) {
  const P = getRingCircumferenceMm(size);
  const T = Number(thicknessMm) || 1.8;
  const W = Number(bandWidthMm) || 3.5;
  return (P + Math.PI * T) * T * W;
}

export function estimateRingWeightGrams(size, thicknessMm = 1.8, bandWidthMm = 3.5, materialReference = 'MAT-SILVER-925') {
  const volMm3 = calculateRingVolumeMm3(size, thicknessMm, bandWidthMm);
  const volCm3 = volMm3 / 1000.0;
  const density = MATERIAL_DENSITIES[materialReference] || 10.36;
  const weight = volCm3 * density;
  return Math.round(weight * 100) / 100;
}

export function estimateExcessMaterialCost(weightGrams, materialReference = 'MAT-SILVER-925') {
  const w = Number(weightGrams) || 0;
  if (w <= BASE_WEIGHT_LIMIT_GRAMS) return 0;
  const excess = w - BASE_WEIGHT_LIMIT_GRAMS;
  let rate = EXCESS_RATE_PER_GRAM_SILVER;
  if (materialReference === 'MAT-COPPER') rate = EXCESS_RATE_PER_GRAM_COPPER;
  else if (materialReference === 'MAT-STAINLESS-STEEL') rate = EXCESS_RATE_PER_GRAM_STEEL;
  return Math.round(excess * rate);
}

export function calculateDefaultAngles(count) {
  if (count <= 0) return [];
  if (count === 1) return [270];
  const step = Math.min(22, 140 / (count - 1));
  const start = 270 - ((count - 1) * step) / 2;
  return Array.from({ length: count }, (_, i) => Math.round((start + i * step) * 10) / 10);
}

export function derivePreviewModel(draft) {
  if (!draft) {
    return {
      material: MATERIAL_PALETTES['MAT-SILVER-925'],
      shape: SHAPE_STROKES['SHP-CLASSIC-ROUND'],
      ringSize: '8',
      circumferenceMm: 48,
      diameterMm: '15.3',
      thicknessMm: 1.8,
      bandWidthMm: 3.5,
      gemstones: [],
      attachments: [],
      engravingText: '',
      isNeutral: true,
    };
  }

  const material = MATERIAL_PALETTES[draft.materialReference] || MATERIAL_PALETTES['MAT-SILVER-925'];
  const shape = SHAPE_STROKES[draft.shapeReference] || SHAPE_STROKES['SHP-CLASSIC-ROUND'];

  const thicknessMm = typeof draft.thicknessMm === 'number'
    ? Math.min(3.0, Math.max(1.0, Math.round(draft.thicknessMm * 10) / 10))
    : 1.8;

  const bandWidthMm = typeof draft.bandWidthMm === 'number'
    ? Math.min(6.0, Math.max(1.5, Math.round(draft.bandWidthMm * 10) / 10))
    : 3.5;

  const ringSize = String(draft.ringSize || '8');
  const circumferenceMm = getRingCircumferenceMm(ringSize);
  const diameterMm = getRingDiameterMm(ringSize);

  const rawPositions = draft.gemstonePositions || [];
  const totalStonesNeeded = (draft.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
  const defaultAngles = calculateDefaultAngles(totalStonesNeeded);

  let angleIndex = 0;
  const stones = [];

  const gemstones = (draft.components || []).map((comp) => {
    const qty = Math.max(1, Number(comp.quantity) || 1);
    const gemMeta = GEMSTONE_PALETTES[comp.reference] || {
      fill: '#FFFFFF',
      stroke: '#94A3B8',
      glow: '#F1F5F9',
      label: comp.reference,
    };

    for (let i = 0; i < qty; i++) {
      const stoneId = `gem-${comp.reference}-${i}`;
      const existingPos = rawPositions.find((p) => p.id === stoneId || (p.reference === comp.reference && p.index === i));
      const angleDeg = existingPos ? existingPos.angleDeg : (defaultAngles[angleIndex] ?? 270);
      angleIndex++;

      const rad = (angleDeg * Math.PI) / 180;
      const cx = Math.round((150 + 85 * Math.cos(rad)) * 10) / 10;
      const cy = Math.round((135 + 85 * Math.sin(rad)) * 10) / 10;

      stones.push({
        id: stoneId,
        reference: comp.reference,
        index: i,
        angleDeg,
        cx,
        cy,
        ...gemMeta,
      });
    }

    return {
      ...gemMeta,
      reference: comp.reference,
      quantity: qty,
      placement: comp.placement || 'CENTER',
    };
  });

  const attachments = (draft.attachments || []).map((att) => ({
    reference: att.reference,
    quantity: Number(att.quantity) || 1,
    placement: att.placement || 'OUTER_SURFACE',
  }));

  const standardEngravingCost = draft.engraving?.enabled ? 80000 : 0;
  const customImages = draft.engraving?.customImages || [];
  const customImagesCost = customImages.length * 100000;
  const totalEngravingCost = standardEngravingCost + customImagesCost;

  const engravingItems = draft.engraving?.enabled ? (draft.engraving.items || []) : [];
  let engravingText = '';
  if (draft.engraving?.enabled) {
    if (draft.engraving.text && draft.engraving.text.trim()) {
      engravingText = draft.engraving.text.trim();
    } else if (engravingItems.length > 0) {
      engravingText = engravingItems.map((it) => it.content).filter(Boolean).join(' | ');
    }
  }

  const TEXTURED_SHAPES = ['SHP-HAMMERED', 'SHP-BALL-PEEN', 'SHP-SINGLE-TWIST', 'SHP-TWISTED', 'SHP-WAVE'];
  const isTexturedSurface = TEXTURED_SHAPES.includes(draft.shapeReference) ||
    Boolean(draft.sourceModelReference && (
      draft.sourceModelReference.includes('HAMMERED') ||
      draft.sourceModelReference.includes('TWIST') ||
      draft.sourceModelReference.includes('WAVE')
    ));

  const hasOuterEngraving = (
    draft.engraving?.enabled && (
      draft.engraving.placement === 'OUTER_BAND' ||
      engravingItems.some((it) => it.placement === 'OUTER_BAND')
    )
  ) || customImages.some((img) => img.placement === 'OUTER_BAND');

  const showSurfaceTextureWarning = hasOuterEngraving && isTexturedSurface;

  const estimatedWeightGrams = estimateRingWeightGrams(ringSize, thicknessMm, bandWidthMm, draft.materialReference);
  const excessWeightGrams = Math.max(0, Math.round((estimatedWeightGrams - BASE_WEIGHT_LIMIT_GRAMS) * 100) / 100);
  const excessMaterialCost = estimateExcessMaterialCost(estimatedWeightGrams, draft.materialReference);

  return {
    material,
    shape,
    ringSize,
    circumferenceMm,
    diameterMm,
    thicknessMm,
    bandWidthMm,
    estimatedWeightGrams,
    excessWeightGrams,
    excessMaterialCost,
    gemstones,
    stones,
    attachments,
    engravingText,
    engravingItems,
    customImages,
    standardEngravingCost,
    customImagesCost,
    totalEngravingCost,
    isTexturedSurface,
    hasOuterEngraving,
    showSurfaceTextureWarning,
    isNeutral: false,
  };
}

