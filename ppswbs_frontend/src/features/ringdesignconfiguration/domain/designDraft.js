// Feature-012 Draft domain logic and normalization
import { viT } from './designFormat';

export function createDefaultDraft(packageReference = '', sourceModel = null) {
  if (sourceModel) {
    return {
      packageReference,
      sourceModelReference: sourceModel.modelReference || null,
      materialReference: sourceModel.materialReference || 'MAT-SILVER-925',
      shapeReference: sourceModel.shapeReference || 'SHP-CLASSIC-ROUND',
      ringSize: sourceModel.ringSize ? String(sourceModel.ringSize) : '8',
      thicknessMm: Number(sourceModel.thicknessMm) || 1.8,
      bandWidthMm: Number(sourceModel.bandWidthMm) || 3.5,
      components: sourceModel.defaultComponents?.components || (Array.isArray(sourceModel.defaultComponents) ? sourceModel.defaultComponents : []),
      attachments: sourceModel.defaultComponents?.attachments || (Array.isArray(sourceModel.defaultAttachments) ? sourceModel.defaultAttachments : []),
      gemstonePositions: sourceModel.defaultComponents?.gemstonePositions || [],
      engraving: {
        enabled: false,
        text: '',
        placement: 'INNER_BAND',
        items: [],
        customImages: [],
      },
    };
  }

  return {
    packageReference,
    sourceModelReference: null,
    materialReference: 'MAT-SILVER-925',
    shapeReference: 'SHP-CLASSIC-ROUND',
    ringSize: '8',
    thicknessMm: 1.8,
    bandWidthMm: 3.5,
    components: [],
    attachments: [],
    gemstonePositions: [],
    engraving: {
      enabled: false,
      text: '',
      placement: 'INNER_BAND',
      items: [],
      customImages: [],
    },
  };
}

