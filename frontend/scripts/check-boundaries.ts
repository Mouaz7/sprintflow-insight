import assert from 'node:assert/strict';
import { ESLint } from 'eslint';

const eslint = new ESLint();
const cases = [
  ['src/features/tasks/boundary-probe.ts', '../planning/BacklogView', true],
  ['src/shared/lib/boundary-probe.ts', '../../features/tasks/TasksView', true],
  ['src/features/tasks/boundary-probe.ts', '../../app/Workspace', true],
  [
    'src/features/tasks/boundary-probe.ts',
    '../../shared/lib/formValues',
    false,
  ],
  ['src/features/tasks/boundary-probe.ts', './TaskCollaboration', false],
];
for (const [filePath, dependency, rejected] of cases) {
  const [result] = await eslint.lintText(`import '${dependency}';\n`, {
    filePath,
  });
  const violated = result.messages.some(
    (message) => message.ruleId === 'boundaries/dependencies',
  );
  assert.equal(
    violated,
    rejected,
    `${filePath} importing ${dependency}: ${JSON.stringify(result.messages)}`,
  );
}
console.log(
  'Feature import boundaries reject cross-feature/upward imports and accept shared/local imports.',
);
