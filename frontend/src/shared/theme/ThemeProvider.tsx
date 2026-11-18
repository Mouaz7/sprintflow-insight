import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { applyTheme, storedTheme, type Theme } from '../lib/appearance';

type ThemeState = { theme: Theme; setTheme: (theme: Theme) => void };

const ThemeContext = createContext<ThemeState>({
  theme: 'light',
  setTheme: () => {},
});

/** The colour theme, remembered in this browser and set on `<html data-theme>`. */
export function ThemeProvider({
  children,
  initial,
}: {
  children: ReactNode;
  initial?: Theme | undefined;
}) {
  const [theme, setTheme] = useState<Theme>(() => initial ?? storedTheme());
  useEffect(() => applyTheme(theme), [theme]);
  return <ThemeContext value={{ theme, setTheme }}>{children}</ThemeContext>;
}

export function useTheme(): ThemeState {
  return useContext(ThemeContext);
}
