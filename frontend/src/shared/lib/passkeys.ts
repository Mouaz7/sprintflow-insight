import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';

type Settings = { available: boolean; origin: string | null };
const config = queryOptions({
  queryKey: ['passkeys', 'config'],
  queryFn: () => api<Settings>('/auth/passkeys/config'),
});

export function usePasskeysAvailable() {
  const supported =
    window.isSecureContext &&
    typeof PublicKeyCredential !== 'undefined' &&
    typeof PublicKeyCredential.parseCreationOptionsFromJSON === 'function' &&
    typeof PublicKeyCredential.parseRequestOptionsFromJSON === 'function';
  const result = useQuery({ ...config, enabled: supported });
  return (
    result.data?.available && result.data.origin === window.location.origin
  );
}

export async function createPasskey(label: string) {
  const options = await api<PublicKeyCredentialCreationOptionsJSON>(
    '/auth/passkeys/registration/options',
    { method: 'POST' },
  );
  const credential = await navigator.credentials.create({
    publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(options),
  });
  if (!(credential instanceof PublicKeyCredential))
    throw new Error('The passkey request was cancelled.');
  return api('/auth/passkeys/registration/complete', {
    method: 'POST',
    body: JSON.stringify({ label, credential: credential.toJSON() }),
  });
}

export async function authenticatePasskey<T>() {
  const options = await api<PublicKeyCredentialRequestOptionsJSON>(
    '/auth/passkeys/login/options',
    { method: 'POST' },
  );
  const credential = await navigator.credentials.get({
    publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(options),
  });
  if (!(credential instanceof PublicKeyCredential))
    throw new Error('The passkey request was cancelled.');
  return api<T>('/auth/passkeys/login/complete', {
    method: 'POST',
    body: JSON.stringify({ credential: credential.toJSON() }),
  });
}
