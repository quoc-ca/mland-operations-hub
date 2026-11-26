export const DECIMAL_SCORE_PATTERN = /^(0|[1-9][0-9]{0,5})(\.[0-9]{1,4})?$/;
export const OPTION_KINDS = ['MATERIAL', 'SHAPE', 'GEMSTONE', 'ATTACHMENT'];

/**
 * Normalizes a policy version or candidate into a clean PolicyValues payload
 * matching the OpenAPI schema (no extra metadata fields).
 */
export function toPolicyValues(source) {
  if (!source) {
    return {
      optionScores: [],
      engravingBonus: '0',
      customImageBonusPerImage: '0',
      thresholds: {
        easyMax: '3',
        mediumMax: '6',
        hardMax: '9',
        veryHardMax: '11',
        waxAbove: '9',
        silverClayGemstonesAbove: 5,
      },
    };
  }
  return {
    optionScores: (source.optionScores || []).map((item) => ({
      kind: item.kind,
      reference: item.reference,
      score: String(item.score ?? '').trim(),
    })),
    engravingBonus: String(source.engravingBonus ?? '').trim(),
    customImageBonusPerImage: String(source.customImageBonusPerImage ?? '').trim(),
    thresholds: {
      easyMax: String(source.thresholds?.easyMax ?? '').trim(),
      mediumMax: String(source.thresholds?.mediumMax ?? '').trim(),
      hardMax: String(source.thresholds?.hardMax ?? '').trim(),
      veryHardMax: String(source.thresholds?.veryHardMax ?? '').trim(),
      waxAbove: String(source.thresholds?.waxAbove ?? '').trim(),
      silverClayGemstonesAbove: Number(source.thresholds?.silverClayGemstonesAbove ?? 0),
    },
  };
}

/**
 * Deterministic fingerprint for candidate + active version + optional samples,
 * used to invalidate preview proofs immediately upon any edit.
 */
export function buildPreviewSignature(candidate, expectedActiveRulesVersion, samples = []) {
  const normalized = toPolicyValues(candidate);
  const sortedScores = [...normalized.optionScores].sort((a, b) =>
    `${a.kind}:${a.reference}`.localeCompare(`${b.kind}:${b.reference}`)
  );
  return JSON.stringify({
    expectedActiveRulesVersion: expectedActiveRulesVersion || '',
    candidate: { ...normalized, optionScores: sortedScores },
    samples: (samples || []).map((s) => ({
      sampleName: (s.sampleName || '').trim(),
      materialReference: s.materialReference || '',
      shapeReference: s.shapeReference || '',
      gemstones: s.gemstones || [],
      attachments: s.attachments || [],
      engravingEnabled: Boolean(s.engravingEnabled),
      customImageCount: Number(s.customImageCount || 0),
    })),
  });
}

export function isValidDecimalScore(value) {
  const str = String(value ?? '').trim();
  return DECIMAL_SCORE_PATTERN.test(str);
}

/**
 * Validates the complete candidate locally before preview/publish.
 * Returns { valid, fieldErrors } where fieldErrors maps field keys to i18n error codes.
 */
