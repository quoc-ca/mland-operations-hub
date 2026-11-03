import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { currentIdToken, endSession, watchSession } from './firebaseSession';

// Session state for the whole app: 'loading' | 'unconfigured' | 'signed-out' | 'signed-in'.
// A route guard or hidden button never replaces the backend's authorization.
const AuthContext = createContext(null);

const firebaseSource = { watch: watchSession, getIdToken: currentIdToken, signOut: endSession };

export function AuthProvider({ children, source = firebaseSource }) {
  const [status, setStatus] = useState('loading');
  useEffect(() => source.watch(next => setStatus(next.status)), [source]);
  const value = useMemo(() => ({
    status,
    getIdToken: source.getIdToken,
    signOut: async () => {
      await source.signOut();
      setStatus(current => (current === 'unconfigured' ? current : 'signed-out'));
    },
  }), [status, source]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
