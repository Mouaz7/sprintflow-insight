import { useT } from '../../shared/i18n/LanguageProvider';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { profileText } from './i18n';
import { Feedback, SettingsIcon } from './ProfileFeedback';
import type { UseProfileState } from './useProfile';
export function ProfileDetails({
  controller,
}: {
  controller: UseProfileState;
}) {
  const {
    saveProfile,
    saving,
    readingImage,
    avatarImage,
    displayName,
    user,
    fileInput,
    setAvatarImage,
    clearFeedback,
    selectImage,
    setDisplayName,
    jobTitle,
    setJobTitle,
    bio,
    setBio,
    error,
    message,
    dirty,
    discardChanges,
  } = controller;
  const t = useT(profileText);
  const fieldExample = useFieldExample();
  return (
    <form
      className="profile-card profile-details-card"
      onSubmit={saveProfile}
      aria-labelledby="profile-details-title"
      aria-busy={saving || readingImage}
    >
      <div className="profile-card-heading">
        <span className="profile-card-icon">
          <SettingsIcon kind="person" />
        </span>
        <div>
          <h2 id="profile-details-title">{t('tabDetails')}</h2>
          <p>{t('detailsHelp')}</p>
        </div>
      </div>
      <fieldset className="profile-fields" disabled={saving}>
        <legend className="sr-only">{t('profileDetails')}</legend>
        <FormGuide topic="profile" />
        <div className="profile-photo-row">
          <div className="profile-avatar">
            {avatarImage ? (
              <img src={avatarImage} alt={t('photoPreview')} />
            ) : (
              (displayName.trim() || user.email).slice(0, 1).toUpperCase()
            )}
          </div>
          <div className="profile-photo-controls">
            <strong>{t('photo')}</strong>
            <div className="profile-photo-actions">
              <button
                type="button"
                className="secondary-action"
                disabled={readingImage}
                onClick={() => fileInput.current?.click()}
              >
                {t(readingImage ? 'readingPhoto' : 'changePhoto')}
              </button>
              {avatarImage && (
                <button
                  type="button"
                  className="profile-text-button"
                  disabled={readingImage}
                  onClick={() => {
                    setAvatarImage('');
                    clearFeedback();
                  }}
                >
                  {t('removePhoto')}
                </button>
              )}
            </div>
            <small>{t('photoFormats')}</small>
            <input
              ref={fileInput}
              type="file"
              hidden
              aria-label={t('choosePhoto')}
              accept="image/png,image/jpeg,image/webp,image/gif"
              onChange={selectImage}
            />
          </div>
        </div>
        <div className="profile-field-row">
          <label>
            {t('name')}
            <input
              required
              maxLength={80}
              autoComplete="name"
              value={displayName}
              onChange={(event) => {
                setDisplayName(event.target.value);
                clearFeedback();
              }}
              placeholder={fieldExample('name')}
            />
          </label>
          <label>
            {t('role')}
            <input
              maxLength={80}
              autoComplete="organization-title"
              value={jobTitle}
              onChange={(event) => {
                setJobTitle(event.target.value);
                clearFeedback();
              }}
              placeholder={fieldExample('jobTitle')}
            />
          </label>
        </div>
        <label>
          {t('email')}
          <input
            type="email"
            value={user.email}
            readOnly
            aria-describedby="profile-email-help"
          />
        </label>
        <p id="profile-email-help" className="profile-field-help">
          {t('emailHelp')}
        </p>
        <label>
          {t('aboutYou')}
          <textarea
            aria-label={t('aboutYou')}
            maxLength={500}
            rows={4}
            value={bio}
            onChange={(event) => {
              setBio(event.target.value);
              clearFeedback();
            }}
            placeholder={fieldExample('bio')}
            aria-describedby="profile-bio-count"
          />
        </label>
        <span id="profile-bio-count" className="profile-character-count">
          {bio.length} / 500
        </span>
      </fieldset>
      <Feedback error={error} message={message} />
      <div className="profile-card-footer">
        <span className="profile-save-hint">
          {t(dirty ? 'unsavedChanges' : 'upToDate')}
        </span>
        <div className="profile-form-actions">
          {dirty && (
            <button
              type="button"
              className="profile-text-button"
              disabled={saving || readingImage}
              onClick={discardChanges}
            >
              {t('discardChanges')}
            </button>
          )}
          <button
            className="primary-action"
            disabled={saving || readingImage || !dirty}
          >
            {t(saving ? 'saving' : 'saveProfile')}
          </button>
        </div>
      </div>
    </form>
  );
}
