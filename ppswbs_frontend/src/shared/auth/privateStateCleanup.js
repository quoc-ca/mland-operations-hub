/**
 * Private state & draft cleanup registry (FR-041 / Revision R-SL T070).
 * Components holding private in-memory drafts (Profile, AddressModal, etc.)
 * register their reset callbacks here.
 * On terminal sign-out or session loss, all registered cleanups execute.
 */

const cleanupCallbacks = new Set();

export function registerPrivateStateCleanup(fn) {
  if (typeof fn === 'function') {
    cleanupCallbacks.add(fn);
  }
  return () => {
    cleanupCallbacks.delete(fn);
  };
}

export function executePrivateStateCleanup() {
  cleanupCallbacks.forEach(fn => {
    try {
      fn();
    } catch (err) {
      console.warn('Private state cleanup error:', err);
    }
  });
}

