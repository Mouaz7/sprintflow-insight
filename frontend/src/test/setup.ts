import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

// Views are lazy route chunks; their first import compiles through the React
// Compiler in Vitest, which can exceed the default one-second wait.
configure({ asyncUtilTimeout: 5000 });

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  window.history.replaceState(null, '', '/');
});
afterAll(() => server.close());
