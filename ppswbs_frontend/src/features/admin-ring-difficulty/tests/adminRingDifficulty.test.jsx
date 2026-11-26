import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { signInAs } from '../../loyalty-promotion/tests/fixtures';
import { adminRingDifficultyRoutes } from '../routes';
import { validatePolicyCandidate, countChangedFields } from '../domain/policyValidation';

const testRoutes = [
  ...adminRingDifficultyRoutes,
  { path: '/404', element: <div data-testid="not-found-page">404</div> },
  { path: '/login', element: <div data-testid="login-page">Login</div> },
];

const open = (path, options) => renderRoutes(testRoutes, { path, ...options });

const activePolicyMock = {
  rulesVersion: 'POLICY-2026-11-26-001',
  status: 'EFFECTIVE',
  publishedAt: '2026-11-26T09:00:00Z',
  publishedBy: 'ADM-001',
  optionScores: [
    { kind: 'MATERIAL', reference: 'MAT-SILVER-925', score: '1.0000' },
    { kind: 'SHAPE', reference: 'SHP-CLASSIC', score: '0.5000' },
    { kind: 'GEMSTONE', reference: 'GEM-CZ-WHITE-2MM', score: '0.4000' },
    { kind: 'ATTACHMENT', reference: 'ATT-MILGRAIN', score: '0.3000' },
  ],
  engravingBonus: '0.5000',
  customImageBonusPerImage: '0.3000',
  thresholds: {
    easyMax: '3.0000',
    mediumMax: '6.0000',
    hardMax: '9.0000',
    veryHardMax: '11.0000',
    waxAbove: '9.0000',
    silverClayGemstonesAbove: 5,
  },
};

const historicalPolicyMock = {
  ...activePolicyMock,
  rulesVersion: 'POLICY-2026-11-20-000',
  status: 'HISTORICAL',
  publishedAt: '2026-11-20T09:00:00Z',
  engravingBonus: '0.4000',
};

const eligiblePreviewMock = {
  activeRulesVersion: 'POLICY-2026-11-26-001',
  catalogueRevision: 'CAT-REV-10',
  publicationEligible: true,
  blockingModelCount: 0,
  previewReference: '11111111-1111-4111-8111-111111111111',
  previewExpiresAt: '2099-01-01T00:15:00Z',
  comparisons: [
    {
      source: 'STANDARD',
      before: { sampleName: 'Basic Silver', difficultyScore: '1.5000', difficultyLevel: 'EASY', recommendationCode: 'SIGNATURE_SILVER' },
      after: { sampleName: 'Basic Silver', difficultyScore: '2.0000', difficultyLevel: 'EASY', recommendationCode: 'SIGNATURE_SILVER' },
    },
  ],
  unavailableStandardCases: [],
  modelImpacts: [
    {
      modelReference: 'MOD-001',
      name: 'Classic Silver Band',
      materialReference: 'MAT-SILVER-925',
      before: { sampleName: 'MOD-001', difficultyScore: '1.5000', difficultyLevel: 'EASY', recommendationCode: 'SIGNATURE_SILVER' },
      after: { sampleName: 'MOD-001', difficultyScore: '2.0000', difficultyLevel: 'EASY', recommendationCode: 'SIGNATURE_SILVER' },
      eligible: true,
      blockingReasons: [],
    },
  ],
};

