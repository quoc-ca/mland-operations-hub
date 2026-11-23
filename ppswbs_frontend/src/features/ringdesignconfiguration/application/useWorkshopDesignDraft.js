import { useState, useMemo, useCallback } from 'react';
import { translate } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { createDefaultDraft, normalizeDraftForSave, validateDraftClient } from '../domain/designDraft';
import { derivePreviewModel, findNextAvailableAngle } from '../domain/designPreview';

export function useWorkshopDesignDraft(packageReference, initialModel = null, t = (key, params) => translate(messages, 'vi', key, params)) {
  const [draft, setDraft] = useState(() => createDefaultDraft(packageReference, initialModel));
  const [isDirty, setIsDirty] = useState(false);

  const previewModel = useMemo(() => derivePreviewModel(draft, t), [draft, t]);
  const clientValidation = useMemo(() => validateDraftClient(draft, t), [draft, t]);

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

  const updateThickness = useCallback((thickness) => {
    const clamped = Math.min(3.0, Math.max(1.0, Math.round(Number(thickness) * 10) / 10));
    setDraft((prev) => ({ ...prev, thicknessMm: clamped }));
    setIsDirty(true);
  }, []);

  const updateBandWidth = useCallback((bandWidth) => {
    const clamped = Math.min(6.0, Math.max(1.5, Math.round(Number(bandWidth) * 10) / 10));
    setDraft((prev) => ({ ...prev, bandWidthMm: clamped }));
    setIsDirty(true);
  }, []);

  const setComponentQuantity = useCallback((componentReference, rawQuantity, maxQuantity = 10) => {
    setDraft((prev) => {
      const existing = prev.components.find((c) => c.reference === componentReference);
      const otherTotal = prev.components
        .filter((c) => c.reference !== componentReference)
        .reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);

      const requestedQty = Math.max(0, Number(rawQuantity) || 0);
      if (requestedQty > (existing?.quantity || 0) && otherTotal + requestedQty > 10) {
        // Exceeds aggregate limit of 10: do not change draft on 11th attempt
        return prev;
      }

      const allowedForThis = Math.min(Number(maxQuantity) || 10, 10 - otherTotal);
      if (allowedForThis < 0) return prev;
      const qty = Math.min(allowedForThis, requestedQty);

      let nextComponents;
      if (qty <= 0) {
        nextComponents = prev.components.filter((c) => c.reference !== componentReference);
      } else if (existing) {
        nextComponents = prev.components.map((c) =>
          c.reference === componentReference ? { ...c, quantity: qty } : c
        );
      } else {
        nextComponents = [...prev.components, { reference: componentReference, quantity: qty, placement: 'CENTER' }];
      }

      // Synchronize gemstone positions
      let nextPositions = [...(prev.gemstonePositions || [])];
      if (qty <= 0) {
        nextPositions = nextPositions.filter((p) => p.reference !== componentReference);
      } else {
        const matching = nextPositions.filter((p) => p.reference === componentReference);
        if (matching.length > qty) {
          // Remove excess
          let toRemove = matching.length - qty;
          nextPositions = nextPositions.filter((p) => {
            if (p.reference === componentReference && toRemove > 0) {
              toRemove--;
              return false;
            }
            return true;
          });
        } else if (matching.length < qty) {
          // Add new stone positions around ring arc
          const needed = qty - matching.length;
          for (let i = 0; i < needed; i++) {
            const stoneIndex = matching.length + i;
            const stoneId = `gem-${componentReference}-${stoneIndex}`;
            const newAngle = findNextAvailableAngle(nextPositions);
            nextPositions.push({
              id: stoneId,
              reference: componentReference,
              index: stoneIndex,
              angleDeg: newAngle,
            });
          }
        }
      }

      return {
        ...prev,
        components: nextComponents,
        gemstonePositions: nextPositions,
      };
    });
    setIsDirty(true);
  }, []);

  const toggleComponent = useCallback((componentReference, maxQuantity = 10) => {
    setDraft((prev) => {
      const existing = prev.components.find((c) => c.reference === componentReference);
      if (existing) {
        return {
          ...prev,
          components: prev.components.filter((c) => c.reference !== componentReference),
          gemstonePositions: (prev.gemstonePositions || []).filter((p) => p.reference !== componentReference),
        };
      }
      const currentTotal = prev.components.reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
      if (currentTotal >= 10) {
        return prev; // Reached aggregate limit of 10
      }
      const stoneId = `gem-${componentReference}-0`;
      const currentPositions = prev.gemstonePositions || [];
      const newAngle = findNextAvailableAngle(currentPositions);
      return {
        ...prev,
        components: [...prev.components, { reference: componentReference, quantity: 1, placement: 'CENTER' }],
        gemstonePositions: [
          ...currentPositions,
          { id: stoneId, reference: componentReference, index: 0, angleDeg: newAngle },
        ],
      };
    });
    setIsDirty(true);
  }, []);

  const updateComponentQuantity = useCallback((componentReference, quantity, maxQuantity = 10) => {
    setComponentQuantity(componentReference, quantity, maxQuantity);
  }, [setComponentQuantity]);

  const moveGemstone = useCallback((stoneId, newAngleDeg) => {
    setDraft((prev) => {
      const positions = prev.gemstonePositions ? [...prev.gemstonePositions] : [];
      const normalizedAngle = (Math.round(newAngleDeg * 10) / 10 + 360) % 360;
      const existingIndex = positions.findIndex((p) => p.id === stoneId);

      if (existingIndex >= 0) {
        positions[existingIndex] = { ...positions[existingIndex], angleDeg: normalizedAngle };
      } else {
        // If not yet in positions list, parse reference from id: gem-[ref]-[index]
        const match = stoneId.match(/^gem-(.+)-(\d+)$/);
        const ref = match ? match[1] : '';
        const idx = match ? parseInt(match[2], 10) : 0;
        positions.push({
          id: stoneId,
          reference: ref,
          index: idx,
          angleDeg: normalizedAngle,
        });
      }

      return {
        ...prev,
        gemstonePositions: positions,
      };
    });
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
    const restoredImages = (snapshot.engraving?.customImages || []).map((img, idx) => ({
      id: img.id || `img-${idx + 1}`,
      placement: img.placement || 'INNER_BAND',
      imageUrl: img.imageUrl || img.imageReference,
      imageReference: img.imageReference || img.imageUrl,
      processedPreviewReference: img.processedPreviewReference || img.previewDataUrl || img.previewUrl || img.imageUrl,
      previewDataUrl: img.previewDataUrl || img.processedPreviewReference || img.previewUrl || img.imageUrl,
      cropX: Number(img.cropX) || 0,
      cropY: Number(img.cropY) || 0,
      cropWidth: Number(img.cropWidth) || 160,
      cropHeight: Number(img.cropHeight) || 160,
      contrast: img.contrast !== undefined ? Number(img.contrast) : 50,
      sharpness: img.sharpness !== undefined ? Number(img.sharpness) : 50,
      price: Number(img.price) || 100000,
    }));

    setDraft({
      packageReference: designResponse.packageReference,
      sourceModelReference: snapshot.sourceModelReference || designResponse.sourceModelReference || null,
      materialReference: snapshot.materialReference || 'MAT-SILVER-925',
      shapeReference: snapshot.shapeReference || 'SHP-CLASSIC-ROUND',
      ringSize: String(snapshot.ringSize || '8'),
      thicknessMm: Number(snapshot.thicknessMm) || 1.8,
      bandWidthMm: Number(snapshot.bandWidthMm) || 3.5,
      components: snapshot.components || [],
      attachments: snapshot.attachments || [],
      gemstonePositions: snapshot.gemstonePositions || [],
      engraving: {
        enabled: Boolean(snapshot.engraving?.enabled),
        text: snapshot.engraving?.text || '',
        placement: snapshot.engraving?.placement || 'INNER_BAND',
        items: snapshot.engraving?.items || [],
        customImages: restoredImages,
      },
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
    updateThickness,
    updateBandWidth,
    toggleComponent,
    setComponentQuantity,
    updateComponentQuantity,
    moveGemstone,
    toggleAttachment,
    updateEngraving,
    resetDraftFromModel,
    resetFromSavedDesign,
    getPayloadForSave: () => normalizeDraftForSave(draft),
  };
}

