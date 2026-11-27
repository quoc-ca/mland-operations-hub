import React, { useEffect, useRef, useState } from 'react';
import { useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/** The same QR is ignored while it stays in front of the camera and for this long after it was last seen. */
export const REPEAT_GUARD_MS = 4000;

/** A short tone: one high note for a recorded or repeated check-in, a low one for a refusal. Silent without Web Audio. */
function beep(audio, ok) {
  if (!audio) return;
  const oscillator = audio.createOscillator();
  const gain = audio.createGain();
  oscillator.type = ok ? 'sine' : 'square';
  oscillator.frequency.value = ok ? 880 : 220;
  gain.gain.value = 0.15;
  oscillator.connect(gain);
  gain.connect(audio.destination);
  oscillator.start();
  oscillator.stop(audio.currentTime + (ok ? 0.15 : 0.35));
}

/**
 * Camera scan of ticket QR codes (003.2 FR-057). The camera runs only while this dialog is mounted. `onScan(token)`
 * sends the check-in and resolves to {tone: 'success' | 'repeat' | 'error', title, detail}. The token is never shown,
 * logged or stored; it is only held in memory to ignore the same code seen again.
 */
export function QrCheckInScanner({ onScan, onClose }) {
  const t = useT(messages);
  const videoRef = useRef(null);
  const closeRef = useRef(null);
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;
  const [cameraFailed, setCameraFailed] = useState(false);
  const [checking, setChecking] = useState(false);
  const [outcome, setOutcome] = useState(null);

  useEffect(() => {
    closeRef.current?.focus();
    let stopped = false;
    let controls = null;
    let busy = false;
    const last = { token: null, at: 0 };
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    let audio = null;

    const handle = async token => {
      const now = Date.now();
      if (token === last.token && now - last.at < REPEAT_GUARD_MS) { last.at = now; return; }
      if (busy) return;
      busy = true;
      last.token = token;
      last.at = now;
      setChecking(true);
      setOutcome(null);
      const next = await onScanRef.current(token);
      busy = false;
      last.at = Date.now();
      if (stopped) return;
      setChecking(false);
      setOutcome(next);
      beep(audio, next.tone !== 'error');
    };

    // Deferred so that an effect that is cleaned up at once (React StrictMode) never opens the camera.
    const start = setTimeout(async () => {
      try {
        if (AudioContextClass) audio = new AudioContextClass();
        const { BrowserQRCodeReader } = await import('@zxing/browser');
        if (stopped) return;
        const started = await new BrowserQRCodeReader().decodeFromConstraints(
          { video: { facingMode: 'environment' } },
          videoRef.current,
          result => { if (result && !stopped) handle(result.getText()); });
        if (stopped) started.stop();
        else controls = started;
      } catch {
        if (!stopped) setCameraFailed(true);
      }
    }, 0);

    return () => {
      stopped = true;
      clearTimeout(start);
      controls?.stop();
      audio?.close?.();
    };
  }, []);

  return (
    <div className="dialog-backdrop">
      <div className="dialog qr-scanner" role="dialog" aria-modal="true" aria-label={t('scan.title')}
        onKeyDown={event => { if (event.key === 'Escape') onClose(); }}>
        <h2>{t('scan.title')}</h2>
        {cameraFailed
          ? <div className="notice error" role="alert"><p>{t('scan.noCamera')}</p></div>
          : <video ref={videoRef} className="qr-scanner-video" aria-label={t('scan.videoAria')} muted playsInline />}
        <div role="status" aria-live="assertive">
          {outcome ? (
            <div className={`qr-scan-result qr-scan-${outcome.tone}`}>
              <strong>{outcome.title}</strong>
              {outcome.detail && <span>{outcome.detail}</span>}
            </div>
          ) : !cameraFailed && <p className="muted">{checking ? t('scan.checking') : t('scan.hint')}</p>}
        </div>
        <div className="dialog-actions">
          <button type="button" ref={closeRef} className="button outline" onClick={onClose}>{t('scan.close')}</button>
        </div>
      </div>
    </div>
  );
}
