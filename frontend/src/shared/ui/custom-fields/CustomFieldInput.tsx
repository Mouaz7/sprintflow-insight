import { useId } from 'react';
import type {
  CustomFieldDefinition,
  ProjectMember,
} from '../../../api/contracts';
import { useT } from '../../i18n/LanguageProvider';
import { DateInput } from '../DateInput';
import { customFieldText } from './i18n';

/** Strings preserve exact decimals and membership identities throughout editing. */
export function CustomFieldInput({
  field,
  value,
  onChange,
  members,
  disabled = false,
  label,
  maxLength = 2000,
}: {
  field: CustomFieldDefinition;
  value: string;
  onChange: (value: string) => void;
  members: ProjectMember[];
  disabled?: boolean;
  label?: string;
  maxLength?: number;
}) {
  const t = useT(customFieldText);
  const hintId = useId();
  const name = label ?? field.name;
  const options =
    field.kind === 'USER'
      ? members.map((member) => ({
          value: String(member.id),
          label: member.email,
        }))
      : field.options.map((option) => ({ value: option, label: option }));
  return (
    <label className="custom-field-input">
      <span>{name}</span>
      {field.kind === 'TEXT' ? (
        <textarea
          aria-label={name}
          value={value}
          maxLength={maxLength}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          placeholder={t('textExample')}
          rows={2}
        />
      ) : field.kind === 'NUMBER' ? (
        <input
          aria-label={name}
          aria-describedby={hintId}
          value={value}
          type="text"
          inputMode="decimal"
          maxLength={maxLength}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value.replace(',', '.'))}
          placeholder={t('numberExample')}
        />
      ) : field.kind === 'DATE' ? (
        <DateInput
          value={value}
          ariaLabel={name}
          onChange={onChange}
          disabled={disabled}
        />
      ) : (
        <select
          aria-label={name}
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        >
          <option value="">{t('unset')}</option>
          {value && !options.some((option) => option.value === value) && (
            <option value={value} disabled>
              {t('unavailable')}
            </option>
          )}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
      {field.kind === 'NUMBER' && <small id={hintId}>{t('numberHint')}</small>}
    </label>
  );
}
