import { describe, expect, it } from 'vitest';
import { afterSignInPath, roleHomePath, safeReturnTo } from './roleHome';

describe('role home', () => {
  it('sends Staff and Manager to their own home and everyone else to the general home', () => {
    expect(roleHomePath('STAFF')).toBe('/staff/home');
    expect(roleHomePath('MANAGER')).toBe('/manager/home');
    expect(roleHomePath('MEMBER')).toBe('/');
    expect(roleHomePath('ADMIN')).toBe('/');
    expect(roleHomePath(undefined)).toBe('/');
  });

  it('follows a returnTo only when it is a path on this site', () => {
    expect(safeReturnTo('/operations/sessions/abc')).toBe('/operations/sessions/abc');
    expect(safeReturnTo('https://evil.example/steal')).toBeNull();
    expect(safeReturnTo('//evil.example')).toBeNull();
    expect(safeReturnTo('/\\evil.example')).toBeNull();
    expect(safeReturnTo('javascript:alert(1)')).toBeNull();
    expect(safeReturnTo(null)).toBeNull();
  });

  it('prefers the page the user came from, else the role home', () => {
    expect(afterSignInPath('STAFF', '/settlement')).toBe('/settlement');
    expect(afterSignInPath('STAFF', 'https://evil.example')).toBe('/staff/home');
    expect(afterSignInPath('MANAGER', null)).toBe('/manager/home');
    expect(afterSignInPath('MEMBER', null)).toBe('/');
  });
});
