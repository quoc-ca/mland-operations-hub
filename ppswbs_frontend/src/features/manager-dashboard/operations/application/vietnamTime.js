// Manager Home: "today" and the 24-hour window are Vietnam calendar time (UTC+7), whatever the browser's time zone
// (spec 010 FR-034, FR-036; research D7).
const VIETNAM_ZONE = 'Asia/Ho_Chi_Minh';

/** Today's date in Vietnam as yyyy-MM-dd. */
export function todayInVietnam(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: VIETNAM_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** The instant `hours` before `now`, as an ISO string (an instant needs no zone conversion). */
export function hoursAgoInstant(now = new Date(), hours = 24) {
  return new Date(now.getTime() - hours * 3600 * 1000).toISOString();
}

/** A date-time shown in Vietnam time, in the page language. */
export function formatVietnamTime(value, language = 'vi') {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(language === 'en' ? 'en-GB' : 'vi-VN', {
    dateStyle: 'short', timeStyle: 'short', timeZone: VIETNAM_ZONE,
  }).format(date);
}
