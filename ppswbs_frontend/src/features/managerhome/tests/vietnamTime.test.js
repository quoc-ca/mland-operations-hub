import { describe, expect, it } from 'vitest';
import { formatVietnamTime, hoursAgoInstant, todayInVietnam } from '../application/vietnamTime';

describe('vietnamTime', () => {
  it('changes the Vietnam day at 00:00 UTC+7, i.e. 17:00 UTC', () => {
    expect(todayInVietnam(new Date('2026-11-18T16:59:59Z'))).toBe('2026-11-18');
    expect(todayInVietnam(new Date('2026-11-18T17:00:00Z'))).toBe('2026-11-19');
  });

  it('gives the instant 24 hours earlier', () => {
    expect(hoursAgoInstant(new Date('2026-11-18T10:00:00Z'), 24)).toBe('2026-11-17T10:00:00.000Z');
  });

  it('shows times in Vietnam time whatever the browser zone', () => {
    expect(formatVietnamTime('2026-11-18T17:30:00Z', 'en')).toContain('00:30');
    expect(formatVietnamTime(null)).toBe('—');
  });
});
