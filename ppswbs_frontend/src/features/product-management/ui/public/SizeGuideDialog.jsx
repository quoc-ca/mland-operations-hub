import React, { useEffect, useId, useRef, useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { sizeGuide } from './sizeGuide';

/**
 * "Bảng Quy Đổi Kích Cỡ" dialog: title, one line of description, the shop's size chart image and, for each way to
 * measure, a heading with numbered steps.
 * The content comes from {@link sizeGuide}; a missing image shows a short notice instead of a broken picture.
 */
export function SizeGuideDialog({ onClose, guide = sizeGuide }) {
  const t = useT(messages);
  const titleId = useId();
  const closeRef = useRef(null);
  const [imageFailed, setImageFailed] = useState(false);
  useEffect(() => { closeRef.current?.focus(); }, []);
  return (
    <div className="dialog-backdrop size-guide-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
      {/* Escape closes only this dialog, not a quick-add dialog it is opened from. */}
      <div className="dialog size-guide" role="dialog" aria-modal="true" aria-labelledby={titleId}
        onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); onClose(); } }}>
        <div className="size-guide-head">
          <h2 id={titleId}>{guide.title}</h2>
          <button type="button" ref={closeRef} className="quick-add-close" onClick={onClose} aria-label={t('sizeGuide.close')}>×</button>
        </div>
        {guide.description && <p className="size-guide-description">{guide.description}</p>}
        {imageFailed || !guide.image
          ? <p className="status">{t('sizeGuide.noImage')}</p>
          : <img className="size-guide-image" src={guide.image} alt={guide.imageAlt || guide.title} onError={() => setImageFailed(true)} />}
        {(guide.methods || []).map(method => (
          <section key={method.title} className="size-guide-method" aria-label={method.title}>
            <h3>{method.title}</h3>
            <ol className="size-guide-steps">
              {method.steps.map(step => <li key={step}>{step}</li>)}
            </ol>
          </section>
        ))}
      </div>
    </div>
  );
}
