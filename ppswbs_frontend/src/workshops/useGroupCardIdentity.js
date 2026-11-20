import { useEffect, useState } from 'react';
import { guestContactProof } from './contactValidation';

/**
 * What the group card needs to prove the buyer (003.1 FR-042/FR-048): a signed-in Member sends the bearer and no
 * contact; a Guest sends no bearer and exactly one contact. The server refuses any mix of the two.
 */
export function useGroupCardIdentity({ mode, memberAuthorization, contact }) {
  const [authorization, setAuthorization] = useState(null);
  useEffect(() => {
    let active = true;
    if (mode !== 'MEMBER') { setAuthorization(null); return () => { active = false; }; }
    memberAuthorization().then(value => { if (active) setAuthorization(value); }).catch(() => {});
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);
  const isMember = mode === 'MEMBER';
  return { authorization: isMember ? authorization : null, proof: isMember ? null : guestContactProof(contact) };
}
