import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { newCommandId } from '../../../shared/api/http';
import { useRingDifficultyAdminApi } from '../api/ringDifficultyAdminApi';
import {
  buildPreviewSignature,
  countChangedFields,
  isPreviewProofValid,
  toPolicyValues,
  validatePolicyCandidate,
} from '../domain/policyValidation';

/**
 * Manages active policy, immutable history, local candidate edits,
 * 15-minute preview proof invalidation, and idempotent atomic publication.
 */
export function useRingDifficultyPolicy() {
  const api = useRingDifficultyAdminApi();

  const [activePolicy, setActivePolicy] = useState(null);
  const [versions, setVersions] = useState([]);
  const [catalogueOptions, setCatalogueOptions] = useState(null);
  const [candidate, setCandidate] = useState(null);
  const [samples, setSamples] = useState([]);
  const [previewState, setPreviewState] = useState(null); // { result, signature }

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [publishSuccess, setPublishSuccess] = useState(null);

  const commandKeyRef = useRef({ key: newCommandId(), signature: null });

  const load = useCallback(async ({ preserveCandidate = false } = {}) => {
    setLoading(true);
    setLoadError(null);
    try {
      const [active, history, options] = await Promise.all([
        api.getActive(),
        api.listVersions(),
        api.getCatalogueOptions().catch(() => null),
      ]);
      setActivePolicy(active);
      setVersions(Array.isArray(history) ? history : []);
      if (options) setCatalogueOptions(options);
      if (!preserveCandidate) {
        setCandidate(toPolicyValues(active));
        setPreviewState(null);
        setPublishSuccess(null);
      } else {
        setPreviewState(null);
      }
      setActionError(null);
    } catch (err) {
      setLoadError(err);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    load();
  }, [load]);

  const currentSignature = useMemo(
    () => buildPreviewSignature(candidate, activePolicy?.rulesVersion, samples),
    [candidate, activePolicy?.rulesVersion, samples]
  );

  // Invalidate preview proof immediately whenever candidate, version, or samples diverge
  useEffect(() => {
    if (previewState && previewState.signature !== currentSignature) {
      setPreviewState(null);
    }
  }, [currentSignature, previewState]);

  const localValidation = useMemo(() => validatePolicyCandidate(candidate), [candidate]);
  const changedFieldCount = useMemo(
    () => countChangedFields(activePolicy, candidate),
    [activePolicy, candidate]
  );

  const proofValid = isPreviewProofValid(previewState, currentSignature);

  const updateOptionScore = useCallback((kind, reference, score) => {
    setActionError(null);
    setPublishSuccess(null);
    setCandidate((prev) => {
      if (!prev) return prev;
      const exists = prev.optionScores.some(
        (item) => item.kind === kind && item.reference === reference
      );
      const nextScores = exists
        ? prev.optionScores.map((item) =>
            item.kind === kind && item.reference === reference ? { ...item, score } : item
          )
        : [...prev.optionScores, { kind, reference, score }];
      return { ...prev, optionScores: nextScores };
    });
  }, []);

  const updateCoefficient = useCallback((field, value) => {
    setActionError(null);
    setPublishSuccess(null);
    setCandidate((prev) => (prev ? { ...prev, [field]: value } : prev));
  }, []);

  const updateThreshold = useCallback((field, value) => {
    setActionError(null);
    setPublishSuccess(null);
    setCandidate((prev) =>
      prev
        ? {
            ...prev,
            thresholds: {
              ...prev.thresholds,
              [field]: field === 'silverClayGemstonesAbove' ? value : value,
            },
          }
        : prev
    );
  }, []);

  const addSample = useCallback((sample) => {
    setActionError(null);
    setSamples((prev) => (prev.length >= 20 ? prev : [...prev, sample]));
  }, []);

  const removeSample = useCallback((index) => {
    setActionError(null);
    setSamples((prev) => prev.filter((_, idx) => idx !== index));
  }, []);

  const resetCandidate = useCallback(() => {
    if (!activePolicy) return;
    setCandidate(toPolicyValues(activePolicy));
    setPreviewState(null);
    setActionError(null);
    setPublishSuccess(null);
  }, [activePolicy]);

  const restoreVersion = useCallback((versionRow) => {
    if (!versionRow) return;
    setCandidate(toPolicyValues(versionRow));
    setPreviewState(null);
    setActionError(null);
    setPublishSuccess(null);
  }, []);

  const runPreview = useCallback(async () => {
    if (!activePolicy || !candidate) return null;
    setActionError(null);
    setPublishSuccess(null);
    const check = validatePolicyCandidate(candidate);
    if (!check.valid) return null;

    setPreviewing(true);
    try {
      const payload = {
        expectedActiveRulesVersion: activePolicy.rulesVersion,
        candidate: toPolicyValues(candidate),
        ...(samples.length > 0 ? { samples } : {}),
      };
      const result = await api.preview(payload);
      setPreviewState({ result, signature: currentSignature });
      return result;
    } catch (err) {
      setPreviewState(null);
      setActionError(err);
      return null;
    } finally {
      setPreviewing(false);
    }
  }, [activePolicy, api, candidate, currentSignature, samples]);

  const runPublish = useCallback(async () => {
    if (!activePolicy || !candidate || !proofValid) return null;
    setActionError(null);

    const publishSignature = `${currentSignature}:${previewState.result.previewReference}`;
    if (commandKeyRef.current.signature !== publishSignature) {
      commandKeyRef.current = { key: newCommandId(), signature: publishSignature };
    }

    setPublishing(true);
    try {
      const payload = {
        expectedActiveRulesVersion: activePolicy.rulesVersion,
        candidate: toPolicyValues(candidate),
        previewReference: previewState.result.previewReference,
      };
      const published = await api.publish(payload, commandKeyRef.current.key);
      setActivePolicy(published);
      setCandidate(toPolicyValues(published));
      setPreviewState(null);
      setPublishSuccess(published);
      commandKeyRef.current = { key: newCommandId(), signature: null };
      // Refresh immutable history
      const history = await api.listVersions().catch(() => null);
      if (Array.isArray(history)) setVersions(history);
      return published;
    } catch (err) {
      setActionError(err);
      // On 409/422 proof invalidation, clear stale proof so Admin previews again after reload/fix
      if (err?.status === 409 || err?.code === 'PREVIEW_EXPIRED' || err?.code === 'PREVIEW_MISMATCH') {
        setPreviewState(null);
      }
      return null;
    } finally {
      setPublishing(false);
    }
  }, [activePolicy, api, candidate, currentSignature, previewState, proofValid]);

  return {
    activePolicy,
    versions,
    catalogueOptions,
    candidate,
    samples,
    previewResult: previewState?.result || null,
    proofValid,
    changedFieldCount,
    localValidation,
    loading,
    loadError,
    previewing,
    publishing,
    actionError,
    publishSuccess,
    reload: load,
    updateOptionScore,
    updateCoefficient,
    updateThreshold,
    addSample,
    removeSample,
    resetCandidate,
    restoreVersion,
    runPreview,
    runPublish,
  };
}
