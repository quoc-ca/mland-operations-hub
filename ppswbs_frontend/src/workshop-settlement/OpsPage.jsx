import React from 'react';
import '../shared/ui/workspace.css';
import './workshop-settlement.css';

/** Staff/Manager settlement screen frame: sidebar, heading and content. The backend decides every permission. */
export function OpsPage({ eyebrow, title, intro, children }) {
  return (
    <div className="wsp-workspace">
      
      <main className="wsp-main">
        <header className="wsp-page-heading">
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
