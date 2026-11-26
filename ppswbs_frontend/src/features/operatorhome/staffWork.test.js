import { describe, expect, it } from 'vitest';
import { workshopTasks } from './staffWork';

const session = (id, day) => ({ id, state: 'IN_PROGRESS', scheduledStartAt: `${day}T02:30:00Z`, scheduledEndAt: `${day}T05:00:00Z` });

describe('staff workshop tasks', () => {
  it('names the day of a session that is not today (BUG-E2E-0036)', () => {
    const [today, earlier] = workshopTasks([session('a', '2026-11-26'), session('b', '2026-11-25')], Date.parse('2026-11-26T10:00:00Z'))
      .sort((left, right) => right.start - left.start);
    expect(earlier.title).toContain('09:30 25/11');
    expect(today.title).toContain('09:30');
    expect(today.title).not.toContain('26/11');
  });
});
