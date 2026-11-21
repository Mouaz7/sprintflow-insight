import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { customFieldsQuery, customOperators } from '../../api/customFields';
import { membersQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { Drawer, Notice } from '../../shared/ui/ui';
import { CustomFieldInput } from '../../shared/ui/custom-fields/CustomFieldInput';
import { automationText } from './i18n';
import type { Draft, Rule, Trigger } from './queries';

const commands: Record<Trigger, string> = {
  TASK_MOVED: 'task-moved',
  PULL_REQUEST_MERGED: 'pull-request-merged',
  OVERDUE: 'overdue',
};
const statuses = {
  'To Do': 'toDo',
  'In Progress': 'inProgress',
  Review: 'review',
  Done: 'done',
} as const;
const actions = ['label', 'assign', 'move', 'comment', 'notify'] as const;
const operators = [
  'equals',
  'not-equals',
  'contains',
  'greater-than',
  'less-than',
  'at-least',
  'at-most',
  'is-set',
  'is-not-set',
] as const;
type Action = (typeof actions)[number];
type Condition =
  'always' | 'conditionStatus' | 'conditionLabel' | 'conditionField';
type Operator = (typeof operators)[number];

function quote(value: string) {
  return JSON.stringify(value.replaceAll('\r\n', '\n').replaceAll('\r', '\n'));
}

export function RuleEditor({
  projectId,
  rule,
  saving,
  error,
  onSave,
  onClose,
}: {
  projectId: number;
  rule?: Rule | undefined;
  saving: boolean;
  error: Error | null;
  onSave: (draft: Draft) => void;
  onClose: () => void;
}) {
  const t = useT(automationText);
  const { language } = useLanguage();
  const [name, setName] = useState(rule?.name ?? '');
  const [enabled, setEnabled] = useState(rule?.enabled ?? true);
  const [source, setSource] = useState<string | undefined>(rule?.source);
  const [trigger, setTrigger] = useState<Trigger>('TASK_MOVED');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('Review');
  const [condition, setCondition] = useState<Condition>('always');
  const [fieldId, setFieldId] = useState('');
  const [operator, setOperator] = useState<Operator>('equals');
  const [conditionValue, setConditionValue] = useState('');
  const [action, setAction] = useState<Action>('label');
  const [actionValue, setActionValue] = useState('');
  const fields = useQuery({
    ...customFieldsQuery(projectId),
    enabled: condition === 'conditionField',
  });
  const selectedField = fields.data?.find(
    (field) => String(field.id) === fieldId,
  );
  const allowedOperators = selectedField
    ? customOperators(selectedField.kind).map(
        (item) => item.toLowerCase().replaceAll('_', '-') as Operator,
      )
    : operators;
  const members = useQuery({
    ...membersQuery(projectId),
    enabled: action === 'assign' || selectedField?.kind === 'USER',
  });
  const noValue = operator === 'is-set' || operator === 'is-not-set';
  const incompleteField =
    source === undefined &&
    condition === 'conditionField' &&
    (!selectedField || (!noValue && !conditionValue.trim()));

  const lines = [`when ${commands[trigger]}`];
  if (trigger === 'TASK_MOVED') {
    if (from) lines[0] += ` from ${quote(from)}`;
    if (to) lines[0] += ` to ${quote(to)}`;
  }
  if (condition === 'conditionStatus')
    lines.push(`if status ${quote(conditionValue)}`);
  if (condition === 'conditionLabel')
    lines.push(`if label ${quote(conditionValue)}`);
  if (condition === 'conditionField')
    lines.push(
      `if field ${fieldId} ${operator}${noValue ? '' : ` ${quote(conditionValue)}`}`,
    );
  lines.push(`then ${action} ${quote(actionValue)}`);
  const dsl = source ?? lines.join('\n');
  const statusOptions = Object.entries(statuses).map(([value, key]) => (
    <option key={value} value={value}>
      {t(key)}
    </option>
  ));

  return (
    <Drawer
      title={t(rule ? 'editRule' : 'newRule')}
      onClose={onClose}
      busy={saving}
      dirty={
        name !== (rule?.name ?? '') ||
        enabled !== (rule?.enabled ?? true) ||
        (source !== undefined && source !== rule?.source) ||
        Boolean(actionValue)
      }
    >
      <form
        className="editor-form"
        onSubmit={(event) => {
          event.preventDefault();
          onSave({ name, enabled, source: dsl });
        }}
      >
        <Notice error={error ? localizeMessage(error.message, language) : ''} />
        <label>
          {t('ruleName')}{' '}
          <input
            value={name}
            required
            maxLength={80}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label className="blocked-filter">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(event) => setEnabled(event.target.checked)}
          />{' '}
          {t('enabled')}
        </label>
        {source === undefined ? (
          <>
            <label>
              {t('trigger')}{' '}
              <select
                value={trigger}
                onChange={(event) => setTrigger(event.target.value as Trigger)}
              >
                {(Object.keys(commands) as Trigger[]).map((item) => (
                  <option key={item} value={item}>
                    {t(item)}
                  </option>
                ))}
              </select>
            </label>
            {trigger === 'TASK_MOVED' && (
              <div className="form-row">
                <label>
                  {t('sourceColumn')}{' '}
                  <select
                    value={from}
                    onChange={(event) => setFrom(event.target.value)}
                  >
                    <option value="">{t('anyColumn')}</option>
                    {statusOptions}
                  </select>
                </label>
                <label>
                  {t('targetColumn')}{' '}
                  <select
                    value={to}
                    onChange={(event) => setTo(event.target.value)}
                  >
                    <option value="">{t('anyColumn')}</option>
                    {statusOptions}
                  </select>
                </label>
              </div>
            )}
            <label>
              {t('condition')}{' '}
              <select
                value={condition}
                onChange={(event) => {
                  setCondition(event.target.value as Condition);
                  setConditionValue('');
                }}
              >
                {(
                  [
                    'always',
                    'conditionStatus',
                    'conditionLabel',
                    'conditionField',
                  ] as const
                ).map((item) => (
                  <option key={item} value={item}>
                    {t(item)}
                  </option>
                ))}
              </select>
            </label>
            {condition === 'conditionField' && (
              <div className="form-row">
                <label>
                  {t('field')}{' '}
                  <select
                    required
                    value={fieldId}
                    onChange={(event) => {
                      setFieldId(event.target.value);
                      setOperator('equals');
                      setConditionValue('');
                    }}
                  >
                    <option value="">{t('choose')}</option>
                    {fields.data?.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {t('operator')}{' '}
                  <select
                    value={operator}
                    onChange={(event) =>
                      setOperator(event.target.value as Operator)
                    }
                  >
                    {allowedOperators.map((item) => (
                      <option key={item} value={item}>
                        {t(item)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            )}
            {condition === 'conditionStatus' && (
              <label>
                {t('fieldValue')}{' '}
                <select
                  required
                  value={conditionValue}
                  onChange={(event) => setConditionValue(event.target.value)}
                >
                  <option value="">{t('choose')}</option>
                  {statusOptions}
                </select>
              </label>
            )}
            {condition === 'conditionLabel' && (
              <label>
                {t('fieldValue')}{' '}
                <input
                  value={conditionValue}
                  required
                  maxLength={32}
                  onChange={(event) => setConditionValue(event.target.value)}
                />
              </label>
            )}
            {condition === 'conditionField' && selectedField && !noValue && (
              <CustomFieldInput
                field={selectedField}
                label={t('fieldValue')}
                value={conditionValue}
                onChange={setConditionValue}
                members={members.data ?? []}
                maxLength={200}
                disabled={saving}
              />
            )}
            <label>
              {t('action')}{' '}
              <select
                value={action}
                onChange={(event) => {
                  setAction(event.target.value as Action);
                  setActionValue('');
                }}
              >
                {actions.map((item) => (
                  <option key={item} value={item}>
                    {t(item)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('actionValue')}
              {action === 'move' || action === 'assign' ? (
                <select
                  value={actionValue}
                  required
                  onChange={(event) => setActionValue(event.target.value)}
                >
                  <option value="">{t('choose')}</option>
                  {action === 'move'
                    ? statusOptions
                    : members.data?.map((item) => (
                        <option key={item.email} value={item.email}>
                          {item.email}
                        </option>
                      ))}
                </select>
              ) : (
                <input
                  value={actionValue}
                  required
                  maxLength={action === 'label' ? 32 : 1000}
                  onChange={(event) => setActionValue(event.target.value)}
                />
              )}
            </label>
            <button
              type="button"
              className="secondary-action"
              onClick={() => setSource(dsl)}
            >
              {t('dsl')}
            </button>
          </>
        ) : (
          <>
            <p>{t('dslHelp')}</p>
            <label>
              {t('dsl')}{' '}
              <textarea
                rows={8}
                required
                maxLength={4096}
                value={source}
                onChange={(event) => setSource(event.target.value)}
              />
            </label>
            {!rule && (
              <button
                type="button"
                className="secondary-action"
                onClick={() => setSource(undefined)}
              >
                {t('builder')}
              </button>
            )}
          </>
        )}
        <div className="form-actions">
          <button
            className="primary-action"
            disabled={saving || incompleteField}
          >
            {t('save')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
