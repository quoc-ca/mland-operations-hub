import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { translate, useLanguage } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import DesignEvaluationPage from '../ui/DesignEvaluationPage';
import { getErrorMessage } from '../domain/designErrors';
import { validateDraftClient } from '../domain/designDraft';
import { derivePreviewModel, getDifficultyMeta } from '../domain/designPreview';

const tFor = (language) => (key, params) => translate(messages, language, key, params);

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return (
    <>
      <button type="button" onClick={() => setLanguage('en')}>to-en</button>
      <button type="button" onClick={() => setLanguage('vi')}>to-vi</button>
    </>
  );
}

describe('Ring design i18n', () => {
  afterEach(() => window.localStorage.clear());

  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    expect(Object.keys(messages.en).sort()).toEqual(Object.keys(messages.vi).sort());
    const placeholders = (text) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();
    Object.keys(messages.vi).forEach((key) => {
      expect(placeholders(messages.en[key]), key).toEqual(placeholders(messages.vi[key]));
    });
  });

  it('translates domain helpers (errors, validation, difficulty, preview labels) by language', () => {
    const en = tFor('en');
    expect(getErrorMessage({ code: 'DESIGN_LOCKED' }, en)).toBe(messages.en['error.DESIGN_LOCKED']);
    expect(getErrorMessage({ message: 'Server says no' }, en)).toBe('Server says no');
    expect(getErrorMessage(null, en)).toBe('An unknown error occurred.');
    expect(validateDraftClient({ packageReference: '' }, en).errors.packageReference).toBe('Please select a workshop package.');
    expect(validateDraftClient({ packageReference: '' }).errors.packageReference).toBe('Vui lòng chọn gói workshop.');
    expect(getDifficultyMeta(9.5, en)).toMatchObject({ label: 'Very hard', isVeryHard: true });
    expect(getDifficultyMeta(2, en).label).toBe('Easy');
    expect(derivePreviewModel({ materialReference: 'MAT-COPPER', shapeReference: 'SHP-WAVE' }, en)).toMatchObject({
      material: { label: 'Copper' },
      shape: { label: 'Wave' },
    });
  });

  it('switches the evaluation page between vi and en without touching ids or API values', async () => {
    window.localStorage.clear();
    mockFetch({
      'GET /api/v1/workshop-designs/DSG-I18N-1': json(200, {
        designId: 'DSG-I18N-1',
        packageReference: 'PKG-COUPLE-01',
        designVersion: 1,
        status: 'VALIDATED',
        evaluation: { status: 'VALIDATED', difficultyScore: 3.5, estimatedPrice: 350000, rulesVersion: 'v1.0' },
        componentSnapshot: {
          packageReference: 'PKG-COUPLE-01',
          materialReference: 'MAT-SILVER-925',
          shapeReference: 'SHP-CLASSIC-ROUND',
          ringSize: '12',
          components: [],
          attachments: [],
          engraving: { enabled: false },
        },
      }),
    });
    const routes = [{
      path: '/workshops/:packageReference/design/:designId',
      element: <><LanguageToggle /><DesignEvaluationPage /></>,
    }];
    renderRoutes(routes, { path: '/workshops/PKG-COUPLE-01/design/DSG-I18N-1', session: fakeSession('signed-out') });

    expect(await screen.findByTestId('design-evaluation-page')).toBeInTheDocument();
    expect(screen.getByText(/Mã thiết kế:/)).toBeInTheDocument();
    expect(screen.getByTestId('difficulty-badge')).toHaveTextContent('Trung bình');
    expect(screen.getByText('350.000 ₫')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getByText(/Design ID:/)).toBeInTheDocument();
    expect(screen.getByTestId('difficulty-badge')).toHaveTextContent('Medium');
    expect(screen.getByText('350,000 ₫')).toBeInTheDocument();
    expect(screen.getByText('DSG-I18N-1')).toBeInTheDocument();
    expect(screen.queryByText(/Mã thiết kế:/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-vi' }));
    expect(screen.getByText(/Mã thiết kế:/)).toBeInTheDocument();
    expect(screen.getByTestId('difficulty-badge')).toHaveTextContent('Trung bình');
  });
});
