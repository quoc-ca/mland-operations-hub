import { useState, useMemo, useCallback } from 'react';
import { createDefaultDraft, normalizeDraftForSave, validateDraftClient } from '../domain/designDraft';
import { derivePreviewModel } from '../domain/designPreview';

export function useWorkshopDesignDraft(packageReference, initialModel = null) {
  const [draft, setDraft] = useState(() => createDefaultDraft(packageReference, initialModel));
  const [isDirty, setIsDirty] = useState(false);

  const previewModel = useMemo(() => derivePreviewModel(draft), [draft]);
  const clientValidation = useMemo(() => validateDraftClient(draft), [draft]);

  const updateMaterial = useCallback((materialReference) => {
    setDraft((prev) => ({ ...prev, materialReference }));
    setIsDirty(true);
  }, []);

  const updateShape = useCallback((shapeReference) => {
    setDraft((prev) => ({ ...prev, shapeReference }));
    setIsDirty(true);
  }, []);

  const updateRingSize = useCallback((ringSize) => {
    setDraft((prev) => ({ ...prev, ringSize: String(ringSize) }));
    setIsDirty(true);
  }, []);

  const toggleComponent = useCallback((componentReference, maxQuantity = 10) => {
    setDraft((prev) => {
      const existing = prev.components.find((c) => c.reference === componentReference);
      let updated;
      if (existing) {
        updated = prev.components.filter((c) => c.reference !== componentReference);
      } else {
        updated = [...prev.components, { reference: componentReference, quantity: 1, placement: 'CENTER' }];
      }
      return { ...prev, components: updated };
    });
    setIsDirty(true);
  }, []);

  const updateComponentQuantity = useCallback((componentReference, quantity) => {
    setDraft((prev) => ({
      ...prev,
      components: prev.components.map((c) =>
        c.reference === componentReference ? { ...c, quantity: Math.max(1, Number(quantity) || 1) } : c
      ),
    }));
    setIsDirty(true);
  }, []);

  const toggleAttachment = useCallback((attachmentReference) => {
    setDraft((prev) => {
      const existing = prev.attachments.find((a) => a.reference === attachmentReference);
      let updated;
      if (existing) {
        updated = prev.attachments.filter((a) => a.reference !== attachmentReference);
      } else {
        updated = [...prev.attachments, { reference: attachmentReference, quantity: 1, placement: 'OUTER_SURFACE' }];
      }
      return { ...prev, attachments: updated };
    });
    setIsDirty(true);
  }, []);

  const updateEngraving = useCallback((engravingPatch) => {
    setDraft((prev) => ({
      ...prev,
      engraving: { ...prev.engraving, ...engravingPatch },
    }));
    setIsDirty(true);
  }, []);

  const resetDraftFromModel = useCallback((model) => {
    setDraft(createDefaultDraft(packageReference, model));
    setIsDirty(false);
  }, [packageReference]);

  const resetFromSavedDesign = useCallback((designResponse) => {
    if (!designResponse) return;
    const snapshot = designResponse.componentSnapshot || {};
    setDraft({
      packageReference: designResponse.packageReference,
      materialReference: snapshot.materialReference || 'MAT-SILVER-925',
      shapeReference: snapshot.shapeReference || 'SHP-CLASSIC-ROUND',
      ringSize: String(snapshot.ringSize || '8'),
      components: snapshot.components || [],
      attachments: snapshot.attachments || [],
      engraving: snapshot.engraving || { enabled: false, text: '', placement: 'INNER_BAND' },
    });
    setIsDirty(false);
  }, []);

  return {
    draft,
    isDirty,
    previewModel,
    clientValidation,
    updateMaterial,
    updateShape,
    updateRingSize,
    toggleComponent,
    updateComponentQuantity,
    toggleAttachment,
    updateEngraving,
    resetDraftFromModel,
    resetFromSavedDesign,
    getPayloadForSave: () => normalizeDraftForSave(draft),
  };
}

