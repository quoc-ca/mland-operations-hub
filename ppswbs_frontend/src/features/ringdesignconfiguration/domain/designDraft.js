// Feature-012 Draft domain logic and normalization

export function createDefaultDraft(packageReference = '', sourceModel = null) {
  if (sourceModel) {
    return {
      packageReference,
      sourceModelReference: sourceModel.modelReference || null,
      materialReference: sourceModel.materialReference || 'MAT-SILVER-925',
      shapeReference: sourceModel.shapeReference || 'SHP-CLASSIC-ROUND',
      ringSize: '8',
      thicknessMm: Number(sourceModel.thicknessMm) || 1.8,
      bandWidthMm: Number(sourceModel.bandWidthMm) || 3.5,
      components: sourceModel.defaultComponents?.components || [],
      attachments: sourceModel.defaultComponents?.attachments || [],
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

export function validateDraftClient(draft) {
  const errors = {};

  if (!draft.packageReference) {
    errors.packageReference = 'Vui lòng chọn gói workshop.';
  }
  if (!draft.materialReference) {
    errors.materialReference = 'Vui lòng chọn chất liệu nhẫn.';
  }
  if (!draft.shapeReference) {
    errors.shapeReference = 'Vui lòng chọn phom dáng đai nhẫn.';
  }
  if (!draft.ringSize) {
    errors.ringSize = 'Vui lòng chọn size nhẫn.';
  }

  const totalGemstones = (draft.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 0), 0);
  if (totalGemstones > 10) {
    errors.gemstones = 'Tổng số lượng đá quý không được vượt quá 10 viên.';
  }

  if (draft.engraving?.enabled) {
    const items = draft.engraving.items || [];
    const textItems = items.filter((it) => it.type !== 'SYMBOL');
    const symbolItems = items.filter((it) => it.type === 'SYMBOL');
    const customImagesList = draft.engraving?.customImages || draft.customImages || [];

    if (textItems.length > 2) {
      errors.engravingText = 'Số lượng mục khắc chữ tối đa là 2.';
    }
    if (symbolItems.length > 2) {
      errors.engravingSymbol = 'Số lượng mục khắc biểu tượng tối đa là 2.';
    }
    if (items.length > 2 && textItems.length <= 2 && symbolItems.length <= 2) {
      errors.engravingText = 'Số lượng vị trí khắc tối đa là 2.';
    }

    if (items.length > 0) {
      const emptyItem = items.find((it) => !it.content || it.content.trim().length === 0);
      if (emptyItem && customImagesList.length === 0) {
        errors.engravingText = 'Vui lòng nhập nội dung cho tất cả các vị trí khắc đã thêm.';
      }
      const tooLongItem = items.find((it) => it.content && it.content.trim().length > 50);
      if (tooLongItem) {
        errors.engravingText = 'Nội dung mỗi vị trí khắc tối đa 50 ký tự.';
      }
    } else if (!draft.engraving.text || draft.engraving.text.trim().length === 0) {
      if (customImagesList.length === 0) {
        errors.engravingText = 'Vui lòng nhập nội dung khắc hoặc tắt tùy chọn khắc.';
      }
    } else if (draft.engraving.text.trim().length > 50) {
      errors.engravingText = 'Nội dung khắc tối đa 50 ký tự.';
    }
  }

  const customImagesList = draft.engraving?.customImages || draft.customImages || [];
  if (customImagesList.length > 2) {
    errors.engravingImage = 'Số lượng ảnh khắc laser tối đa là 2.';
  }
  for (const img of customImagesList) {
    if (img.contrast !== undefined && (Number(img.contrast) < 0 || Number(img.contrast) > 100)) {
      errors.engravingImagePreprocessing = 'Độ tương phản phải nằm trong khoảng từ 0 đến 100.';
    }
    if (img.sharpness !== undefined && (Number(img.sharpness) < 0 || Number(img.sharpness) > 100)) {
      errors.engravingImagePreprocessing = 'Độ sắc nét phải nằm trong khoảng từ 0 đến 100.';
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function normalizeDraftForSave(draft) {
  const rawItems = draft.engraving?.items || [];
  const engravingItems = rawItems
    .map((it, idx) => ({
      id: it.id || `eng-${idx + 1}`,
      type: it.type || 'TEXT',
      content: (it.content || '').trim(),
      placement: it.placement || 'INNER_BAND',
    }))
    .filter((it) => it.content.length > 0);

  const customImages = (draft.engraving?.customImages || draft.customImages || []).map((img, idx) => ({
    id: img.id || `img-${idx + 1}`,
    placement: img.placement || 'INNER_BAND',
    imageUrl: img.imageUrl,
    previewDataUrl: img.previewDataUrl || img.imageUrl,
    processedPreviewReference: img.processedPreviewReference || null,
    cropX: Math.round(img.cropX || 0),
    cropY: Math.round(img.cropY || 0),
    cropWidth: 160,
    cropHeight: 160,
    contrast: img.contrast !== undefined ? Number(img.contrast) : 50,
    sharpness: img.sharpness !== undefined ? Number(img.sharpness) : 50,
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
