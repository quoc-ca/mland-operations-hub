import React, { useEffect, useState } from 'react';
import { useLanguage } from '../shared/i18n/I18nProvider';
import { groupResultHeading } from './groupDisplay';

/** Eyebrow and title of the held-group screen; it follows the clock so the wording changes when the payment deadline passes. */
export function GroupResultHeading({ group }) {
  const { language } = useLanguage();
  const [now, setNow] = useState(() => Date.now());
  const watching = group?.groupStatus === 'PENDING_PAYMENT' && Boolean(group?.paymentDeadlineAt);
  useEffect(() => {
    if (!watching) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, [watching]);
  const { eyebrow, title } = groupResultHeading(group, now, language);
  return (
    <>
      <p className="eyebrow">{eyebrow}</p>
      <h1 style={{ color: '#002967' }}>{title}</h1>
    </>
  );
}