export function validatePolicyCandidate(candidate) {
  const fieldErrors = {};
  if (!candidate) {
    return { valid: false, fieldErrors: { _form: 'validation.policyMissing' } };
  }

  const seen = new Set();
  for (const item of candidate.optionScores || []) {
    const key = `${item.kind}:${item.reference}`;
    if (seen.has(key)) {
      fieldErrors[`option.${key}`] = 'validation.duplicateOption';
    }
    seen.add(key);

    if (!String(item.score ?? '').trim()) {
      fieldErrors[`option.${key}`] = 'validation.scoreRequired';
    } else if (!isValidDecimalScore(item.score)) {
      fieldErrors[`option.${key}`] = 'validation.decimalInvalid';
    }
  }

  if (!isValidDecimalScore(candidate.engravingBonus)) {
    fieldErrors.engravingBonus = 'validation.decimalInvalid';
  }
  if (!isValidDecimalScore(candidate.customImageBonusPerImage)) {
    fieldErrors.customImageBonusPerImage = 'validation.decimalInvalid';
  }

  const th = candidate.thresholds || {};
  const decimalThresholdKeys = ['easyMax', 'mediumMax', 'hardMax', 'veryHardMax', 'waxAbove'];
  for (const k of decimalThresholdKeys) {
    if (!isValidDecimalScore(th[k])) {
      fieldErrors[`thresholds.${k}`] = 'validation.decimalInvalid';
    }
  }

  const gemCutoff = th.silverClayGemstonesAbove;
  if (!Number.isInteger(Number(gemCutoff)) || Number(gemCutoff) < 0 || Number(gemCutoff) > 9 || String(gemCutoff).trim() === '') {
    fieldErrors['thresholds.silverClayGemstonesAbove'] = 'validation.gemstoneCutoffInvalid';
  }

  if (
    !fieldErrors['thresholds.easyMax'] &&
    !fieldErrors['thresholds.mediumMax'] &&
    !fieldErrors['thresholds.hardMax'] &&
    !fieldErrors['thresholds.veryHardMax']
  ) {
    const e = Number(th.easyMax);
    const m = Number(th.mediumMax);
    const h = Number(th.hardMax);
    const v = Number(th.veryHardMax);
    if (!(e < m)) fieldErrors['thresholds.mediumMax'] = 'validation.cutoffOrder';
    if (!(m < h)) fieldErrors['thresholds.hardMax'] = 'validation.cutoffOrder';
    if (!(h < v)) fieldErrors['thresholds.veryHardMax'] = 'validation.cutoffOrder';
  }

  if (!fieldErrors['thresholds.waxAbove'] && !fieldErrors['thresholds.veryHardMax']) {
    if (!(Number(th.waxAbove) < Number(th.veryHardMax))) {
      fieldErrors['thresholds.waxAbove'] = 'validation.waxBelowVeryHard';
    }
  }

  return {
    valid: Object.keys(fieldErrors).length === 0,
    fieldErrors,
  };
}

/**
 * Counts how many fields differ between the active effective policy and the local candidate.
 */
export function countChangedFields(activePolicy, candidate) {
  if (!activePolicy || !candidate) return 0;
  let count = 0;

  const activeMap = new Map(
    (activePolicy.optionScores || []).map((o) => [`${o.kind}:${o.reference}`, String(o.score)])
  );
  for (const item of candidate.optionScores || []) {
    const prev = activeMap.get(`${item.kind}:${item.reference}`);
    if (prev === undefined || Number(prev) !== Number(item.score) || String(prev) !== String(item.score).trim()) {
      count += 1;
    }
  }

  if (String(activePolicy.engravingBonus) !== String(candidate.engravingBonus).trim()) count += 1;
  if (String(activePolicy.customImageBonusPerImage) !== String(candidate.customImageBonusPerImage).trim()) count += 1;

  const aTh = activePolicy.thresholds || {};
  const cTh = candidate.thresholds || {};
  for (const k of ['easyMax', 'mediumMax', 'hardMax', 'veryHardMax', 'waxAbove']) {
    if (String(aTh[k]) !== String(cTh[k]).trim()) count += 1;
  }
  if (Number(aTh.silverClayGemstonesAbove) !== Number(cTh.silverClayGemstonesAbove)) count += 1;

  return count;
}

export function isPreviewProofValid(previewState, currentSignature, nowMs = Date.now()) {
  if (!previewState?.result) return false;
  const { result, signature } = previewState;
  if (!result.publicationEligible || !result.previewReference) return false;
  if (signature !== currentSignature) return false;
  if (result.previewExpiresAt) {
    const exp = Date.parse(result.previewExpiresAt);
    if (!Number.isNaN(exp) && exp <= nowMs) return false;
  }
  return true;
}
