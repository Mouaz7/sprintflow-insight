import { useMutation } from '@tanstack/react-query';
import { apiBlob } from '../../api/client';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { defineLexicon } from '../../shared/i18n/lexicon';
import { localizeMessage } from '../../shared/i18n/messages';
import { Notice } from '../../shared/ui/ui';
const text = defineLexicon({
  EN: {
    title: 'Export your account',
    help: 'Download a ZIP with JSON files for your profile, preferences, attributed contributions and content in projects you can currently access. Stored attachments are included. Passwords, session tokens and other participants’ hidden votes are excluded. Deleted projects and work without a recorded creator are limited as explained in the archive manifest.',
    download: 'Download account ZIP',
    busy: 'Preparing archive…',
    saved: 'Your account archive is ready. Keep it somewhere private.',
  },
  SV: {
    title: 'Exportera ditt konto',
    help: 'Hämta en ZIP med JSON-filer för din profil, inställningar, egna registrerade bidrag och innehåll i projekt du har tillgång till. Lagrade bilagor ingår. Lösenord, sessionstoken och andras dolda röster utelämnas. Borttagna projekt och arbete utan registrerad skapare omfattas enligt arkivets beskrivning.',
    download: 'Hämta konto som ZIP',
    busy: 'Förbereder arkivet…',
    saved: 'Ditt kontoarkiv är klart. Förvara det på en privat plats.',
  },
});
export function AccountExport() {
  const t = useT(text);
  const { language } = useLanguage();
  const mutation = useMutation({
    mutationFn: () => apiBlob('/account/export', { method: 'POST' }),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'sprintflow-account.zip';
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
  });
  return (
    <section className="profile-card">
      <h2>{t('title')}</h2>
      <p>{t('help')}</p>
      <button
        className="secondary-action"
        type="button"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        {t(mutation.isPending ? 'busy' : 'download')}
      </button>
      <Notice
        error={localizeMessage(mutation.error?.message ?? '', language)}
        success={mutation.isSuccess ? t('saved') : ''}
      />
    </section>
  );
}
