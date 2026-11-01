import { initializeApp } from 'firebase/app';
import { getAuth, onAuthStateChanged, signOut as firebaseSignOut } from 'firebase/auth';
import { firebaseConfig, hasFirebaseConfig } from './firebaseConfig';

// Firebase session (proposal §7). Firebase proves identity only; the backend resolves the Mland account and
// decides every permission. The ID token is read from the SDK for each request and never stored, put in a
// URL or logged by this code. Sign-in screens belong to 001.
export function readFirebaseConfig() {
  return hasFirebaseConfig() ? firebaseConfig : null;
}

let auth = null;

function firebaseAuth() {
  if (auth) return auth;
  const config = readFirebaseConfig();
  if (!config) return null;
  auth = getAuth(initializeApp(config));
  return auth;
}

/** Subscribes to sign-in changes; returns an unsubscribe function. Reports 'unconfigured' without config. */
export function watchSession(listener) {
  const current = firebaseAuth();
  if (!current) {
    listener({ status: 'unconfigured' });
    return () => {};
  }
  return onAuthStateChanged(current, user => listener({ status: user ? 'signed-in' : 'signed-out' }));
}

/** The current Firebase ID token, or null when nobody is signed in. forceRefresh asks Firebase for a new one. */
export async function currentIdToken(forceRefresh = false) {
  const user = firebaseAuth()?.currentUser;
  return user ? user.getIdToken(forceRefresh) : null;
}

export async function endSession() {
  const current = firebaseAuth();
  if (current) await firebaseSignOut(current);
}
