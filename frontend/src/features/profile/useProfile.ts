import type { ChangeEvent, FormEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { User } from '../../api/contracts';
type ProfileProps = {
  user: User;
  language: 'EN' | 'SV';
  onSaved: (user: User) => void;
  onLogout: () => Promise<void>;
  onAccountDeleted: () => void;
  onAppearance?:
    ((language: 'EN' | 'SV', theme: 'light' | 'dark') => void) | undefined;
  onReplay?: (() => void) | undefined;
};
export type ProfileViewProps = ProfileProps;
export function useProfile({
  user,
  language,
  onSaved,
  onLogout,
  onAccountDeleted,
  onAppearance,
  onReplay,
}: ProfileViewProps) {
  const sv = language === 'SV';
  const [tab, setTab] = useState<'details' | 'security' | 'settings'>(
    'details',
  );
  const [displayName, setDisplayName] = useState(user.displayName || '');
  const [jobTitle, setJobTitle] = useState(user.jobTitle || '');
  const [bio, setBio] = useState(user.bio || '');
  const [avatarImage, setAvatarImage] = useState(user.avatarImage || '');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [readingImage, setReadingImage] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const imageReader = useRef<FileReader | null>(null);
  const dirty =
    displayName !== (user.displayName || '') ||
    jobTitle !== (user.jobTitle || '') ||
    bio !== (user.bio || '') ||
    avatarImage !== (user.avatarImage || '');

  // When the saved account changes, the form follows it (adjusted while
  // rendering instead of in an effect).
  const savedUser = [
    user.displayName,
    user.jobTitle,
    user.bio,
    user.avatarImage,
  ].join('\u0000');
  const [syncedUser, setSyncedUser] = useState(savedUser);
  if (syncedUser !== savedUser) {
    setSyncedUser(savedUser);
    setDisplayName(user.displayName || '');
    setJobTitle(user.jobTitle || '');
    setBio(user.bio || '');
    setAvatarImage(user.avatarImage || '');
  }

  useEffect(
    () => () => {
      if (imageReader.current) {
        imageReader.current.onload = null;
        imageReader.current.onerror = null;
        imageReader.current.onabort = null;
        imageReader.current.abort();
      }
    },
    [],
  );

  function clearFeedback() {
    setMessage('');
    setError('');
  }
  function discardChanges() {
    setDisplayName(user.displayName || '');
    setJobTitle(user.jobTitle || '');
    setBio(user.bio || '');
    setAvatarImage(user.avatarImage || '');
    clearFeedback();
  }

  function selectImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    clearFeedback();
    const invalidImage = sv
      ? 'Välj PNG, JPEG, WEBP eller GIF under 750 kB.'
      : 'Choose a PNG, JPEG, WEBP or GIF under 750 KB.';
    if (
      !['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(
        file.type,
      ) ||
      file.size > 750_000
    ) {
      setError(invalidImage);
      return;
    }
    imageReader.current?.abort();
    const reader = new FileReader();
    imageReader.current = reader;
    setReadingImage(true);
    reader.onload = () => {
      const result = String(reader.result);
      if (result.length > 1_000_000) setError(invalidImage);
      else setAvatarImage(result);
      setReadingImage(false);
    };
    reader.onerror = () => {
      setReadingImage(false);
      setError(
        sv
          ? 'Bilden kunde inte läsas. Välj en annan bild.'
          : 'We couldn’t read this photo. Choose another image.',
      );
    };
    reader.onabort = () => setReadingImage(false);
    reader.readAsDataURL(file);
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    if (saving || readingImage || !dirty) return;
    if (!displayName.trim()) {
      setError(sv ? 'Ange ditt namn.' : 'Enter your name.');
      return;
    }
    setSaving(true);
    clearFeedback();
    try {
      const updated = await api<User>('/auth/profile', {
        method: 'PUT',
        body: JSON.stringify({
          currentEmail: user.email,
          email: user.email,
          displayName: displayName.trim(),
          jobTitle: jobTitle.trim(),
          bio,
          avatarImage,
          recoveryCode: null,
        }),
      });
      onSaved(updated);
      setMessage(sv ? 'Profilen har sparats.' : 'Profile saved.');
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Profilen kunde inte sparas. Försök igen.'
            : 'Could not save your profile. Please try again.',
      );
    } finally {
      setSaving(false);
    }
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    setLogoutError('');
    try {
      await onLogout();
    } catch (cause) {
      setLogoutError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Det gick inte att logga ut. Försök igen.'
            : 'Could not log out. Please try again.',
      );
      setLoggingOut(false);
    }
  }

  return {
    user,
    language,
    onSaved,
    onLogout,
    onAccountDeleted,
    onAppearance,
    onReplay,
    sv,
    tab,
    setTab,
    displayName,
    setDisplayName,
    jobTitle,
    setJobTitle,
    bio,
    setBio,
    avatarImage,
    setAvatarImage,
    message,
    setMessage,
    error,
    setError,
    saving,
    setSaving,
    readingImage,
    setReadingImage,
    loggingOut,
    setLoggingOut,
    logoutError,
    setLogoutError,
    fileInput,
    imageReader,
    dirty,
    clearFeedback,
    discardChanges,
    selectImage,
    saveProfile,
    logout,
  };
}
export type UseProfileState = ReturnType<typeof useProfile>;
