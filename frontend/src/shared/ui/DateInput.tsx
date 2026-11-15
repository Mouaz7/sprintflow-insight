import { useRef } from 'react';
import type { Language } from '../../api/contracts';
import {
  MAX_DATE,
  MIN_DATE,
  normalizeDate,
  validDate,
} from '../lib/formValues';
import { InterfaceIcon } from './InterfaceIcons';

export function DateInput({
  value,
  onChange,
  language,
  required,
  min = MIN_DATE,
  max = MAX_DATE,
  onBlur,
}: {
  value: string;
  onChange: (value: string) => void;
  language: Language;
  required?: boolean;
  min?: string;
  max?: string;
  onBlur?: () => void;
}) {
  const picker = useRef<HTMLInputElement>(null);
  return (
    <span className="date-input-control">
      <input
        type="text"
        inputMode="numeric"
        placeholder={language === 'SV' ? 'ÅÅÅÅ-MM-DD' : 'YYYY-MM-DD'}
        maxLength={10}
        pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
        required={required}
        value={value}
        onBlur={onBlur}
        onChange={(event) => {
          if (/^[0-9-]*$/.test(event.target.value))
            onChange(normalizeDate(event.target.value));
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
        onChange={(event) => onChange(event.target.value)}
      />
      <button
        type="button"
        className="date-input-picker"
        aria-label={
          language === 'SV'
            ? 'Välj datum i kalender'
            : 'Choose date from calendar'
        }
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
