// Loaded by index.html before the application bundle, so the document has the
// remembered language and theme before React renders. External module only:
// the CSP allows scripts by nonce and never inline.
import {
  applyLanguage,
  applyTheme,
  storedLanguage,
  storedTheme,
} from './shared/lib/appearance';

applyLanguage(storedLanguage(), false);
applyTheme(storedTheme(), false);
