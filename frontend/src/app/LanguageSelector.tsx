import type { Language } from '../api/contracts';

export function LanguageSelector({
  language,
  onChange,
}: {
  language: Language;
  onChange: (language: Language) => void;
}) {
  return (
    <div className="language-selector">
      <svg
        className="language-symbol"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M3 12h18M12 3a16 16 0 0 1 0 18 16 16 0 0 1 0-18Z" />
      </svg>
      <select
        className="language-control"
        aria-label={language === 'SV' ? 'Språk' : 'Language'}
        title={
          language === 'SV' ? 'Byt språk: Svenska' : 'Change language: English'
        }
        value={language}
        onChange={(event) => onChange(event.target.value as Language)}
      >
        <option value="EN">EN</option>
        <option value="SV">SV</option>
      </select>
      <svg
        className="language-chevron"
        width="12"
        height="12"
        viewBox="0 0 12 12"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="m3 4.5 3 3 3-3" />
      </svg>
    </div>
  );
}
