import React from 'react';
import '../ota-connectivity/ota-connectivity.css';
import './workshop-settlement.css';

/** Staff/Manager settlement screen frame: sidebar, heading and content. The backend decides every permission. */
export function OpsPage({ eyebrow = 'SETTLEMENT OPERATIONS', title, intro, children }) {
  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / WORKSHOP SETTLEMENT <span>● Backend-authorized</span></div>
        <header className="ota-page-heading">
          <div>
            
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
      
      {intro && <p className="muted">{intro}</p>}
      {children}
    </main>
  );
}
