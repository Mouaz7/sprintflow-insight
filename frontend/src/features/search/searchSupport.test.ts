import { describe, expect, it } from 'vitest';
import { targetOf } from './CommandPalette';
import { taskKeyOf, type SearchHit } from './searchApi';
import { searchText } from './searchText';

const base: SearchHit = {
  type: 'task',
  id: 1,
  projectId: 3,
  projectKey: 'SF',
  projectName: 'Alpha',
  taskId: 1,
  key: 'SF-1',
  title: 'T',
  titleSegments: [],
  snippet: [],
  exactMatch: false,
};

describe('search support', () => {
  it('recognises task keys like the server does', () => {
    expect(taskKeyOf(' sf-12 ')).toBe('SF-12');
    expect(taskKeyOf('ABCDEFGHIJ-999999999')).toBe('ABCDEFGHIJ-999999999');
    expect(taskKeyOf('SF-0')).toBeUndefined();
    expect(taskKeyOf('S-1')).toBeUndefined();
    expect(taskKeyOf('SF-12 login')).toBeUndefined();
  });

  it('sends each kind of hit to its view', () => {
    expect(targetOf(base)).toEqual({
      view: 'tasks',
      projectId: 3,
      taskKey: 'SF-1',
    });
    expect(targetOf({ ...base, type: 'comment' })).toEqual({
      view: 'tasks',
      projectId: 3,
      taskKey: 'SF-1',
    });
    expect(targetOf({ ...base, type: 'task', key: null })).toEqual({
      view: 'tasks',
      projectId: 3,
    });
    expect(targetOf({ ...base, type: 'story' })).toEqual({
      view: 'backlog',
      projectId: 3,
    });
    expect(targetOf({ ...base, type: 'sprint' })).toEqual({
      view: 'sprints',
      projectId: 3,
    });
    expect(targetOf({ ...base, type: 'project' })).toEqual({
      view: 'overview',
      projectId: 3,
    });
  });

  it('fills placeholders and keeps unknown ones', () => {
    expect(searchText('EN')('resultCount', { count: 3 })).toBe('3 results');
    expect(searchText('SV')('resultCount', { count: 3 })).toBe('3 träffar');
    expect(searchText('EN')('inProject')).toBe('in {project}');
  });
});
