import React from 'react';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from '../../shared/auth/AuthProvider';
import { I18nProvider, useLanguage } from '../../shared/i18n/I18nProvider';
import { fakeSession, json, mockFetch } from '../../test/render';
import OtaChannelConfigurationPage from '../pages/OtaChannelConfigurationPage';
import { otaMessages } from '../messages';

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <button type="button" onClick={() => setLanguage('en')}>to-en</button>;
}

describe('OTA connectivity i18n', () => {
  it('keeps vi and en dictionaries on the same key set', () => {
    expect(Object.keys(otaMessages.en).sort()).toEqual(Object.keys(otaMessages.vi).sort());
  });

  it('switches the channel page between vi and en while provider names and references stay as received', async () => {
    window.localStorage.clear();
    mockFetch({
      'GET /api/v1/ota-channels': json(200, {
        items: [{
          mappingId: 'f0000000-0000-0000-0000-000000000001', channelId: 'c1', provider: 'KLOOK', state: 'SALES_CLOSED',
          commercialStatus: 'APPROVED', bookingEnabled: false, safeReadinessStatus: 'INCOMPLETE', version: 1,
          salesControlStatus: 'NONE', providerProductReference: 'KLK-RING-01',
        }],
      }),
    });
    render(
      <I18nProvider initialLanguage="vi">
        <LanguageToggle />
        <AuthProvider source={fakeSession()}>
          <MemoryRouter>
            <OtaChannelConfigurationPage />
          </MemoryRouter>
        </AuthProvider>
      </I18nProvider>
    );

    expect(await screen.findByText('KLOOK')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sửa ánh xạ' })).toBeInTheDocument();
    expect(screen.getByText('Đã đóng bán (SALES_CLOSED)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));

    expect(screen.getByRole('button', { name: 'Edit Mapping' })).toBeInTheDocument();
    expect(screen.getByText('SALES_CLOSED')).toBeInTheDocument();
    expect(screen.getByText('KLOOK')).toBeInTheDocument();
    expect(screen.getByText('KLK-RING-01')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sửa ánh xạ' })).not.toBeInTheDocument();
  });
});
