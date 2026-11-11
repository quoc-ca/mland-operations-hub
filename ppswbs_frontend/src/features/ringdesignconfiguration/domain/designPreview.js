// Derives a 2D local preview model for the interactive SVG RingPreview

export const MATERIAL_PALETTES = {
  'MAT-SILVER-925': {
    primary: '#D6D8DC',
    highlight: '#F3F4F6',
    shadow: '#9CA3AF',
    label: 'Bạc Ý 925',
  },
  'MAT-GOLD-14K': {
    primary: '#EAB308',
    highlight: '#FEF08A',
    shadow: '#CA8A04',
    label: 'Vàng 14K',
  },
  'MAT-ROSE-GOLD-14K': {
    primary: '#E0A899',
    highlight: '#FCE7F3',
    shadow: '#B45309',
    label: 'Vàng Hồng 14K',
  },
  'MAT-WHITE-GOLD-14K': {
    primary: '#E2E8F0',
    highlight: '#FFFFFF',
    shadow: '#64748B',
    label: 'Vàng Trắng 14K',
  },
};

export const SHAPE_STROKES = {
  'SHP-CLASSIC-ROUND': { strokeWidth: 14, style: 'round', label: 'Tròn cổ điển' },
  'SHP-COMFORT-FIT': { strokeWidth: 18, style: 'comfort', label: 'Vát cong lòng trong' },
  'SHP-TWISTED': { strokeWidth: 15, style: 'twisted', label: 'Dáng xoắn vô cực' },
  'SHP-HAMMERED': { strokeWidth: 16, style: 'hammered', label: 'Búa gõ thủ công' },
};

export const GEMSTONE_PALETTES = {
  'GEM-CZ-WHITE-2MM': { fill: '#F8FAFC', stroke: '#94A3B8', glow: '#E2E8F0', label: 'CZ Trắng 2mm' },
  'GEM-CZ-BLUE-2MM': { fill: '#38BDF8', stroke: '#0284C7', glow: '#BAE6FD', label: 'CZ Lam 2mm' },
  'GEM-CZ-RED-2MM': { fill: '#F43F5E', stroke: '#BE123C', glow: '#FECDD3', label: 'CZ Đỏ 2mm' },
  'GEM-MOISSANITE-3MM': { fill: '#FFFFFF', stroke: '#6366F1', glow: '#C7D2FE', label: 'Moissanite 3mm' },
};

export function derivePreviewModel(draft) {
  if (!draft) {
    return {
      material: MATERIAL_PALETTES['MAT-SILVER-925'],
      shape: SHAPE_STROKES['SHP-CLASSIC-ROUND'],
      ringSize: '8',
      gemstones: [],
      attachments: [],
      engravingText: '',
      isNeutral: true,
    };
  }

  const material = MATERIAL_PALETTES[draft.materialReference] || MATERIAL_PALETTES['MAT-SILVER-925'];
  const shape = SHAPE_STROKES[draft.shapeReference] || SHAPE_STROKES['SHP-CLASSIC-ROUND'];

  const gemstones = (draft.components || []).map((comp) => {
    const gemMeta = GEMSTONE_PALETTES[comp.reference] || {
      fill: '#FFFFFF',
      stroke: '#94A3B8',
      glow: '#F1F5F9',
      label: comp.reference,
    };
    return {
      ...gemMeta,
      reference: comp.reference,
      quantity: Number(comp.quantity) || 1,
      placement: comp.placement || 'CENTER',
    };
  });

  const attachments = (draft.attachments || []).map((att) => ({
    reference: att.reference,
    quantity: Number(att.quantity) || 1,
    placement: att.placement || 'OUTER_SURFACE',
  }));

  const engravingText = draft.engraving?.enabled && draft.engraving?.text
    ? draft.engraving.text.trim()
    : '';

  return {
    material,
    shape,
    ringSize: String(draft.ringSize || '8'),
    gemstones,
    attachments,
    engravingText,
    isNeutral: false,
  };
}

