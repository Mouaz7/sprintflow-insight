import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Language, Preferences } from '../api/contracts';
import { FormGuide, fieldExample } from './Guidance';
import { Drawer, Notice, useConfirm } from './ui';
export type TaskFilters = {
  query: string;
  status: string;
  sprint: string;
  assignee: string;
  due: string;
  blocked: boolean;
  label: string;
};
type SavedFilter = {
  id: string;
  name: string;
  projectId: number;
  criteria: TaskFilters;
};
export function SavedFilters({
  projectId,
  language,
  userEmail,
  current,
  onApply,
}: {
  projectId: number;
  language: Language;
  userEmail: string;
  current: TaskFilters;
  onApply: (value: TaskFilters) => void;
}) {
  const sv = language === 'SV';
  const [items, setItems] = useState<SavedFilter[]>([]);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState('');
  const [editor, setEditor] = useState<{ item?: SavedFilter }>();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const confirm = useConfirm();
  useEffect(() => {
    let live = true;
    api<Preferences>('/auth/preferences')
      .then((value) => {
        if (live) {
          try {
            const data = JSON.parse(value.savedFilters ?? '[]');
            setItems(Array.isArray(data) ? data : []);
          } catch {
            setError(
              sv
                ? 'Sparade filter kunde inte läsas.'
                : 'Saved filters could not load.',
            );
          }
        }
      })
      .catch((cause) => {
        if (live) setError(cause.message);
      });
    return () => {
      live = false;
    };
  }, [projectId]);
  async function persist(values: SavedFilter[]) {
    setBusy(true);
    setError('');
    try {
      await api('/auth/preferences', {
        method: 'PUT',
        body: JSON.stringify({ savedFilters: JSON.stringify(values) }),
      });
      setItems(values);
      return true;
    } catch (cause) {
      setError((cause as Error).message);
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy || !name.trim()) return;
    const value: SavedFilter = editor?.item
      ? { ...editor.item, name: name.trim() }
      : {
          id: crypto.randomUUID(),
          name: name.trim(),
          projectId,
          criteria: { ...current },
        };
    if (
      await persist(
        editor?.item
          ? items.map((item) => (item.id === value.id ? value : item))
          : [...items, value],
      )
    ) {
      setSelected(value.id);
      setEditor(undefined);
    }
  }
  async function remove() {
    const item = items.find((value) => value.id === selectedValue);
    if (
      item &&
      (await confirm(
        sv ? 'Ta bort det sparade filtret?' : 'Delete this saved filter?',
      )) &&
      (await persist(items.filter((value) => value.id !== item.id)))
    )
      setSelected('');
  }
  const empty: TaskFilters = {
    query: '',
    status: '',
    sprint: '',
    assignee: '',
    due: '',
    blocked: false,
    label: '',
  };
  const selectedCriteria = selected === 'mine'
    ? { ...empty, assignee: userEmail }
    : selected === 'blocked'
      ? { ...empty, blocked: true }
      : items.find((item) => item.id === selected && item.projectId === projectId)?.criteria;
  const criteria = { ...empty, ...selectedCriteria };
  const selectedValue = selectedCriteria &&
    (Object.keys(empty) as (keyof TaskFilters)[]).every(
      (key) => current[key] === criteria[key],
    ) ? selected : '';
  return (
    <div className="saved-filter-controls">
      <label>
        {sv ? 'Sparade filter' : 'Saved filters'}
        <select
          aria-label={sv ? 'Sparade filter' : 'Saved filters'}
          value={selectedValue}
          onChange={(event) => {
            const value = event.target.value;
            setSelected(value);
            if (value === 'mine') onApply({ ...empty, assignee: userEmail });
            else if (value === 'blocked') onApply({ ...empty, blocked: true });
            else if (!value) onApply(empty);
            else {
              const filter = items.find(
                (item) => item.id === value && item.projectId === projectId,
              );
              if (filter) onApply({ ...empty, ...filter.criteria });
            }
          }}
        >
          <option value="">{sv ? 'Alla uppgifter' : 'All tasks'}</option>
          <option value="mine">{sv ? 'Mina uppgifter' : 'My tasks'}</option>
          <option value="blocked">{sv ? 'Blockerat' : 'Blocked'}</option>
          {items
            .filter((item) => item.projectId === projectId)
            .map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
        </select>
      </label>
      <button
        disabled={busy || items.length >= 30}
        onClick={() => {
          setName('');
          setEditor({});
        }}
      >
        {sv ? 'Spara filter' : 'Save filter'}
      </button>
      {items.some((item) => item.id === selectedValue) && (
        <>
          <button
            disabled={busy}
            onClick={() => {
              const item = items.find((value) => value.id === selectedValue)!;
              setName(item.name);
              setEditor({ item });
            }}
          >
            {sv ? 'Byt filternamn' : 'Rename filter'}
          </button>
          <button disabled={busy} className="danger-action" onClick={remove}>
            {sv ? 'Ta bort filter' : 'Delete filter'}
          </button>
        </>
      )}
      <Notice language={language} error={editor ? undefined : error} />
      {editor && (
        <Drawer
          title={
            editor.item
              ? sv
                ? 'Byt filternamn'
                : 'Rename filter'
              : sv
                ? 'Spara filter'
                : 'Save filter'
          }
          language={language}
          dirty={name !== (editor.item?.name ?? '')}
          busy={busy}
          onClose={() => setEditor(undefined)}
        >
          <form className="editor-form" onSubmit={save}>
            <FormGuide topic="filter" language={language} />
            <label>
              {sv ? 'Filternamn' : 'Filter name'}
              <input
                required
                maxLength={80}
                placeholder={fieldExample('filter', language)}
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <Notice language={language} error={error} />
            <button className="primary-action" disabled={busy || !name.trim()}>
              {sv ? 'Spara' : 'Save'}
            </button>
          </form>
        </Drawer>
      )}
    </div>
  );
}
