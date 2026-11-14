import { getApp, getApps, initializeApp } from 'firebase/app';
import { firebaseConfig, hasFirebaseConfig } from './src/shared/auth/firebaseConfig';

// Compatibility entry for Firebase-generated setup snippets. Authentication
// uses src/shared/auth/firebaseSession.js; this module only exposes the same
// configured Firebase app for future Firebase products.
export const app = hasFirebaseConfig()
  ? (getApps().length ? getApp() : initializeApp(firebaseConfig))
  : null;

export { firebaseConfig, hasFirebaseConfig };