export function validateDraftClient(draft, t = viT) {
  const errors = {};

  if (!draft.packageReference) {
    errors.packageReference = t('validation.package');
  }
  if (!draft.materialReference) {
    errors.materialReference = t('validation.material');
  }
  if (!draft.shapeReference) {
    errors.shapeReference = t('validation.shape');
  }
  if (!draft.ringSize) {
    errors.ringSize = t('validation.size');
  }

  const totalGemstones = (draft.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
  if (totalGemstones > 10) {
    errors.components = t('validation.gemstones');
  }

  const customImages = draft.engraving?.customImages || [];
  if (customImages.length > 2) {
    errors.customImages = t('validation.images');
  }

  customImages.forEach((img, idx) => {
    if (img.contrast !== undefined && (img.contrast < 0 || img.contrast > 100)) {
      errors[`customImage_contrast_${idx}`] = t('validation.contrast');
      if (!errors.customImages) {
        errors.customImages = t('validation.imageContrast');
      }
    }
    if (img.sharpness !== undefined && (img.sharpness < 0 || img.sharpness > 100)) {
      errors[`customImage_sharpness_${idx}`] = t('validation.sharpness');
      if (!errors.customImages) {
        errors.customImages = t('validation.imageSharpness');
      }
    }
  });

  if (draft.engraving?.enabled) {
    const items = draft.engraving.items || [];
    const textItems = items.filter((it) => (it.type || 'TEXT').toUpperCase() !== 'SYMBOL');
    const symbolItems = items.filter((it) => (it.type || 'TEXT').toUpperCase() === 'SYMBOL');

    if (items.length > 2) {
      errors.engravingText = t('validation.engravingItems');
      if (textItems.length > 2) {
        errors.engravingText = t('validation.engravingTextItems');
      }
      if (symbolItems.length > 2) {
        errors.engravingSymbol = t('validation.engravingSymbolItems');
      }
    }

    if (items.length > 0) {
      const emptyItem = items.find((it) => !it.content || it.content.trim().length === 0);
      if (emptyItem) {
        errors.engravingText = t('validation.engravingEmpty');
      }
      const tooLongItem = items.find((it) => it.content && it.content.length > 50);
      if (tooLongItem) {
        errors.engravingText = t('validation.engravingItemLong');
      }
    } else if (!draft.engraving.text || draft.engraving.text.trim().length === 0) {
      errors.engravingText = t('validation.engravingMissing');
    } else if (draft.engraving.text.length > 50) {
      errors.engravingText = t('validation.engravingLong');
    }

    // Check conflict: Cannot engrave outer band if stones are mounted on outer band
    if (totalGemstones > 0) {
      const hasOuterItem = items.some((it) => it.placement === 'OUTER_BAND');
      const hasOuterPlacement = draft.engraving.placement === 'OUTER_BAND';
      const hasOuterImg = customImages.some((img) => img.placement === 'OUTER_BAND');
      if (hasOuterItem || hasOuterPlacement || hasOuterImg) {
        errors.engraving = t('validation.engravingOuterStoneConflict') || 'Do mẫu thiết kế có đính đá ở ngoài nên không thể khắc lazer';
        if (!errors.engravingText) {
          errors.engravingText = t('validation.engravingOuterStoneConflict') || 'Do mẫu thiết kế có đính đá ở ngoài nên không thể khắc lazer';
        }
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function normalizeDraftForSave(draft) {
  const engravingItems = (draft.engraving?.items || []).map((it, idx) => ({
    id: it.id || `eng-${idx + 1}`,
    type: it.type || 'TEXT',
    content: (it.content || '').trim(),
    placement: it.placement || 'INNER_BAND',
  }));

  const customImages = (draft.engraving?.customImages || []).map((img, idx) => ({
    id: img.id || `img-${idx + 1}`,
    placement: img.placement || 'INNER_BAND',
    imageUrl: img.imageUrl,
    cropX: Math.round(img.cropX || 0),
    cropY: Math.round(img.cropY || 0),
    cropWidth: 160,
    cropHeight: 160,
    contrast: img.contrast !== undefined ? Math.round(Number(img.contrast)) : 50,
    sharpness: img.sharpness !== undefined ? Math.round(Number(img.sharpness)) : 50,
    processedPreviewReference: img.processedPreviewReference || img.previewDataUrl || img.previewUrl || img.imageUrl,
    price: 100000,
  }));

  let primaryText = draft.engraving?.text ? draft.engraving.text.trim() : null;
  let primaryPlacement = draft.engraving?.placement || 'INNER_BAND';
  if (!primaryText && engravingItems.length > 0) {
    primaryText = engravingItems.map((it) => it.content).filter(Boolean).join(' | ');
    primaryPlacement = engravingItems[0].placement;
  }

  return {
    packageReference: draft.packageReference,
    materialReference: draft.materialReference,
    shapeReference: draft.shapeReference,
    ringSize: String(draft.ringSize),
    thicknessMm: Number(draft.thicknessMm) || 1.8,
    bandWidthMm: Number(draft.bandWidthMm) || 3.5,
    components: (draft.components || []).map((c) => ({
      reference: c.reference,
      quantity: Number(c.quantity) || 1,
      variant: c.variant || null,
      placement: c.placement || 'CENTER',
    })),
    attachments: (draft.attachments || []).map((a) => ({
      reference: a.reference,
      quantity: Number(a.quantity) || 1,
      variant: a.variant || null,
      placement: a.placement || 'OUTER_SURFACE',
    })),
    engraving: {
      enabled: Boolean(draft.engraving?.enabled),
      text: draft.engraving?.enabled ? primaryText : null,
      placement: draft.engraving?.enabled ? primaryPlacement : null,
      items: draft.engraving?.enabled ? engravingItems : [],
      customImages,
    },
    gemstonePositions: (draft.gemstonePositions || []).map((p) => ({
      id: p.id,
      reference: p.reference,
      index: Number(p.index) || 0,
      angleDeg: Number(p.angleDeg) || 270,
    })),
  };
}
