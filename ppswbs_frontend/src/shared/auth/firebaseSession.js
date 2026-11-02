import { initializeApp } from 'firebase/app';
import {
  createUserWithEmailAndPassword,
  getAuth,
  onAuthStateChanged,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
} from 'firebase/auth';
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

export function getFirebaseAuth() {
  return firebaseAuth();
}

function requireFirebaseAuth() {
  const current = firebaseAuth();
  if (!current) {
    const error = new Error('Firebase is not configured');
    error.code = 'CONFIGURATION_REQUIRED';
    throw error;
  }
  return current;
}

export async function signInWithPassword(email, password) {
  return signInWithEmailAndPassword(requireFirebaseAuth(), email, password);
}

export async function registerWithPassword(email, password) {
  return createUserWithEmailAndPassword(requireFirebaseAuth(), email, password);
}

export async function sendPasswordReset(email) {
  return sendPasswordResetEmail(requireFirebaseAuth(), email);
}

export async function sendVerificationEmail() {
  const current = requireFirebaseAuth();
  if (!current.currentUser) {
    const error = new Error('No signed-in user');
    error.code = 'AUTHENTICATION_REQUIRED';
    throw error;
  }
  return sendEmailVerification(current.currentUser);
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
