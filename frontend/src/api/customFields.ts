import { queryOptions } from '@tanstack/react-query';
import { api } from './client';
import { queryKeys } from './queries';
import type {
  CustomFieldCondition,
  CustomFieldDefinition,
  CustomFieldKind,
  CustomFieldOperator,
  TaskCustomFields,
} from './contracts';

export const customFieldsQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.customFields(projectId),
    queryFn: () =>
      api<CustomFieldDefinition[]>(`/projects/${projectId}/custom-fields`),
  });

export const taskCustomFieldsQuery = (projectId: number, taskId: number) =>
  queryOptions({
    queryKey: queryKeys.taskCustomFields(projectId, taskId),
    queryFn: () => api<TaskCustomFields>(`/tasks/${taskId}/custom-fields`),
  });

export function needsCustomValue(operator: CustomFieldOperator) {
  return operator !== 'IS_SET' && operator !== 'IS_NOT_SET';
}

export function customConditionReady(condition: CustomFieldCondition | null) {
  return (
    !!condition &&
    (!needsCustomValue(condition.operator) || !!condition.value?.trim())
  );
}

export function customOperators(kind: CustomFieldKind): CustomFieldOperator[] {
  return [
    'EQUALS',
    'NOT_EQUALS',
    ...(kind === 'TEXT' ? ['CONTAINS'] : []),
    ...(kind === 'NUMBER' || kind === 'DATE'
      ? ['LESS_THAN', 'AT_MOST', 'GREATER_THAN', 'AT_LEAST']
      : []),
    'IS_SET',
    'IS_NOT_SET',
  ] as CustomFieldOperator[];
}

/** Shared by search and CSV/PDF: clearing a field also removes old operands. */
export function appendCustomCondition(
  params: URLSearchParams,
  condition: CustomFieldCondition | null,
) {
  for (const name of ['customFieldId', 'customOperator', 'customValue'])
    params.delete(name);
  if (!condition) return;
  params.set('customFieldId', String(condition.fieldId));
  params.set('customOperator', condition.operator);
  if (needsCustomValue(condition.operator))
    params.set('customValue', condition.value ?? '');
}

export const customMatchesQuery = (
  projectId: number,
  condition: CustomFieldCondition | null,
) =>
  queryOptions({
    queryKey: [
      ...queryKeys.customMatches(projectId),
      condition?.fieldId,
      condition?.operator,
      condition?.value ?? '',
    ],
    enabled: !!projectId && customConditionReady(condition),
    queryFn: () => {
      if (!condition) throw new Error('A field condition is required.');
      const params = new URLSearchParams({
        fieldId: String(condition.fieldId),
        operator: condition.operator,
      });
      if (needsCustomValue(condition.operator))
        params.set('value', condition.value ?? '');
      return api<{ taskIds: number[] }>(
        `/projects/${projectId}/custom-fields/matches?${params}`,
      );
    },
  });
