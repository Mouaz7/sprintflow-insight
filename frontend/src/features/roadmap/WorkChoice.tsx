import { useId, useState } from 'react';
import { useT } from '../../shared/i18n/LanguageProvider';
import { roadmapText } from './i18n';

/** A bounded native select stays usable even when the backlog has thousands of items. */
export function WorkChoice({
  label,
  items,
  value,
  onChange,
}: {
  label: string;
  items: { id: number; title: string; key?: string | undefined }[];
  value: number | undefined;
  onChange: (id: number) => void;
}) {
  const t = useT(roadmapText);
  const helpId = useId();
  const [filter, setFilter] = useState('');
  const matches = items
    .filter((item) =>
      `${item.key ?? ''} ${item.title}`
        .toLocaleLowerCase()
        .includes(filter.toLocaleLowerCase()),
    )
    .slice(0, 50);
  const selected = items.find((item) => item.id === value);
  if (selected && !matches.some((item) => item.id === selected.id))
    matches.unshift(selected);
  return (
    <fieldset className="roadmap-choice">
      <legend>{label}</legend>
      <label>
        {t('filter')}
        <input
          type="search"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        />
      </label>
      <label>
        {label}
        <select
          required
          aria-describedby={helpId}
          value={value ?? ''}
          onChange={(event) => onChange(Number(event.target.value))}
        >
          <option value="" disabled>
            —
          </option>
          {matches.map((item) => (
            <option key={item.id} value={item.id}>
              {item.key ? `${item.key} · ` : ''}
              {item.title}
            </option>
          ))}
        </select>
      </label>
      <small id={helpId}>{t('choiceHint')}</small>
    </fieldset>
  );
}
