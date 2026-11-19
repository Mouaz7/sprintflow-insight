import { useId, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { membersQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { defineLexicon } from '../../shared/i18n/lexicon';
const text = defineLexicon({
  EN: {
    hint: 'Mention a project member with @name or @full@email. Use Down Arrow to enter suggestions.',
    suggestions: 'Mention suggestions',
    unavailable:
      'Could not load members. You can still type a full @email address.',
    tooLong: 'This mention would exceed the comment limit.',
    count: ({ count }: { count: number }, f) =>
      f.plural(count, { one: '# suggestion', other: '# suggestions' }),
  },
  SV: {
    hint: 'Nämn en projektmedlem med @namn eller @hel@adress. Pil ned öppnar förslagen.',
    suggestions: 'Förslag på omnämnanden',
    unavailable:
      'Kunde inte ladda medlemmar. Du kan fortfarande skriva en hel @e-postadress.',
    tooLong: 'Omnämnandet skulle överskrida kommentarens teckengräns.',
    count: ({ count }, f) =>
      f.plural(count, { one: '# förslag', other: '# förslag' }),
  },
});
export function MentionInput({
  projectId,
  value,
  onChange,
  label,
  disabled = false,
  placeholder,
}: {
  projectId: number;
  value: string;
  onChange: (text: string) => void;
  label: string;
  disabled?: boolean;
  placeholder?: string;
}) {
  const t = useT(text);
  const id = useId();
  const input = useRef<HTMLTextAreaElement>(null);
  const group = useRef<HTMLDivElement>(null);
  const [cursor, setCursor] = useState(value.length);
  const [dismissed, setDismissed] = useState(false);
  const [tooLong, setTooLong] = useState(false);
  const prefix = value.slice(0, cursor);
  const match = prefix.match(
    /(?:^|[^A-Za-z0-9._%+@-])@([A-Za-z0-9._%+-]*(?:@[A-Za-z0-9.-]*)?)$/,
  );
  const handle = match?.[1];
  const members = useQuery({
    ...membersQuery(projectId),
    enabled: handle !== undefined && !disabled && !dismissed,
  });
  const options =
    handle === undefined || dismissed
      ? []
      : (members.data ?? [])
          .filter((member) =>
            member.email.toLowerCase().includes(handle.toLowerCase()),
          )
          .slice(0, 8);
  function choose(email: string) {
    const at = cursor - (handle?.length ?? 0) - 1;
    const next = value.slice(0, at) + '@' + email + ' ' + value.slice(cursor);
    if (next.length > 1000) {
      setTooLong(true);
      return;
    }
    const position = at + email.length + 2;
    onChange(next);
    setCursor(position);
    setDismissed(true);
    setTooLong(false);
    requestAnimationFrame(() => {
      input.current?.focus();
      input.current?.setSelectionRange(position, position);
    });
  }
  function focusOption(index: number) {
    group.current
      ?.querySelectorAll<HTMLButtonElement>('button')
      [index]?.focus();
  }
  return (
    <div
      className="mention-input"
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null))
          setDismissed(true);
      }}
    >
      <label htmlFor={id}>{label}</label>
      <textarea
        id={id}
        ref={input}
        aria-describedby={`${id}-hint`}
        required
        maxLength={1000}
        rows={4}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        onChange={(e) => {
          setCursor(e.target.selectionStart);
          setDismissed(false);
          setTooLong(false);
          onChange(e.target.value);
        }}
        onSelect={(e) => setCursor(e.currentTarget.selectionStart)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown' && options.length) {
            e.preventDefault();
            focusOption(0);
          }
          if (e.key === 'Escape' && options.length) {
            e.preventDefault();
            e.stopPropagation();
            setDismissed(true);
          }
        }}
      />
      <p className="mention-hint" id={`${id}-hint`}>
        {t('hint')}
      </p>
      {tooLong && <p role="alert">{t('tooLong')}</p>}
      {handle !== undefined && !dismissed && members.isError && (
        <p>{t('unavailable')}</p>
      )}
      {options.length > 0 && (
        <>
          <p role="status">{t('count', { count: options.length })}</p>
          <div
            ref={group}
            className="mention-suggestions"
            role="group"
            aria-label={t('suggestions')}
          >
            {options.map((member, index) => (
              <button
                type="button"
                className="secondary-action"
                key={member.id}
                onClick={() => choose(member.email)}
                onKeyDown={(e) => {
                  if (['ArrowDown', 'ArrowUp'].includes(e.key)) {
                    e.preventDefault();
                    focusOption(
                      (index +
                        (e.key === 'ArrowDown' ? 1 : options.length - 1)) %
                        options.length,
                    );
                  }
                  if (e.key === 'Escape') {
                    e.preventDefault();
                    e.stopPropagation();
                    setDismissed(true);
                    input.current?.focus();
                  }
                }}
              >
                {member.email}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
