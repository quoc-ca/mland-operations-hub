// Shared pure helpers for the ring design domain (no hooks: callers pass `t` / `fmt` in).
import { translate } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/** Default translator for callers that do not pass `t` (Vietnamese is the default language). */
export const viT = (key, params) => translate(messages, 'vi', key, params);

/** Whole-VND amount (numbers or numeric strings, possibly with a fractional part) through useFormat().money. */
export function wholeMoney(fmt, amount) {
  return fmt.money(String(Math.round(Number(amount) || 0)));
}
