import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { translate } from '../../shared/i18n/I18nProvider';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operatorHomeMessages } from './messages';
import { operatorSections } from './operatorSections';
import { operatorHomeRoutes } from './routes';
import { workshopTasks } from './staffWork';

const tIn = language => (key, params) => translate(operatorHomeMessages, language, key, params);

describe('Staff home i18n', () => {
  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    const { vi, en } = operatorHomeMessages;
    expect(Object.keys(en).sort()).toEqual(Object.keys(vi).sort());
    const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
    Object.keys(vi).forEach(key => expect(placeholders(en[key]), key).toEqual(placeholders(vi[key])));
  });

  it('has a message for every key the menu data points to', () => {
    operatorSections().forEach(section => {
      expect(operatorHomeMessages.vi[section.titleKey]).toBeTruthy();
      section.items.forEach(item => {
        expect(operatorHomeMessages.vi[item.titleKey]).toBeTruthy();
        expect(operatorHomeMessages.en[item.descKey]).toBeTruthy();
      });
    });
  });

  it('writes the session tasks in the chosen language and keeps the same tasks in both', () => {
    const now = Date.parse('2026-11-18T03:00:00Z');
    const iso = minutes => new Date(now + minutes * 60_000).toISOString();
    const sessions = [{ id: 'late', state: 'IN_PROGRESS', locationName: 'Hoàn Kiếm', scheduledStartAt: iso(-180), scheduledEndAt: iso(-60) }];
    const vi = workshopTasks(sessions, now, tIn('vi'), 'vi');
    const en = workshopTasks(sessions, now, tIn('en'), 'en');
    expect(vi[0].title).toBe('Buổi 07:00 · Hoàn Kiếm: chưa chốt điểm danh');
    expect(en[0].title).toBe('Session 07:00 · Hoàn Kiếm: attendance not finalized');
    expect(en[0].action).toBe('Finalize');
    expect(workshopTasks(sessions, now)[0].title).toBe(vi[0].title);
    expect(en).toHaveLength(vi.length);
  });

  it('shows the Staff home in English when the language is en', async () => {
    const now = Date.now();
    const iso = minutes => new Date(now + minutes * 60_000).toISOString();
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF', fullName: 'Test User' }),
      'GET /api/v1/workshop-sessions': json(200, [{ id: 'late', state: 'IN_PROGRESS', scheduledStartAt: iso(-180), scheduledEndAt: iso(-60) }]),
      'GET /api/v1/staff/retail-pickups': json(200, [{ order: { id: 'o1', paymentState: 'ACCEPTED_UNPAID' }, pickupReadyConfirmed: false }]),
      'GET /api/v1/staff/retail-shipments': json(200, { items: [] }),
    });
    renderRoutes(operatorHomeRoutes, { path: '/staff/home', language: 'en' });

    expect(await screen.findByText('Hello, Test User')).toBeInTheDocument();
    const main = within(screen.getByRole('main'));
    expect(await main.findByRole('link', { name: /attendance not finalized/ })).toBeInTheDocument();
    expect(await main.findByText('1 orders to handle')).toBeInTheDocument();
    expect(main.getByRole('link', { name: 'Final bills' })).toHaveAttribute('href', '/settlement');
    expect(main.getByRole('tablist', { name: 'Your work' })).toBeInTheDocument();
  });
});
