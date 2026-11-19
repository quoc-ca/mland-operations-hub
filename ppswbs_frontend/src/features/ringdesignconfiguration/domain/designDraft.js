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

export function validateDraftClient(draft, lang = 'vi') {
  const errors = {};
  const isEn = lang === 'en';

  if (!draft.packageReference) {
    errors.packageReference = isEn ? 'Please select a workshop package.' : 'Vui lòng chọn gói workshop.';
  }
  if (!draft.materialReference) {
    errors.materialReference = isEn ? 'Please select a ring material.' : 'Vui lòng chọn chất liệu nhẫn.';
  }
  if (!draft.shapeReference) {
    errors.shapeReference = isEn ? 'Please select a band shape.' : 'Vui lòng chọn phom dáng đai nhẫn.';
  }
  if (!draft.ringSize) {
    errors.ringSize = isEn ? 'Please select a ring size.' : 'Vui lòng chọn size nhẫn.';
  }

  const totalGemstones = (draft.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
  if (totalGemstones > 10) {
    errors.components = isEn ? 'Total gemstone quantity cannot exceed 10.' : 'Tổng số lượng đá quý không được vượt quá 10 viên.';
  }

  const customImages = draft.engraving?.customImages || [];
  if (customImages.length > 2) {
    errors.customImages = isEn ? 'Maximum 2 custom laser images allowed.' : 'Số lượng ảnh khắc laser không được vượt quá 2 ảnh.';
  }

  customImages.forEach((img, idx) => {
    if (img.contrast !== undefined && (img.contrast < 0 || img.contrast > 100)) {
      errors[`customImage_contrast_${idx}`] = isEn ? 'Contrast must be between 0 and 100.' : 'Độ tương phản phải nằm trong khoảng từ 0 đến 100.';
      if (!errors.customImages) {
        errors.customImages = isEn ? 'Image contrast must be between 0 and 100.' : 'Độ tương phản ảnh phải nằm trong khoảng từ 0 đến 100.';
      }
    }
    if (img.sharpness !== undefined && (img.sharpness < 0 || img.sharpness > 100)) {
      errors[`customImage_sharpness_${idx}`] = isEn ? 'Sharpness must be between 0 and 100.' : 'Độ sắc nét phải nằm trong khoảng từ 0 đến 100.';
      if (!errors.customImages) {
        errors.customImages = isEn ? 'Image sharpness must be between 0 and 100.' : 'Độ sắc nét ảnh phải nằm trong khoảng từ 0 đến 100.';
      }
    }
  });

  if (draft.engraving?.enabled) {
    const items = draft.engraving.items || [];
    const textItems = items.filter((it) => (it.type || 'TEXT').toUpperCase() !== 'SYMBOL');
    const symbolItems = items.filter((it) => (it.type || 'TEXT').toUpperCase() === 'SYMBOL');

    if (items.length > 2) {
      errors.engravingText = isEn ? 'Maximum 2 engraving items allowed.' : 'Số lượng mục khắc chữ & biểu tượng không được vượt quá 2 mục.';
      if (textItems.length > 2) {
        errors.engravingText = isEn ? 'Maximum 2 text engraving items allowed.' : 'Số lượng chữ khắc không được vượt quá 2 mục.';
      }
      if (symbolItems.length > 2) {
        errors.engravingSymbol = isEn ? 'Maximum 2 symbol engraving items allowed.' : 'Số lượng biểu tượng khắc không được vượt quá 2 mục.';
      }
    }

    if (items.length > 0) {
      const emptyItem = items.find((it) => !it.content || it.content.trim().length === 0);
      if (emptyItem) {
        errors.engravingText = isEn ? 'Please enter engraving content for all added positions.' : 'Vui lòng nhập nội dung cho tất cả các vị trí khắc đã thêm.';
      }
      const tooLongItem = items.find((it) => it.content && it.content.trim().length > 50);
      if (tooLongItem) {
        errors.engravingText = isEn ? 'Engraving content cannot exceed 50 characters per item.' : 'Nội dung mỗi vị trí khắc tối đa 50 ký tự.';
      }
    } else if (!draft.engraving.text || draft.engraving.text.trim().length === 0) {
      errors.engravingText = isEn ? 'Please enter engraving text or disable engraving.' : 'Vui lòng nhập nội dung khắc hoặc tắt tùy chọn khắc.';
    } else if (draft.engraving.text.trim().length > 50) {
      errors.engravingText = isEn ? 'Engraving text cannot exceed 50 characters.' : 'Nội dung khắc tối đa 50 ký tự.';
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
    imageReference: img.imageReference || img.imageUrl,
    processedPreviewReference: img.processedPreviewReference || img.previewDataUrl || img.previewUrl || img.imageUrl,
    previewDataUrl: img.previewDataUrl || img.processedPreviewReference || img.imageUrl,
    cropX: Math.round(img.cropX || 0),
    cropY: Math.round(img.cropY || 0),
    cropWidth: 160,
    cropHeight: 160,
    contrast: img.contrast !== undefined ? Math.round(Number(img.contrast)) : 50,
    sharpness: img.sharpness !== undefined ? Math.round(Number(img.sharpness)) : 50,
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
