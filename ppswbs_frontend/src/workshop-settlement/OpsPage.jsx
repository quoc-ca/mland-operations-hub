import React from 'react';
import { OperationsSidebar } from '../shared/ui/OperationsSidebar';
import '../ota-connectivity/ota-connectivity.css';
import './workshop-settlement.css';

/** Staff/Manager settlement screen frame: sidebar, heading and content. The backend decides every permission. */
export function OpsPage({ eyebrow = 'SETTLEMENT OPERATIONS', title, intro, children }) {
  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="ota-topline">MLAND / WORKSHOP SETTLEMENT <span>● Backend-authorized</span></div>
        <header className="ota-page-heading">
          <div>
            <p className="ota-eyebrow">{eyebrow}</p>
            <h1>{title}</h1>
            {intro && <p>{intro}</p>}
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}

/** Public customer screen frame (no operations sidebar). */
export function CustomerPage({ title, intro, children }) {
  return (
    <main className="settlement-customer">
      <h1>{title}</h1>
      {intro && <p className="muted">{intro}</p>}
      {children}
    </main>
  );
}
