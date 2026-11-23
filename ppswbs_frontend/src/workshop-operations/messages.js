// vi/en dictionary of the Staff/Manager workshop operations module, merged from one file per area (same layout as
// src/workshops/messages.js). Shared texts (loading, retry, cancel, close, error.*) live in sharedMessages.
import { messagesAdmin } from './messages.admin';
import { messagesAudit } from './messages.audit';
import { messagesCommon } from './messages.common';
import { messagesPackages } from './messages.packages';
import { messagesSession } from './messages.session';

const parts = [messagesCommon, messagesAdmin, messagesAudit, messagesPackages, messagesSession];

export const messages = {
  vi: Object.assign({}, ...parts.map(part => part.vi)),
  en: Object.assign({}, ...parts.map(part => part.en)),
};
