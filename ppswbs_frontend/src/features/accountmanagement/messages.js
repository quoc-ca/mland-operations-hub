import { addressMessages } from './messages.address';
import { adminDetailMessages } from './messages.adminDetail';
import { adminListMessages } from './messages.adminList';
import { authMessages } from './messages.auth';
import { carrierMessages } from './messages.carrier';
import { createMessages } from './messages.create';
import { hookMessages } from './messages.hooks';
import { profileMessages } from './messages.profile';
import { roleMessages } from './messages.roles';

// One { vi, en } dictionary for the account-management feature, split per screen to stay readable.
// Keys are namespaced per screen (addr., auth., carrier., create., detail., hook., list., profile., roles.).
const parts = [
  addressMessages, adminDetailMessages, adminListMessages, authMessages,
  carrierMessages, createMessages, hookMessages, profileMessages, roleMessages,
];

export const accountMessages = {
  vi: Object.assign({}, ...parts.map(part => part.vi)),
  en: Object.assign({}, ...parts.map(part => part.en)),
};