describe('FE-09 Admin Ring Difficulty Configuration', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('masks non-Admin (MANAGER) to /404 without calling Admin policy APIs', async () => {
    const calls = mockFetch({});
    const { router } = open('/admin/ring-difficulty', {
      session: fakeSession('signed-in', { account: signInAs('MANAGER') }),
    });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/404');
    });
    expect(calls).toHaveLength(0);
  });

  it('validates cutoff ordering and decimal format locally', () => {
    const valid = validatePolicyCandidate(activePolicyMock);
    expect(valid.valid).toBe(true);

    const badCutoff = validatePolicyCandidate({
      ...activePolicyMock,
      thresholds: { ...activePolicyMock.thresholds, waxAbove: '12.0000' },
    });
    expect(badCutoff.valid).toBe(false);
    expect(badCutoff.fieldErrors['thresholds.waxAbove']).toBe('validation.waxBelowVeryHard');

    const badScore = validatePolicyCandidate({
      ...activePolicyMock,
      engravingBonus: '-1',
    });
    expect(badScore.valid).toBe(false);
    expect(badScore.fieldErrors.engravingBonus).toBe('validation.decimalInvalid');
  });

  it('loads effective policy & history, previews, invalidates proof on edit, and publishes with Idempotency-Key', async () => {
    const user = userEvent.setup();
    const publishedVersion = {
      ...activePolicyMock,
      rulesVersion: 'POLICY-2026-11-26-002',
      optionScores: [
        { kind: 'MATERIAL', reference: 'MAT-SILVER-925', score: '1.5000' },
        { kind: 'SHAPE', reference: 'SHP-CLASSIC', score: '0.5000' },
        { kind: 'GEMSTONE', reference: 'GEM-CZ-WHITE-2MM', score: '0.4000' },
        { kind: 'ATTACHMENT', reference: 'ATT-MILGRAIN', score: '0.3000' },
      ],
    };

    const calls = mockFetch({
      'GET /api/v1/admin/workshop-ring-difficulty/active': json(200, activePolicyMock),
      'GET /api/v1/admin/workshop-ring-difficulty/versions': json(200, [activePolicyMock, historicalPolicyMock]),
      'GET /api/v1/workshop-design-options': json(200, { materials: [], shapes: [], components: [], attachments: [] }),
      'POST /api/v1/admin/workshop-ring-difficulty/preview': json(200, eligiblePreviewMock),
      'POST /api/v1/admin/workshop-ring-difficulty/versions': json(201, publishedVersion),
    });

    open('/admin/ring-difficulty', {
      session: fakeSession('signed-in', { account: signInAs('ADMIN') }),
    });

    expect(await screen.findByTestId('ring-difficulty-page')).toBeInTheDocument();
    expect(screen.getByTestId('effective-rules-version')).toHaveTextContent('POLICY-2026-11-26-001');
    expect(screen.getByTestId('open-publish-confirm-btn')).toBeDisabled();

    // Edit score
    const silverInput = screen.getByTestId('score-input-MATERIAL-MAT-SILVER-925');
    await user.clear(silverInput);
    await user.type(silverInput, '1.5000');
    expect(screen.getByTestId('unsaved-badge')).toBeInTheDocument();

    // Run Preview
    await user.click(screen.getByTestId('preview-policy-btn'));
    expect(await screen.findByTestId('preview-results-section')).toBeInTheDocument();
    expect(screen.getByTestId('proof-valid-badge')).toBeInTheDocument();
    expect(screen.getByTestId('open-publish-confirm-btn')).toBeEnabled();

    // Edit after preview invalidates proof immediately
    await user.clear(silverInput);
    await user.type(silverInput, '1.6000');
    expect(screen.getByTestId('open-publish-confirm-btn')).toBeDisabled();

    // Re-edit back and preview again
    await user.clear(silverInput);
    await user.type(silverInput, '1.5000');
    await user.click(screen.getByTestId('preview-policy-btn'));
    expect(await screen.findByTestId('proof-valid-badge')).toBeInTheDocument();

    // Confirm and Publish
    await user.click(screen.getByTestId('open-publish-confirm-btn'));
    expect(await screen.findByTestId('publish-confirm-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('confirm-publish-btn'));

    expect(await screen.findByTestId('publish-success-banner')).toBeInTheDocument();
    expect(screen.getByTestId('effective-rules-version')).toHaveTextContent('POLICY-2026-11-26-002');

    // Verify Idempotency-Key header was sent on publish
    const publishCall = calls.find((call) =>
      call.path === '/api/v1/admin/workshop-ring-difficulty/versions' && call.method === 'POST'
    );
    expect(publishCall).toBeTruthy();
    expect(publishCall.headers['idempotency-key'] || publishCall.headers['Idempotency-Key']).toBeTruthy();
  });

  it('keeps publish disabled when preview reports blocking models', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/admin/workshop-ring-difficulty/active': json(200, activePolicyMock),
      'GET /api/v1/admin/workshop-ring-difficulty/versions': json(200, [activePolicyMock]),
      'GET /api/v1/workshop-design-options': json(200, { materials: [], shapes: [], components: [], attachments: [] }),
      'POST /api/v1/admin/workshop-ring-difficulty/preview': json(200, {
        ...eligiblePreviewMock,
        publicationEligible: false,
        blockingModelCount: 1,
        previewReference: null,
        previewExpiresAt: null,
        modelImpacts: [
          {
            modelReference: 'MOD-002',
            name: 'Complex Halo',
            materialReference: 'MAT-SILVER-925',
            before: { sampleName: 'MOD-002', difficultyScore: '8.0000', difficultyLevel: 'HARD', recommendationCode: 'SIGNATURE_SILVER' },
            after: { sampleName: 'MOD-002', difficultyScore: '12.5000', difficultyLevel: 'TOO_RISKY', recommendationCode: 'TOO_RISKY' },
            eligible: false,
            blockingReasons: ['MODEL_TOO_RISKY'],
          },
        ],
      }),
    });

    open('/admin/ring-difficulty', {
      session: fakeSession('signed-in', { account: signInAs('ADMIN') }),
    });

    expect(await screen.findByTestId('ring-difficulty-page')).toBeInTheDocument();
    await user.click(screen.getByTestId('preview-policy-btn'));

    expect(await screen.findByTestId('proof-blocked-badge')).toBeInTheDocument();
    expect(screen.getByTestId('model-impact-MOD-002')).toBeInTheDocument();
    expect(screen.getByTestId('open-publish-confirm-btn')).toBeDisabled();
  });

  it('preserves local candidate on 409 conflict and offers reload', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/admin/workshop-ring-difficulty/active': json(200, activePolicyMock),
      'GET /api/v1/admin/workshop-ring-difficulty/versions': json(200, [activePolicyMock]),
      'GET /api/v1/workshop-design-options': json(200, { materials: [], shapes: [], components: [], attachments: [] }),
      'POST /api/v1/admin/workshop-ring-difficulty/preview': json(409, { code: 'CONFLICT', message: 'Stale policy version' }),
    });

    open('/admin/ring-difficulty', {
      session: fakeSession('signed-in', { account: signInAs('ADMIN') }),
    });

    expect(await screen.findByTestId('ring-difficulty-page')).toBeInTheDocument();
    const silverInput = screen.getByTestId('score-input-MATERIAL-MAT-SILVER-925');
    await user.clear(silverInput);
    await user.type(silverInput, '2.2500');

    await user.click(screen.getByTestId('preview-policy-btn'));
    expect(await screen.findByTestId('policy-action-error')).toBeInTheDocument();
    expect(screen.getByTestId('reload-latest-btn')).toBeInTheDocument();
    expect(screen.getByTestId('score-input-MATERIAL-MAT-SILVER-925')).toHaveValue('2.2500');
  });

  it('counts changed fields accurately', () => {
    const changed = countChangedFields(activePolicyMock, {
      ...activePolicyMock,
      engravingBonus: '0.7500',
    });
    expect(changed).toBe(1);
  });
});
