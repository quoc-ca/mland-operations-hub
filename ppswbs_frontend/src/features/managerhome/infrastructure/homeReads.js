// Manager Home's only door to the other features' data (contracts/reads.md section A). Every function is a GET through
// the shared client and maps the response to the small view model of data-model.md, so no raw response shape reaches
// the UI. Audit rows keep time, actor and action only: the free-text reason and metadata are dropped here.
const asList = data => {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.items)) return data.items;
  return [];
};

const actorOf = actor => {
  if (!actor) return null;
  const id = actor.accountId ?? actor.id;
  if (actor.displayName) return id != null ? `${actor.displayName} #${id}` : actor.displayName;
  return id != null ? `#${id}` : null;
};

// A count is a whole number the owner computed; anything else is a failed read, never a zero.
const waitingOf = data => {
  const value = data?.waiting;
  if (!Number.isInteger(value) || value < 0) throw new Error('WAITING_COUNT_INVALID');
  return value;
};

const auditRow = (kind, source) => row => ({
  id: row.id ?? row.eventId ?? null,
  time: row.occurredAt ?? null,
  actor: actorOf(row.actor),
  kind,
  source,
  action: row.action ?? row.actionCode ?? '',
});

export const SETTLEMENT_AUDIT = '/api/v1/workshop-audit/settlement-records';
export const OPERATIONS_AUDIT = '/api/v1/workshop-audit/operations-events';

export function createHomeReads(http) {
  return {
    /** Late payments waiting for a Manager (owner default `PENDING_MANAGER`). */
    countLatePayments: async () => asList(await http('/api/v1/late-payment-reviews', { query: { status: 'PENDING_MANAGER' } })).length,
    /** Ticket change requests awaiting a decision (owner default `PENDING`). */
    countChangeRequests: async () => asList(await http('/api/v1/workshop-change-requests', { query: { status: 'PENDING' } })).length,
    /** Customer designs awaiting review (`NEED_REVIEW`; a Manager sees all). */
    countDesignReviews: async () => asList(await http('/api/v1/design-review-requests')).length,

    /** Refund requests waiting (the four PENDING_* statuses); the card opens the list with the same set. */
    countRefundWaiting: async () => waitingOf(await http('/api/v1/workshop-refund-requests/waiting-count')),
    /** Cash-close differences awaiting a Manager (state PENDING_MANAGER_CONFIRMATION); the card opens the confirmation view. */
    countCashCloseDifferences: async () => waitingOf(await http('/api/v1/workshop-cash-closes/awaiting-confirmation-count')),
    /** OTA reconciliation items needing attention (OPEN and ESCALATED). */
    countOtaNeedingAttention: async () => waitingOf(await http('/api/v1/ota-reconciliation-work-items/needing-attention-count')),

    /**
     * Sessions of one Vietnam day, optionally for one branch. The day summary (owner 003.2) also gives check-in
     * progress over the sessions that are not cancelled; if it cannot be read the session list still gives the counts.
     */
    fetchDayStrip: async (day, locationId) => {
      try {
        const summary = await http('/api/v1/workshop-day-summaries', { query: { date: day, locationId } });
        const numbers = [summary?.sessions, summary?.cancelledSessions, summary?.checkedIn, summary?.expected];
        if (numbers.every(value => Number.isInteger(value) && value >= 0)) {
          return { date: day, sessions: summary.sessions, cancelledSessions: summary.cancelledSessions, checkedIn: summary.checkedIn, expected: summary.expected };
        }
      } catch (error) {
        // fall through to the session list
      }
      return createHomeReads(http).fetchSessionsOfDay(day, locationId);
    },

    /** Sessions of one Vietnam day, optionally for one branch; cancelled ones are counted separately. */
    fetchSessionsOfDay: async (day, locationId) => {
      const sessions = asList(await http('/api/v1/workshop-sessions', { query: { from: day, to: day, locationId } }));
      return { date: day, sessions: sessions.length, cancelledSessions: sessions.filter(item => item.state === 'CANCELLED').length };
    },
    fetchLocations: async () => asList(await http('/api/v1/workshop-locations'))
      .filter(item => item.active !== false)
      .map(item => ({ id: item.id, name: item.name })),

    /** The dashboard's own read for the current month, no branch filter (research D5). */
    fetchMonthlyRevenue: async () => {
      const data = await http('/api/v1/executive-reports', { query: { periodType: 'MONTH' } });
      return {
        status: data?.status ?? 'UNAVAILABLE',
        totalRevenue: data?.totals?.totalRevenue ?? null,
        isDemo: Boolean(data?.developmentDemo),
      };
    },

    fetchOperationsDenials: async since => asList(await http(OPERATIONS_AUDIT, { query: { outcome: 'DENIED', from: since, limit: 5 } }))
      .map(auditRow('REFUSED_ATTEMPT', 'operations')),
    /** Denied attempts of the Settlement audit (owner 003.3, `denied=true`); the reason text is never read. */
    fetchSettlementDenials: async since => asList(await http(SETTLEMENT_AUDIT, { query: { denied: true, from: since, limit: 5 } }))
      .map(auditRow('REFUSED_ATTEMPT', 'settlement')),
    fetchPackageAvailabilityChanges: async since => asList(await http(OPERATIONS_AUDIT, {
      query: { action: 'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED', from: since, limit: 5 },
    })).map(auditRow('PACKAGE_AVAILABILITY_CHANGE', 'operations')),
  };
}
