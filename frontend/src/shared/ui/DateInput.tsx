import { useRef } from 'react';
import {
  MAX_DATE,
  MIN_DATE,
  normalizeDate,
  validDate,
} from '../lib/formValues';
import { useT } from '../i18n/LanguageProvider';
import { uiText } from './i18n';
import { InterfaceIcon } from './InterfaceIcons';

export function DateInput({
  value,
  onChange,
  required,
  min = MIN_DATE,
  max = MAX_DATE,
  onBlur,
  ariaLabel,
}: {
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  min?: string;
  max?: string;
  onBlur?: () => void;
  ariaLabel?: string;
}) {
  const picker = useRef<HTMLInputElement>(null);
  const text = useRef<HTMLInputElement>(null);
  const t = useT(uiText);
  const change = (next: string) => {
    text.current?.setCustomValidity('');
    onChange(next);
  };
  return (
    <span className="date-input-control">
      <input
        ref={text}
        type="text"
        aria-label={ariaLabel}
        inputMode="numeric"
        placeholder={t('datePlaceholder')}
        maxLength={10}
        pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
        required={required}
        value={value}
        onBlur={onBlur}
        onChange={(event) => {
          if (/^[0-9-]*$/.test(event.target.value))
            change(normalizeDate(event.target.value));
        }}
      />
      <input
        ref={picker}
        className="date-input-source"
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        min={min}
        max={max}
        value={validDate(value) ? value : ''}
        onChange={(event) => change(event.target.value)}
        onInvalid={(event) => {
          event.preventDefault();
          const input = text.current;
          if (input) {
            input.setCustomValidity(event.currentTarget.validationMessage);
            input.reportValidity();
          }
        }}
      />
      <button
        type="button"
        className="date-input-picker"
        aria-label={t('chooseDate')}
        onClick={() => {
          const input = picker.current;
          if (input?.showPicker) input.showPicker();
          else input?.click();
        }}
      >
        <InterfaceIcon name="calendar" />
      </button>
    </span>
  );
}
