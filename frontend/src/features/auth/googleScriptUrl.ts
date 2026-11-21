const GOOGLE_CLIENT = 'https://accounts.google.com/gsi/client';

type ScriptUrlPolicy = { createScriptURL: (value: string) => unknown };
type TrustedTypesFactory = {
  createPolicy: (
    name: string,
    rules: { createScriptURL: (value: string) => string },
  ) => ScriptUrlPolicy;
};
const policies = new WeakMap<TrustedTypesFactory, ScriptUrlPolicy>();

/** The Google script-URL policy accepts exactly the official live GIS loader. */
export function googleScriptUrl(): string {
  const factory = (window as unknown as { trustedTypes?: TrustedTypesFactory })
    .trustedTypes;
  if (!factory) return GOOGLE_CLIENT;
  let policy = policies.get(factory);
  if (!policy) {
    policy = factory.createPolicy('sprintflow-google', {
      createScriptURL: (value) => {
        if (value !== GOOGLE_CLIENT)
          throw new TypeError('Unapproved Google script URL.');
        return value;
      },
    });
    policies.set(factory, policy);
  }
  // DOM typings omit TrustedScriptURL; the browser receives the actual branded object.
  return policy.createScriptURL(GOOGLE_CLIENT) as string;
}
