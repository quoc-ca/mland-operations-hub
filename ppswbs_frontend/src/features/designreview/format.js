// Amounts reach the pages as numbers or decimal strings ("15000000", "15000000.00"). useFormat().money only groups
// whole-number strings, so round to a whole VND string first; non-numeric text is passed through unchanged.
export function wholeAmount(value) {
  const number = Number(value);
  if (Number.isFinite(number)) return String(Math.round(number));
  return value === undefined || value === null ? '' : String(value);
}
