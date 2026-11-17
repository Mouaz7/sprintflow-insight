import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import type { Language } from '../api/contracts';
import { createQueryClient } from '../api/queries';
import { LanguageProvider } from '../shared/i18n/LanguageProvider';
import { ThemeProvider } from '../shared/theme/ThemeProvider';

export type ProviderOptions = {
  language?: Language | undefined;
  path?: string | undefined;
};

/** The application providers around a component under test. */
export function TestProviders({
  children,
  language,
  path = '/',
  client,
}: ProviderOptions & { children: ReactNode; client: QueryClient }) {
  return (
    <QueryClientProvider client={client}>
      <LanguageProvider initial={language}>
        <ThemeProvider>
          <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
        </ThemeProvider>
      </LanguageProvider>
    </QueryClientProvider>
  );
}

export function renderWithProviders(
  ui: ReactElement,
  { language, path, ...options }: ProviderOptions & RenderOptions = {},
) {
  const client = createQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <TestProviders language={language} path={path ?? '/'} client={client}>
      {children}
    </TestProviders>
  );
  return { client, ...render(ui, { wrapper, ...options }) };
}
