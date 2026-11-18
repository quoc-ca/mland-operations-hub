/**
 * 003.2 US7 audit review: pure helpers for the two tabs (filters kept in the URL so a lost session returns the
 * Manager to the same filtered view, FR-055), request queries and display formats. No network, no React.
 */

/** Each tab owns its filters; the URL prefix keeps them apart so switching tabs never loses the other tab's filters. */
export const TABS = {
  operations: { prefix: 'o_', fields: ['action', 'outcome', 'actorId', 'sessionId', 'from', 'to'] },
  settlement: { prefix: 's_', fields: ['actionCode', 'actorId', 'aggregateType', 'aggregateReference', 'from', 'to'] },
};

export const OUTCOMES = ['SUCCESS', 'FAILURE', 'DENIED', 'SENT'];

export const emptyFilters = tab => Object.fromEntries(TABS[tab].fields.map(field => [field, '']));

export const tabOf = params => (params.get('tab') === 'settlement' ? 'settlement' : 'operations');

/** Applied filters of one tab, read from the URL search params. */
export function readFilters(params, tab) {
  const { prefix, fields } = TABS[tab];
  return Object.fromEntries(fields.map(field => [field, params.get(prefix + field) || '']));
}

/** A copy of `params` with the filters of `tab` replaced; empty values are removed from the URL. */
export function writeFilters(params, tab, filters) {
  const next = new URLSearchParams(params);
  const { prefix, fields } = TABS[tab];
  fields.forEach(field => {
    const value = (filters[field] || '').trim();
    if (value) next.set(prefix + field, value);
    else next.delete(prefix + field);
  });
  return next;
}

export const activeFilterCount = filters => Object.values(filters).filter(value => (value || '').trim()).length;

export const filtersKey = filters => JSON.stringify(filters);

const toIso = local => {
  const date = new Date(local);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
};

/** API query of one tab: trimmed text, local date-times converted to ISO instants, empty values omitted. */
export function toQuery(filters) {
  const query = {};
  Object.entries(filters).forEach(([field, raw]) => {
    const value = (raw || '').trim();
    if (!value) return;
    query[field] = field === 'from' || field === 'to' ? toIso(value) : value;
  });
  return query;
}

/** FR-049: a reversed time range is refused and the filters stay filled. */
export function hasReversedRange(filters) {
  if (!filters.from || !filters.to) return false;
  const from = new Date(filters.from).getTime();
  const to = new Date(filters.to).getTime();
  return !Number.isNaN(from) && !Number.isNaN(to) && from > to;
}

const pad = value => String(value).padStart(2, '0');

/** dd/mm/yyyy HH:mm:ss in the viewer's local time, as in the approved mockup. */
export function formatTime(iso) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** Correlation ids are long; the screen shows the start and end, the full value stays in the title. */
export function shortRef(value) {
  if (!value) return '—';
  return value.length > 12 ? `${value.slice(0, 4)}…${value.slice(-3)}` : value;
}

/** Actor cell text: name and #id, the id alone when the account is gone, nothing for a system action. */
export function actorLabel(actor) {
  if (!actor) return null;
  return actor.displayName ? `${actor.displayName} #${actor.accountId}` : `#${actor.accountId}`;
}
