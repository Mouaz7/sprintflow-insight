import React from 'react';
import { onlineManager } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './styles.css';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Missing #root element');
onlineManager.setOnline(navigator.onLine);
window.addEventListener(
  'load',
  () => {
    void import('./shared/offline/registerWorker')
      .then(({ registerOfflineWorker }) => registerOfflineWorker())
      .catch(() => undefined);
  },
  { once: true },
);

createRoot(rootElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
