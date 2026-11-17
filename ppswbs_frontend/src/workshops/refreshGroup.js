import { getOwnedBookingGroup, lookupGuestBookingGroup } from '../api/workshops';
import { buildContactProof } from './contactValidation';

/**
 * Reads a booking group again for the checkout screens: a Member by their own session, a Guest by one contact proof
 * (the e-mail when there is one, else the phone). The group shown right after "giữ chỗ" is a snapshot, and the server
 * releases an unpaid group by itself when its hold ends, so the screen has to read it again to show the true status.
 * Returns the fresh group, or null when it cannot be read (the caller keeps the last one it has).
 */
export async function refreshBookingGroup({ groupCode, mode, memberAuthorization, contact }) {
  if (!groupCode) return null;
  try {
    if (mode === 'MEMBER') {
      const bearer = await memberAuthorization();
      if (!bearer) return null;
      return await getOwnedBookingGroup(groupCode, { authorization: bearer });
    }
    const email = String(contact?.contactEmail ?? '').trim();
    const { proof } = buildContactProof(email
      ? { email }
      : { phone: contact?.contactPhone, phoneCountry: contact?.contactPhoneCountry });
    if (!proof) return null;
    return await lookupGuestBookingGroup({ groupCode, ...proof });
  } catch {
    return null;
  }
}
