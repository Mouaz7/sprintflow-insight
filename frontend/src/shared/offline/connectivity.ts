import { useSyncExternalStore } from 'react';

function listen(listener: () => void) {
  window.addEventListener('online', listener);
  window.addEventListener('offline', listener);
  return () => {
    window.removeEventListener('online', listener);
    window.removeEventListener('offline', listener);
  };
}

export function useIsOnline() {
  return useSyncExternalStore(
    listen,
    () => navigator.onLine,
    () => true,
  );
}
