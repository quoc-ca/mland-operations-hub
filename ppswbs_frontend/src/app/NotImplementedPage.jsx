import React from 'react';
import { useT } from '../shared/i18n/I18nProvider';
import { sharedMessages } from '../shared/ui/messages';

export default function NotImplementedPage() {
  const t = useT(sharedMessages);
  return (
    <main className="public-page">
      <div className="container empty-state">
        <p className="eyebrow">MLAND FRONTEND</p>
        <h1>{t('notImplemented.title')}</h1>
        <p>{t('notImplemented.body')}</p>
      </div>
    </main>
  );
}
