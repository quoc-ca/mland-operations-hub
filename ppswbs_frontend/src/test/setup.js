import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

const createStorageMock = () => {
  let store = {};
  return {
    getItem: (key) => (key in store ? store[key] : null),
    setItem: (key, value) => {
      store[key] = String(value);
    },
    removeItem: (key) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    get length() {
      return Object.keys(store).length;
    },
    key: (i) => Object.keys(store)[i] ?? null,
  };
};

if (typeof window !== 'undefined') {
  try {
    if (!window.localStorage || typeof window.localStorage.clear !== 'function') {
      const mock = createStorageMock();
      Object.defineProperty(window, 'localStorage', { value: mock, writable: true, configurable: true });
      Object.defineProperty(globalThis, 'localStorage', { value: mock, writable: true, configurable: true });
    }
  } catch {
    const mock = createStorageMock();
    Object.defineProperty(window, 'localStorage', { value: mock, writable: true, configurable: true });
    Object.defineProperty(globalThis, 'localStorage', { value: mock, writable: true, configurable: true });
  }
}

afterEach(() => {
  cleanup();
  try {
    window.localStorage.clear();
  } catch {
    /* storage may be unavailable */
  }
});
