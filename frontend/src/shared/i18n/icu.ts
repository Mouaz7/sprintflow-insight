import messages from './icu.generated';
import type { IcuArguments } from './icu.types';
import type { Formatter } from './lexicon';

/** Precompiled ICU messages: no parser, eval or Function constructor in the browser. */
export function icu<K extends keyof IcuArguments>(key: K) {
  return (args: IcuArguments[K], format: Formatter): string =>
    messages[format.language][key](args);
}
