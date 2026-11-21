/// <reference types="vite/client" />

const workerPath = '/sw.js';

type TrustedScriptPolicyFactory = {
  createPolicy(
    name: string,
    rules: { createScriptURL: (url: string) => string },
  ): { createScriptURL: (url: string) => unknown };
};

export async function registerOfflineWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  const factory = (
    window as unknown as { trustedTypes?: TrustedScriptPolicyFactory }
  ).trustedTypes;
  const url = factory
    ? factory
        .createPolicy('sprintflow-pwa', {
          createScriptURL: (candidate) => {
            if (candidate !== workerPath)
              throw new TypeError('Unexpected offline worker URL.');
            return candidate;
          },
        })
        .createScriptURL(workerPath)
    : workerPath;
  await navigator.serviceWorker.register(url as string, {
    scope: '/',
    updateViaCache: 'none',
  });
}
