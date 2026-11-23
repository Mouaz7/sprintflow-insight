import type { CustomFieldCondition } from '../../api/contracts';

export type TaskFilters = {
  query: string;
  status: string;
  sprint: string;
  assignee: string;
  due: string;
  blocked: boolean;
  label: string;
  custom?: CustomFieldCondition | null;
};
export const emptyTaskFilters: TaskFilters = {
  query: '',
  status: '',
  sprint: '',
  assignee: '',
  due: '',
  blocked: false,
  label: '',
  custom: null,
};
