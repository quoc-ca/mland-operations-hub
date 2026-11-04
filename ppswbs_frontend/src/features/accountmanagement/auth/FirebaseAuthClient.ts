import { initializeApp, getApps, getApp } from 'firebase/app';
import {
  getAuth,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signOut as fbSignOut,
  sendEmailVerification,
  sendPasswordResetEmail,
  onAuthStateChanged as fbOnAuthStateChanged,
  updatePassword as fbUpdatePassword,
  verifyBeforeUpdateEmail as fbVerifyBeforeUpdateEmail,
  GoogleAuthProvider,
  signInWithRedirect as fbSignInWithRedirect,
  getRedirectResult as fbGetRedirectResult,
  EmailAuthProvider,
  reauthenticateWithCredential as fbReauthenticateWithCredential,
  User,
  Auth,
} from 'firebase/auth';
import { firebaseConfig, hasFirebaseConfig } from '../../../shared/auth/firebaseConfig';

/**
 * FirebaseAuthClient provides client-side authentication operations.
 * Per Principle II & FE-001 spec:
 * - Firebase proves identity only; backend determines Account role, status, and authority.
 * - Never makes role/permission decisions.
 * - No provider display name is used as an identity fact.
 * - Never stores or logs raw passwords or ID tokens.
 */
class FirebaseAuthClient {
  private authInstance: Auth | null = null;

  private getAuth(): Auth | null {
    if (this.authInstance) return this.authInstance;
    if (!hasFirebaseConfig()) return null;

    const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
    this.authInstance = getAuth(app);
    return this.authInstance;
  }

  private requireAuth(): Auth {
    const auth = this.getAuth();
    if (!auth) {
      const error: any = new Error('Firebase configuration is missing or incomplete');
      error.code = 'CONFIGURATION_REQUIRED';
      throw error;
    }
    return auth;
  }

  public isConfigured(): boolean {
    return hasFirebaseConfig();
  }

  public getCurrentUser(): User | null {
    return this.getAuth()?.currentUser ?? null;
  }

  public async getIdToken(forceRefresh: boolean = false): Promise<string | null> {
    const user = this.getCurrentUser();
    if (!user || typeof user.getIdToken !== 'function') return null;
    try {
      return await user.getIdToken(forceRefresh);
    } catch (err: any) {
      console.warn('Firebase getIdToken failed, clearing session:', err?.message || err);
      try {
        await this.signOut();
      } catch {
        // ignore sign out error
      }
      return null;
    }
  }

  public async register(email: string, password: string):Promise<User> {
    const auth = this.requireAuth();
    const cred = await createUserWithEmailAndPassword(auth, email.trim(), password);
    return cred.user;
  }

  public async signIn(email: string, password: string): Promise<User> {
    const auth = this.requireAuth();
    const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
    return cred.user;
  }

  public async signOut(): Promise<void> {
    const auth = this.getAuth();
    if (auth) {
      await fbSignOut(auth);
    }
  }

  public async sendVerificationEmail(): Promise<void> {
    const user = this.getCurrentUser();
    if (!user) {
      const error: any = new Error('No authenticated user found to verify');
      error.code = 'AUTHENTICATION_REQUIRED';
      throw error;
    }
    await sendEmailVerification(user);
  }

  public async sendPasswordReset(email: string): Promise<void> {
    const auth = this.requireAuth();
    await sendPasswordResetEmail(auth, email.trim());
  }

  public async reloadUser(): Promise<void> {
    const user = this.getCurrentUser();
    if (user && typeof user.reload === 'function') {
      try {
        await user.reload();
      } catch (err: any) {
        console.warn('Firebase reloadUser failed:', err?.message || err);
        if (
          err?.code === 'auth/user-not-found' ||
          err?.code === 'auth/user-token-expired' ||
          err?.code === 'auth/invalid-user-token' ||
          err?.message?.includes('400') ||
          err?.message?.includes('INVALID_REFRESH_TOKEN')
        ) {
          try {
            await this.signOut();
          } catch {
            // ignore
          }
        }
        throw err;
      }
    }
  }

  public async verifyBeforeUpdateEmail(newEmail: string): Promise<void> {
    const user = this.getCurrentUser();
    if (!user) {
      const error: any = new Error('No authenticated user found to update email');
      error.code = 'AUTHENTICATION_REQUIRED';
      throw error;
    }
    await fbVerifyBeforeUpdateEmail(user, newEmail.trim());
  }

  public async updatePassword(newPassword: string): Promise<void> {
    const user = this.getCurrentUser();
    if (!user) {
      const error: any = new Error('No authenticated user found to update password');
      error.code = 'AUTHENTICATION_REQUIRED';
      throw error;
    }
    await fbUpdatePassword(user, newPassword);
  }

  public async signInWithGoogleRedirect(): Promise<void> {
    const auth = this.requireAuth();
    const provider = new GoogleAuthProvider();
    await fbSignInWithRedirect(auth, provider);
  }

  public async getRedirectAuthResult(): Promise<User | null> {
    const auth = this.getAuth();
    if (!auth) return null;
    const cred = await fbGetRedirectResult(auth);
    return cred ? cred.user : null;
  }

  public async reauthenticateWithCredential(password: string): Promise<void> {
    const user = this.getCurrentUser();
    if (!user || !user.email) {
      const error: any = new Error('No authenticated user with email found to re-authenticate');
      error.code = 'AUTHENTICATION_REQUIRED';
      throw error;
    }
    const credential = EmailAuthProvider.credential(user.email, password);
    await fbReauthenticateWithCredential(user, credential);
  }

  public hasPasswordProvider(): boolean {
    const user = this.getCurrentUser();
    if (!user || !Array.isArray(user.providerData)) return false;
    return user.providerData.some(p => p.providerId === 'password');
  }

  public onAuthStateChanged(callback: (user: User | null) => void): () => void {
    const auth = this.getAuth();
    if (!auth) {
      callback(null);
      return () => {};
    }
    return fbOnAuthStateChanged(auth, callback);
  }
}

export const firebaseAuthClient = new FirebaseAuthClient();
export default firebaseAuthClient;

