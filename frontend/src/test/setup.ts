import '@testing-library/jest-dom/vitest';

// Components open no real WebSocket in tests; realtime tests inject their own.
Object.defineProperty(globalThis, 'WebSocket', {
  value: undefined,
  configurable: true,
  writable: true,
});
