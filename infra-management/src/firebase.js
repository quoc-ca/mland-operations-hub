import { initializeApp, getApps, getApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth'

const env = import.meta.env

export const firebaseConfig = {
  apiKey: env.VITE_FIREBASE_API_KEY || env.apiKey || '',
  authDomain: env.VITE_FIREBASE_AUTH_DOMAIN || env.authDomain || '',
  projectId: env.VITE_FIREBASE_PROJECT_ID || env.projectId || '',
  storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET || env.storageBucket || '',
  messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID || env.messagingSenderId || '',
  appId: env.VITE_FIREBASE_APP_ID || env.appId || '',
  measurementId: env.VITE_FIREBASE_MEASUREMENT_ID || env.measurementId || '',
}

export function isFirebaseConfigured() {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId && firebaseConfig.appId)
}

const app = isFirebaseConfigured()
  ? (getApps().length > 0 ? getApp() : initializeApp(firebaseConfig))
  : null

export const auth = app ? getAuth(app) : null
export const googleProvider = new GoogleAuthProvider()
googleProvider.setCustomParameters({ prompt: 'select_account' })

export async function loginWithGoogle() {
  if (!auth) {
    throw new Error('Cấu hình Firebase chưa đầy đủ trong file .env. Vui lòng kiểm tra lại VITE_FIREBASE_API_KEY.')
  }
  const result = await signInWithPopup(auth, googleProvider)
  return result.user
}

export async function logoutFirebase() {
  if (auth) {
    await signOut(auth)
  }
}
