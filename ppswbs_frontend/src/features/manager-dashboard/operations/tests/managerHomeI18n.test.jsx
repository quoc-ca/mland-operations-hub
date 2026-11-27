import React from 'react';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { I18nProvider, useLanguage } from '../../../../shared/i18n/I18nProvider';
import { managerHomeMessages } from '../ui/messages';
import RecentEvents from '../ui/RecentEvents';

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <>
    <button type="button" onClick={() => setLanguage('en')}>to-en</button>
    <button type="button" onClick={() => setLanguage('vi')}>to-vi</button>
  </>;
}

describe('Manager home i18n', () => {
  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    const { vi, en } = managerHomeMessages;
    expect(Object.keys(en).sort()).toEqual(Object.keys(vi).sort());
    const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
    Object.keys(vi).forEach(key => expect(placeholders(en[key]), key).toEqual(placeholders(vi[key])));
  });

  it('switches the recent events block between vi and en and shows an unknown kind as it came', () => {
    window.localStorage.clear();
    const events = { state: 'ready', value: { items: [
      { id: 'a', kind: 'REFUSED_ATTEMPT', time: '2026-11-18T03:00:00Z', actor: null, action: 'X' },
      { id: 'b', kind: 'NEW_KIND', time: '2026-11-18T02:00:00Z', actor: 'Staff A', action: 'Y' },
    ], partial: false, settlementIncluded: true } };
    render(<MemoryRouter><I18nProvider initialLanguage="vi"><LanguageToggle /><RecentEvents events={events} onRetry={() => {}} /></I18nProvider></MemoryRouter>);
    expect(screen.getByText('Lượt truy cập bị từ chối')).toBeInTheDocument();
    expect(screen.getByText('Hệ thống')).toBeInTheDocument();
    expect(screen.getByText('NEW_KIND')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getByText('Refused access attempt')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the audit log' })).toBeInTheDocument();
    expect(screen.queryByText('Lượt truy cập bị từ chối')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-vi' }));
    expect(screen.getByRole('link', { name: 'Mở lịch sử thao tác' })).toBeInTheDocument();
  });
});
