import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
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
    sv,
    language,
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
          <h2 id="profile-details-title">
            {sv ? 'Personliga uppgifter' : 'Personal details'}
          </h2>
          <p>
            {sv
              ? 'Så här ser ditt team dig i SprintFlow.'
              : 'How your team sees you in SprintFlow.'}
          </p>
        </div>
      </div>
      <fieldset className="profile-fields" disabled={saving}>
        <legend className="sr-only">
          {sv ? 'Profiluppgifter' : 'Profile details'}
        </legend>
        <FormGuide topic="profile" language={language} />
        <div className="profile-photo-row">
          <div className="profile-avatar">
            {avatarImage ? (
              <img
                src={avatarImage}
                alt={
                  sv ? 'Förhandsvisning av profilbild' : 'Profile photo preview'
                }
              />
            ) : (
              (displayName.trim() || user.email).slice(0, 1).toUpperCase()
            )}
          </div>
          <div className="profile-photo-controls">
            <strong>{sv ? 'Profilbild' : 'Profile photo'}</strong>
            <div className="profile-photo-actions">
              <button
                type="button"
                className="secondary-action"
                disabled={readingImage}
                onClick={() => fileInput.current?.click()}
              >
                {readingImage
                  ? sv
                    ? 'Läser bild…'
                    : 'Reading photo…'
                  : sv
                    ? 'Ändra bild'
                    : 'Change photo'}
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
                  {sv ? 'Ta bort bild' : 'Remove photo'}
                </button>
              )}
            </div>
            <small>
              {sv
                ? 'PNG, JPEG, WEBP eller GIF · under 750 kB'
                : 'PNG, JPEG, WEBP or GIF · under 750 KB'}
            </small>
            <input
              ref={fileInput}
              type="file"
              hidden
              aria-label={sv ? 'Välj profilbild' : 'Choose profile photo'}
              accept="image/png,image/jpeg,image/webp,image/gif"
              onChange={selectImage}
            />
          </div>
        </div>
        <div className="profile-field-row">
          <label>
            {sv ? 'Namn' : 'Name'}
            <input
              required
              maxLength={80}
              autoComplete="name"
              value={displayName}
              onChange={(event) => {
                setDisplayName(event.target.value);
                clearFeedback();
              }}
              placeholder={fieldExample('name', language)}
            />
          </label>
          <label>
            {sv ? 'Roll' : 'Role'}
            <input
              maxLength={80}
              autoComplete="organization-title"
              value={jobTitle}
              onChange={(event) => {
                setJobTitle(event.target.value);
                clearFeedback();
              }}
              placeholder={fieldExample('jobTitle', language)}
            />
          </label>
        </div>
        <label>
          {sv ? 'E-post' : 'Email'}
          <input
            type="email"
            value={user.email}
            readOnly
            aria-describedby="profile-email-help"
          />
        </label>
        <p id="profile-email-help" className="profile-field-help">
          {sv
            ? 'Din e-postadress används för inloggning och kan inte ändras här.'
            : 'Your email is used to sign in and cannot be changed here.'}
        </p>
        <label>
          {sv ? 'Om dig' : 'About you'}
          <textarea
            aria-label={sv ? 'Om dig' : 'About you'}
            maxLength={500}
            rows={4}
            value={bio}
            onChange={(event) => {
              setBio(event.target.value);
              clearFeedback();
            }}
            placeholder={fieldExample('bio', language)}
            aria-describedby="profile-bio-count"
          />
        </label>
        <span id="profile-bio-count" className="profile-character-count">
          {bio.length} / 500
        </span>
      </fieldset>
      <Feedback sv={sv} error={error} message={message} />
      <div className="profile-card-footer">
        <span className="profile-save-hint">
          {dirty
            ? sv
              ? 'Du har osparade ändringar'
              : 'You have unsaved changes'
            : sv
              ? 'Din profil är uppdaterad'
              : 'Your profile is up to date'}
        </span>
        <div className="profile-form-actions">
          {dirty && (
            <button
              type="button"
              className="profile-text-button"
              disabled={saving || readingImage}
              onClick={discardChanges}
            >
              {sv ? 'Ångra ändringar' : 'Discard changes'}
            </button>
          )}
          <button
            className="primary-action"
            disabled={saving || readingImage || !dirty}
          >
            {saving
              ? sv
                ? 'Sparar…'
                : 'Saving…'
              : sv
                ? 'Spara profil'
                : 'Save profile'}
          </button>
        </div>
      </div>
    </form>
  );
}
