import messages, { type IcuArguments } from './icu.generated.js';
import type { Formatter } from './lexicon';

/** Precompiled ICU messages: no parser, eval or Function constructor in the browser. */
export function icu<K extends keyof IcuArguments>(key: K) {
  return (args: IcuArguments[K], format: Formatter): string =>
    messages[format.language][key](args);
}
