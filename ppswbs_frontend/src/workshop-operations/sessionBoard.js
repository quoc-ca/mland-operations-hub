// Tabs, order and date window of the session board.

/** Tab ids in display order; the draft tab is shown only when there is a draft session to put in it. */
export const BOARD_TABS = ['all', 'live', 'upcoming', 'done', 'cancelled', 'draft'];

const TAB_OF_STATE = {
  IN_PROGRESS: 'live',
  PUBLISHED: 'upcoming',
  SALES_PAUSED: 'upcoming',
  ATTENDANCE_FINALIZED: 'done',
  CANCELLATION_REQUESTED: 'cancelled',
  CANCELLED: 'cancelled',
  DRAFT: 'draft',
};

const RANK = { live: 0, upcoming: 1, done: 2, cancelled: 3, draft: 3 };

/** How far back the "ended" tab looks while the From date has not been chosen. */
export const DONE_DAYS_BACK = 7;

/** yyyy-mm-dd of today minus `daysBack`, in the browser's local calendar. */
export const localDay = (daysBack = 0) => {
  const d = new Date();
  d.setDate(d.getDate() - daysBack);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const time = value => {
  const millis = value ? new Date(value).getTime() : NaN;
  return Number.isNaN(millis) ? null : millis;
};

// Unknown times go last in either direction.
const compareTimes = (a, b, direction) => {
  if (a === null || b === null) return (a === null ? 1 : 0) - (b === null ? 1 : 0);
  return (a - b) * direction;
};

/** Running sessions first (ending soonest), then upcoming (soonest), then ended (latest), then cancelled and draft. */
export const sortSessions = sessions => [...sessions].sort((a, b) => {
  const tabA = TAB_OF_STATE[a.state];
  const tabB = TAB_OF_STATE[b.state];
  const byRank = (RANK[tabA] ?? 4) - (RANK[tabB] ?? 4);
  if (byRank !== 0) return byRank;
  if (tabA === 'live') return compareTimes(time(a.scheduledEndAt), time(b.scheduledEndAt), 1);
  return compareTimes(time(a.scheduledStartAt), time(b.scheduledStartAt), tabA === 'upcoming' ? 1 : -1);
});

/**
 * The sessions of one tab, sorted. `autoFrom` is true while the From date is the default: the list then holds the last
 * days too, which only the "ended" tab shows; every other tab starts today but never hides a session still running.
 */
export const sessionsOfTab = (sessions, tab, autoFrom) => {
  const today = localDay();
  return sortSessions(sessions.filter(item => {
    const own = TAB_OF_STATE[item.state];
    if (tab !== 'all' && own !== tab) return false;
    if (!autoFrom || tab === 'done' || own === 'live') return true;
    return !item.workshopDate || item.workshopDate >= today;
  }));
};
