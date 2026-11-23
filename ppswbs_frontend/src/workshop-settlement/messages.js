// vi/en dictionary of the settlement module (003.3). The English text is the wording the screens always had; the
// Vietnamese text is the business translation. Split by area in messages.*.js and merged here, like src/workshops.
import { messagesUi } from './messages.ui';
import { messagesCash } from './messages.cash';
import { messagesTicket } from './messages.ticket';
import { messagesCustomer } from './messages.customer';

const parts = [messagesUi, messagesCash, messagesTicket, messagesCustomer];

export const messages = {
  vi: Object.assign({}, ...parts.map(part => part.vi)),
  en: Object.assign({}, ...parts.map(part => part.en)),
};
