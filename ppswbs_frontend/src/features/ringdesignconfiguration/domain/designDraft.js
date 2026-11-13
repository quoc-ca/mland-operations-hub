// Feature-012 Draft domain logic and normalization

export function createDefaultDraft(packageReference = '', sourceModel = null) {
  if (sourceModel) {
    return {
      packageReference,
      sourceModelReference: sourceModel.modelReference || null,
      materialReference: sourceModel.materialReference || 'MAT-SILVER-925',
      shapeReference: sourceModel.shapeReference || 'SHP-CLASSIC-ROUND',
      ringSize: '8',
      components: sourceModel.defaultComponents?.components || [],
      attachments: sourceModel.defaultComponents?.attachments || [],
      engraving: {
        enabled: false,
        text: '',
        placement: 'INNER_BAND',
      },
    };
  }

  return {
    packageReference,
    sourceModelReference: null,
    materialReference: 'MAT-SILVER-925',
    shapeReference: 'SHP-CLASSIC-ROUND',
    ringSize: '8',
    components: [],
    attachments: [],
    engraving: {
      enabled: false,
      text: '',
      placement: 'INNER_BAND',
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

  if (draft.engraving?.enabled) {
    if (!draft.engraving.text || draft.engraving.text.trim().length === 0) {
      errors.engravingText = 'Vui lòng nhập nội dung khắc hoặc tắt tùy chọn khắc.';
    } else if (draft.engraving.text.trim().length > 50) {
      errors.engravingText = 'Nội dung khắc tối đa 50 ký tự.';
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function normalizeDraftForSave(draft) {
  return {
    packageReference: draft.packageReference,
    materialReference: draft.materialReference,
    shapeReference: draft.shapeReference,
    ringSize: String(draft.ringSize),
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
      text: draft.engraving?.enabled ? draft.engraving.text?.trim() : null,
      placement: draft.engraving?.enabled ? draft.engraving.placement || 'INNER_BAND' : null,
    },
  };
}

