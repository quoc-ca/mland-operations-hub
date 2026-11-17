const env = import.meta.env;

function envValue(primaryKey, legacyKey) {
  return env[primaryKey] || env[legacyKey] || '';
}

// Firebase Web configuration identifies the public app. It is not an auth token
// or a server credential, but it still belongs in environment configuration.
export const firebaseConfig = Object.freeze({
  apiKey: envValue('VITE_FIREBASE_API_KEY', 'apiKey'),
  authDomain: envValue('VITE_FIREBASE_AUTH_DOMAIN', 'authDomain'),
  projectId: envValue('VITE_FIREBASE_PROJECT_ID', 'projectId'),
  storageBucket: envValue('VITE_FIREBASE_STORAGE_BUCKET', 'storageBucket'),
  messagingSenderId: envValue('VITE_FIREBASE_MESSAGING_SENDER_ID', 'messagingSenderId'),
  appId: envValue('VITE_FIREBASE_APP_ID', 'appId'),
  measurementId: envValue('VITE_FIREBASE_MEASUREMENT_ID', 'measurementId'),
});

export function hasFirebaseConfig(config = firebaseConfig) {
  return Boolean(config.apiKey && config.authDomain && config.projectId && config.appId);
}
