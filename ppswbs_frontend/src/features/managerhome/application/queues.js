// Registry of the six queues (spec FR-003). `count` names the homeReads function that returns the waiting count; it is
// null until the owner delivers a count that matches the owner's list (spec FR-004, Clarification 1), and a queue with
// no count is not shown at all. Registered: the three queues whose default list already is the waiting set, and the refund and OTA queues whose
// owners added a count plus a list filter that returns exactly the counted set (R-2, R-4). The cash-close
// card opens the cash-close screen's confirmation view, a list of exactly the counted differences (R-3).
// Queues:
// - late payments: the owner page lists status PENDING_MANAGER with no other filter;
// - change requests: the owner page opens with status PENDING (read from the URL, so the link below carries it);
// - refund queue: ?status=WAITING opens the list with waiting=true (the four PENDING_* statuses);
// - OTA reconciliation: ?status=NEEDS_ATTENTION opens the list with needsAttention=true (OPEN and ESCALATED);
// - design review: the owner page shows everything the Manager read returns, which is NEED_REVIEW only.
export const QUEUES = [
  { id: 'late-payments', target: '/operations/late-payments', count: 'countLatePayments' },
  { id: 'refund-queue', target: '/settlement/refunds?status=WAITING', count: 'countRefundWaiting' },
  { id: 'change-requests', target: '/settlement/change-requests?status=PENDING', count: 'countChangeRequests' },
  { id: 'cash-close-differences', target: '/settlement/cash-close?view=confirmation', count: 'countCashCloseDifferences' },
  { id: 'ota-reconciliation', target: '/ota-connectivity/reconciliation?status=NEEDS_ATTENTION', count: 'countOtaNeedingAttention' },
  { id: 'design-review', target: '/design-review-requests', count: 'countDesignReviews' },
];

/** Only the queues that have a working count. */
export const registeredQueues = () => QUEUES.filter(queue => queue.count && queue.target);
