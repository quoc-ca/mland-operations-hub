import React, { useEffect, useRef } from 'react';
import { Outlet, useNavigate } from 'react-router';
import { translate, useLanguage } from '../i18n/I18nProvider';
import { sharedMessages } from '../ui/messages';
import { useAuth } from './AuthProvider';
import { executePrivateStateCleanup } from './privateStateCleanup';

/**
 * Router-level session navigation boundary (FR-041 / spec-general.md §2 / T070).
 * It is the ONLY client unit permitted to perform terminal session navigation.
 * - USER_SIGN_OUT: executes registered private-state cleanups, navigates to / with replace and 'Đã đăng xuất'.
 * - SESSION_LOST: executes registered private-state cleanups once, navigates to / with replace and 'Phiên đã kết thúc, vui lòng đăng nhập lại'.
 * - ACCESS_DENIED (403): retains session, does NOT clear drafts, navigates to / with replace and generic safe notice.
 */
export function SessionNavigationBoundary() {
  const navigate = useNavigate();
  const { subscribeOutcome } = useAuth();
  // The notice is shown once, in the language chosen when the outcome happens; a ref keeps the subscription stable.
  const { language } = useLanguage();
  const languageRef = useRef(language);
  languageRef.current = language;

  useEffect(() => {
    if (!subscribeOutcome) return;

    return subscribeOutcome(outcome => {
      if (!outcome || !outcome.type) return;
      const notice = key => translate(sharedMessages, languageRef.current, key);

      if (outcome.type === 'USER_SIGN_OUT') {
        executePrivateStateCleanup();
        navigate('/', {
          replace: true,
          state: { sessionNotice: notice('session.noticeSignedOut') },
        });
      } else if (outcome.type === 'SESSION_LOST') {
        executePrivateStateCleanup();
        if (outcome.code === 'ACCOUNT_NOT_FOUND') {
          try {
            sessionStorage.clear();
          } catch {
            // best effort storage purge
          }
          navigate('/login', {
            replace: true,
            state: { sessionNotice: notice('session.noticeAccountMissing') },
          });
        } else {
          navigate('/', {
            replace: true,
            state: { sessionNotice: notice('session.noticeEnded') },
          });
        }
      } else if (outcome.type === 'ACCESS_DENIED') {
        // Safe generic notice; no role, lifecycle, or Workshop-scope detail is disclosed
        navigate('/', {
          replace: true,
          state: { sessionNotice: notice('session.noticeAccessDenied') },
        });
      }
    });
  }, [navigate, subscribeOutcome]);

  return <Outlet />;
}

export default SessionNavigationBoundary;

