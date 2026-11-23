import { useHttp } from '../../shared/api/useHttp';
import { useResource } from '../../shared/ui/components';
import { createOperationsApi } from '../../workshop-operations/api/operationsApi';
import { staffRetailOpenCount } from '../retail-order/ui/manager/staffTabs';

const ZONE = 'Asia/Ho_Chi_Minh';
const DAY_MS = 24 * 60 * 60 * 1000;
const dateInVietnam = time => new Date(time).toLocaleDateString('en-CA', { timeZone: ZONE });
const clock = (time, vi) => new Date(time).toLocaleTimeString(vi ? 'vi-VN' : 'en-GB', { timeZone: ZONE, hour: '2-digit', minute: '2-digit' });
const listOf = data => (Array.isArray(data) ? data : data?.items || data?.content || []);

function minutesText(ms, vi) {
  const minutes = Math.max(1, Math.round(ms / 60_000));
  if (minutes < 60) return vi ? `${minutes} phút` : `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (vi) return rest ? `${hours} giờ ${rest} phút` : `${hours} giờ`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

/**
 * What a Staff's workshop sessions need now (Session 2026-11-22), most urgent first. The backend already limits the
 * list to the sessions this Staff may see; sessions that are finalized, cancelled or still drafts need nothing.
 */
export function workshopTasks(sessions, now = Date.now(), vi = true) {
  const today = dateInVietnam(now);
  return listOf(sessions)
    .filter(session => ['PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'].includes(session.state))
    .map(session => {
      const start = new Date(session.scheduledStartAt).getTime();
      const end = new Date(session.scheduledEndAt).getTime();
      const opens = session.checkInOpensAt ? new Date(session.checkInOpensAt).getTime() : start;
      const when = clock(start, vi);
      const where = session.locationName ? ` · ${session.locationName}` : '';
      const name = (vi ? `Buổi ${when}` : `Session ${when}`) + where;
      if (session.state === 'IN_PROGRESS' && now > end) {
        return { id: session.id, rank: 0, urgent: true, title: vi ? `${name}: chưa chốt điểm danh` : `${name}: attendance not finalized`,
          detail: vi ? `Đã kết thúc lúc ${clock(end, vi)}` : `Ended at ${clock(end, vi)}`, action: vi ? 'Chốt điểm danh' : 'Finalize', start };
      }
      if (session.state === 'IN_PROGRESS') {
        return { id: session.id, rank: 1, urgent: true, title: vi ? `${name} đang diễn ra` : `${name} is in progress`,
          detail: vi ? `Kết thúc lúc ${clock(end, vi)}` : `Ends at ${clock(end, vi)}`, action: vi ? 'Check-in khách' : 'Check guests in', start };
      }
      if (dateInVietnam(start) !== today && start < now) return null;
      if (now >= opens) {
        return { id: session.id, rank: 2, urgent: true, title: vi ? `${name}: đã mở check-in` : `${name}: check-in is open`,
          detail: vi ? `Bắt đầu sau ${minutesText(start - now, vi)}` : `Starts in ${minutesText(start - now, vi)}`, action: vi ? 'Check-in khách' : 'Check guests in', start };
      }
      if (dateInVietnam(start) !== today) return null;
      return { id: session.id, rank: 3, urgent: false, title: vi ? `${name} sắp diễn ra` : `${name} is coming up`,
        detail: vi ? `Mở check-in sau ${minutesText(opens - now, vi)}` : `Check-in opens in ${minutesText(opens - now, vi)}`, action: vi ? 'Xem buổi' : 'Open session', start };
    })
    .filter(Boolean)
    .sort((a, b) => a.rank - b.rank || a.start - b.start);
}

/**
 * The Staff's open work as rail badge counts, keyed like navModel (`groupId`, `groupId/tabId`): the same two numbers
 * the Staff home shows (workshop tasks, open branch orders), read from the same lists the backend already limits to
 * this Staff's branch and sessions. A count that cannot be read is absent, never 0.
 */
export const CONSULTATION_PAGE_SIZE = 50;
export const CONSULTATION_MAX_PAGES = 4;
// The rail's badge shows "99+" from 100 on, so a count of 100 or more is correct even when not every page was read.
const BADGE_CEILING = 100;

/**
 * Open consultations nobody has claimed, read from the same list the Staff inbox shows (the backend limits it to this
 * Staff's branches; conversations without a branch are not in it). The list is paged without a total, so every page is
 * walked up to CONSULTATION_MAX_PAGES. When pages remain, the count is only a lower bound: it is returned only once it
 * reaches the badge ceiling, otherwise the call fails and the badge stays absent rather than understate the work.
 */
export async function countUnclaimedConsultations(http) {
  let cursor = null;
  let unclaimed = 0;
  for (let page = 0; page < CONSULTATION_MAX_PAGES; page += 1) {
    const query = { status: 'OPEN', limit: CONSULTATION_PAGE_SIZE, ...(cursor ? { cursor } : {}) };
    const result = await http('/api/v1/consultations', { query });
    unclaimed += listOf(result).filter(item => !item.claimantRole).length;
    if (!result?.hasMore || !result?.nextCursor) return unclaimed;
    cursor = result.nextCursor;
  }
  if (unclaimed >= BADGE_CEILING) return unclaimed;
  throw new Error('CONSULTATION_COUNT_INCOMPLETE');
}

// Which counted tabs make up the count of each rail group, for a Staff. A group count exists only when every one of its
// counted tabs was read, so a badge never understates the waiting work.
const STAFF_GROUPS = {
  workshop: ['workshop/sessions'],
  retail: ['retail/orders'],
  tickets: ['tickets/change-requests'],
  customers: ['customers/design-review', 'customers/consultations'],
};

export async function readStaffNavCounts(http, now = Date.now()) {
  const [sessions, retail, changes, designs, consultations] = await Promise.allSettled([
    createOperationsApi(http).listSessions({ from: dateInVietnam(now - DAY_MS), to: dateInVietnam(now) }),
    Promise.all([
      http('/api/v1/staff/retail-pickups'),
      http('/api/v1/staff/retail-shipments', { query: { page: 0, size: 50, status: 'PREPARING' } }),
    ]),
    // Unpaged lists, so the length is exact: the backend limits change requests to this Staff's branches, and the
    // design review queue is the same for Staff and Manager.
    http('/api/v1/workshop-change-requests', { query: { status: 'PENDING' } }),
    http('/api/v1/design-review-requests'),
    countUnclaimedConsultations(http),
  ]);
  const counts = {};
  if (sessions.status === 'fulfilled') counts['workshop/sessions'] = workshopTasks(sessions.value, now).length;
  if (retail.status === 'fulfilled') {
    const [pickups, preparing] = retail.value;
    counts['retail/orders'] = staffRetailOpenCount(pickups, listOf(preparing));
  }
  if (changes.status === 'fulfilled') counts['tickets/change-requests'] = listOf(changes.value).length;
  if (designs.status === 'fulfilled') counts['customers/design-review'] = listOf(designs.value).length;
  if (consultations.status === 'fulfilled') counts['customers/consultations'] = consultations.value;
  Object.entries(STAFF_GROUPS).forEach(([groupId, keys]) => {
    if (keys.every(key => Number.isInteger(counts[key]))) counts[groupId] = keys.reduce((sum, key) => sum + counts[key], 0);
  });
  return counts;
}

/** The Staff's open work, for the Staff home and the sidebar badges. Counts stay null while unknown. */
export function useStaffWork(enabled = true) {
  const http = useHttp();
  const sessions = useResource(() => {
    if (!enabled) return Promise.resolve(null);
    const now = Date.now();
    // From yesterday, so a session that ended late yesterday and is still not finalized shows up too.
    return createOperationsApi(http).listSessions({ from: dateInVietnam(now - DAY_MS), to: dateInVietnam(now) });
  }, [enabled]);
  const retail = useResource(async () => {
    if (!enabled) return null;
    const [pickups, preparing] = await Promise.all([
      http('/api/v1/staff/retail-pickups'),
      http('/api/v1/staff/retail-shipments', { query: { page: 0, size: 50, status: 'PREPARING' } }),
    ]);
    return { pickups, shipments: listOf(preparing) };
  }, [enabled]);
  const workshopCount = sessions.data ? workshopTasks(sessions.data).length : null;
  const retailCount = retail.data ? staffRetailOpenCount(retail.data.pickups, retail.data.shipments) : null;
  return {
    sessions,
    retail,
    workshopCount,
    retailCount,
    reload: () => { sessions.reload(); retail.reload(); },
  };
}
