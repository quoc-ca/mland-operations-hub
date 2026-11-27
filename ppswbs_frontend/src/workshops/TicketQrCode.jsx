import React, { useEffect, useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';

/** The QR of one ticket as a scannable image (003.1 FR-063); selecting it shows a large one. */
export function TicketQrCode({ value, ticketCode }) {
  const t = useT(messages);
  const [large, setLarge] = useState(false);
  const closeRef = useRef(null);
  useEffect(() => { if (large) closeRef.current?.focus(); }, [large]);
  const alt = t('ticket.qrAlt', { code: ticketCode });

  return (
    <div style={{ marginTop: '10px' }}>
      <span className="muted" style={{ fontSize: '12px', display: 'block' }}>{t('ticket.qr')}</span>
      <button type="button" onClick={() => setLarge(true)} aria-label={t('ticket.qrEnlarge', { code: ticketCode })}
        style={{ display: 'inline-block', padding: 0, border: '1px solid #d9d9d9', background: '#fff', cursor: 'zoom-in', lineHeight: 0 }}>
        <QRCodeSVG value={value} size={128} level="M" marginSize={4} role="img" aria-label={alt} />
      </button>
      <span className="muted" style={{ fontSize: '12px', display: 'block' }}>{t('ticket.qrHint')}</span>
      {large && (
        <div className="dialog-backdrop" onClick={() => setLarge(false)}>
          <div className="dialog" role="dialog" aria-modal="true" aria-label={alt} style={{ maxWidth: '440px', textAlign: 'center' }}
            onClick={event => event.stopPropagation()}
            onKeyDown={event => { if (event.key === 'Escape') setLarge(false); }}>
            <h2>{ticketCode}</h2>
            <QRCodeSVG value={value} size={360} level="M" marginSize={4} aria-hidden="true" style={{ width: '100%', height: 'auto' }} />
            <div className="dialog-actions">
              <button type="button" ref={closeRef} className="button outline" onClick={() => setLarge(false)}>{t('ticket.close')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
