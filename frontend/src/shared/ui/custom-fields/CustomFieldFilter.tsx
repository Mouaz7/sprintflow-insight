import { useQueries } from '@tanstack/react-query';
import type { CustomFieldCondition } from '../../../api/contracts';
import {
  customFieldsQuery,
  customOperators,
  needsCustomValue,
} from '../../../api/customFields';
import { membersQuery } from '../../../api/queries';
import { useT } from '../../i18n/LanguageProvider';
import { CustomFieldInput } from './CustomFieldInput';
import { customFieldText } from './i18n';

/** Controlled criteria shared by the board and project-scoped search. */
export function CustomFieldFilter({
  projectId,
  value,
  onChange,
}: {
  projectId: number;
  value: CustomFieldCondition | null;
  onChange: (value: CustomFieldCondition | null) => void;
}) {
  const t = useT(customFieldText);
  const [fields, members] = useQueries({
    queries: [customFieldsQuery(projectId), membersQuery(projectId)],
  });
  const field = fields.data?.find((item) => item.id === value?.fieldId);
  return (
    <div className="custom-field-filter">
      <label>
        {t('filter')}
        <select
          aria-label={t('filter')}
          value={value?.fieldId ?? ''}
          onChange={(event) =>
            onChange(
              event.target.value
                ? {
                    fieldId: Number(event.target.value),
                    operator: 'IS_SET',
                  }
                : null,
            )
          }
        >
          <option value="">{t('all')}</option>
          {value && !field && (
            <option value={value.fieldId} disabled>
              {t('unavailable')}
            </option>
          )}
          {fields.data?.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      {field && value && (
        <>
          <label>
            {t('operator')}
            <select
              aria-label={t('operator')}
              value={value.operator}
              onChange={(event) =>
                onChange({
                  fieldId: field.id,
                  operator: event.target
                    .value as CustomFieldCondition['operator'],
                  value: '',
                })
              }
            >
              {customOperators(field.kind).map((operator) => (
                <option key={operator} value={operator}>
                  {t(operator)}
                </option>
              ))}
            </select>
          </label>
          {needsCustomValue(value.operator) && (
            <CustomFieldInput
              field={field}
              label={t('value')}
              value={value.value ?? ''}
              maxLength={200}
              members={members.data ?? []}
              onChange={(operand) => onChange({ ...value, value: operand })}
            />
          )}
          <small>{t('filterHint')}</small>
        </>
      )}
      {fields.isError && (
        <p role="alert">
          {t('failed')}{' '}
          <button
            onClick={() => {
              void fields.refetch();
            }}
          >
            {t('retry')}
          </button>
        </p>
      )}
      {value &&
        (!field ||
          (needsCustomValue(value.operator) && !value.value?.trim())) && (
          <p role="status">{t('invalidFilter')}</p>
        )}
      {value && <button onClick={() => onChange(null)}>{t('clear')}</button>}
    </div>
  );
}
