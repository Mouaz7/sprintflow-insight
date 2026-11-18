import { setupServer } from 'msw/node';

/**
 * One MSW server for every test file. Tests add handlers with `server.use`;
 * an unhandled `/api` request fails the test instead of reaching the network.
 */
export const server = setupServer();
