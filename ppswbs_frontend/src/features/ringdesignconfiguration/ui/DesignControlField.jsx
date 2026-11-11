import React from 'react';

export default function DesignControlField({
  label,
  helperText,
  errorText,
  children,
  required = false,
}) {
  return (
    <div className="ring-control-group" style={{ marginBottom: '1.25rem' }}>
      <label
        style={{
          display: 'block',
          fontWeight: '600',
          fontSize: '0.95rem',
          color: '#1e293b',
          marginBottom: '0.35rem',
        }}
      >
        {label} {required && <span style={{ color: '#ef4444' }}>*</span>}
      </label>
      {helperText && (
        <div
          style={{
            fontSize: '0.825rem',
            color: '#64748b',
            marginBottom: '0.65rem',
            lineHeight: '1.4',
          }}
        >
          {helperText}
        </div>
      )}
      {children}
      {errorText && (
        <div
          role="alert"
          style={{
            fontSize: '0.8rem',
            color: '#ef4444',
            marginTop: '0.35rem',
            fontWeight: '500',
          }}
        >
          {errorText}
        </div>
      )}
    </div>
  );
}

