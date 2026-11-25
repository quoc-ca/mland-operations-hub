import React from 'react';
import { Link } from 'react-router';
import { useT } from '../i18n/I18nProvider';
import { operationsShellMessages } from './operationsShellMessages';
import './workspace.css';

/**
 * Standard workspace shell rendered when an unauthenticated user visits
 * any Operations or Settlement manager route.
 * Shown inside the operations shell (rail on the left); displays the styled login card.
 */
export function OperationsAuthWorkspace({
  topline = 'MLAND / OPERATIONS',
  message,
}) {
  const t = useT(operationsShellMessages);

  return (
    <div className="wsp-workspace">
      
      <main className="wsp-main">
        <div className="wsp-topline">
          {topline} <span>● {t('staffManager')}</span>
        </div>
        <div className="empty-state pm-session" role="status">
          <p>{message || t('authRequired')}</p>
          <Link className="button primary" to="/login">
            {t('workspaceSignIn')}
          </Link>
        </div>
      </main>
    </div>
  );
}

export default OperationsAuthWorkspace;

