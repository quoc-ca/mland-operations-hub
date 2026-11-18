import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { managerHomeMessages } from './messages';

// Fixed set of four (spec FR-011); the owner confirms the set (spec Assumptions).
export const SHORTCUTS = [
  { id: 'packages', to: '/operations/packages' },
  { id: 'availability', to: '/operations/package-availability' },
  { id: 'locations', to: '/operations/admin' },
  { id: 'sessions', to: '/operations/sessions' },
];

export default function Shortcuts() {
  const t = useT(managerHomeMessages);
  return (
    <section aria-labelledby="mh-shortcuts" className="mh-section">
      <h2 id="mh-shortcuts" className="mh-section-title">{t('shortcuts.title')}</h2>
      <ul className="mh-shortcuts">
        {SHORTCUTS.map(item => <li key={item.id}><Link className="button outline" to={item.to}>{t(`shortcut.${item.id}`)}</Link></li>)}
      </ul>
    </section>
  );
}
