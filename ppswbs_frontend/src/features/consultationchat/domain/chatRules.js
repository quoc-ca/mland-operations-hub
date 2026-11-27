// Feature-013 Chat Domain Rules
import { translate } from '../../../shared/i18n/I18nProvider';
import { consultationMessages } from '../messages';

export const MAX_MESSAGE_LENGTH = 2000;

export function countCodePoints(str) {
  if (!str) return 0;
  return Array.from(str).length;
}

// `t` is the feature translator from useT(consultationMessages); without it the Vietnamese text is used.
export function validateMessageText(text, t = (key, params) => translate(consultationMessages, 'vi', key, params)) {
  if (!text || !text.trim()) {
    return t('messageEmpty');
  }
  const len = countCodePoints(text.trim());
  if (len > MAX_MESSAGE_LENGTH) {
    return t('messageTooLong', { count: len });
  }
  return null;
}
