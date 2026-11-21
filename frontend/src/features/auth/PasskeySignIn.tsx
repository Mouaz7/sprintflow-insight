import { useMutation } from '@tanstack/react-query';
import type { User } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import {
  authenticatePasskey,
  usePasskeysAvailable,
} from '../../shared/lib/passkeys';
import { Notice } from '../../shared/ui/ui';
import { authText } from './i18n';

export default function PasskeySignIn({
  onSignedIn,
}: {
  onSignedIn: (user: User) => Promise<void>;
}) {
  const { language } = useLanguage();
  const t = useT(authText);
  const available = usePasskeysAvailable();
  const signIn = useMutation({
    mutationFn: authenticatePasskey<User>,
    onSuccess: onSignedIn,
  });
  if (!available) return null;
  return (
    <div className="editor-form">
      <button
        type="button"
        className="secondary-action"
        disabled={signIn.isPending}
        onClick={() => signIn.mutate()}
      >
        {t('signInPasskey')}
      </button>
      <Notice error={localizeMessage(signIn.error?.message ?? '', language)} />
    </div>
  );
}
