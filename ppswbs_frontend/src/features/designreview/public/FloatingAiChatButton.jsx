import React from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useCurrentAccount } from '../../accountmanagement/hooks/useCurrentAccount';

export default function FloatingAiChatButton() {
  const location = useLocation();
  const { userRole: authRole } = useAuth();
  const { account } = useCurrentAccount?.() || {};
  const userRole = account?.role || authRole;
  const isStaffOrAdmin = userRole === 'STAFF' || userRole === 'ADMIN' || userRole === 'MANAGER';

  // Do not display if user is internal staff/admin or already on the AI chat page
  if (isStaffOrAdmin || location.pathname === '/ai-support') {
    return null;
  }

  return (
    <Link
      to="/ai-support"
      className="floating-ai-chat-btn"
      data-testid="floating-ai-chat-btn"
      aria-label="Trợ lý AI tư vấn"
      title="Trò chuyện với Trợ lý AI"
      style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        zIndex: 9999,
        width: '56px',
        height: '56px',
        borderRadius: '50%',
        background: 'linear-gradient(135deg, #0ea5e9 0%, #0284c7 50%, #0369a1 100%)',
        color: '#ffffff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 4px 14px rgba(2, 132, 199, 0.4)',
        textDecoration: 'none',
        fontSize: '1.65rem',
        cursor: 'pointer',
        transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'scale(1.08) translateY(-2px)';
        e.currentTarget.style.boxShadow = '0 6px 20px rgba(2, 132, 199, 0.55)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'scale(1) translateY(0)';
        e.currentTarget.style.boxShadow = '0 4px 14px rgba(2, 132, 199, 0.4)';
      }}
    >
      <svg
        width="28"
        height="28"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        style={{ pointerEvents: 'none' }}
      >
        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" fill="rgba(255, 255, 255, 0.2)" />
        <circle cx="8.5" cy="11.5" r="1.2" fill="#ffffff" stroke="none" />
        <circle cx="12" cy="11.5" r="1.2" fill="#ffffff" stroke="none" />
        <circle cx="15.5" cy="11.5" r="1.2" fill="#ffffff" stroke="none" />
      </svg>
      <span
        style={{
          position: 'absolute',
          top: '-4px',
          right: '-4px',
          width: '14px',
          height: '14px',
          backgroundColor: '#10b981',
          borderRadius: '50%',
          border: '2px solid #ffffff',
        }}
      />
    </Link>
  );
}
