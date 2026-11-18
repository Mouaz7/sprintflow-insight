import type { Page } from './browser.ts';

// Every API request is intercepted; layout checks never change real accounts.
const user = {
  id: 1,
  email: 'leo@example.com',
  displayName: 'Alexandra Andersson · Produktutveckling och gemensam planering',
  jobTitle: 'Product designer',
  bio: 'Creating useful experiences with the SprintFlow team.',
};
const project = {
  id: 1,
  name: 'Website launch · Gemensam planering och leverans för hela produktteamet',
  description: 'A focused workspace for our next release. Plan together, keep delivery visible.',
  ownerEmail: user.email,
};
const stories = [{
  id: 1, projectId: 1, title: 'A smooth first experience',
  description: 'Help new teammates find their way around the workspace.',
  acceptanceCriteria: 'New users can create a project and find their tasks.',
  storyPoints: 8, priorityId: 1, businessValue: 5, sprintId: 1,
}];
const sprints = [{
  id: 1, projectId: 1, name: 'Sprint 04 · Delivery',
  goal: 'Ship the core experience and make onboarding simpler.',
  status: 'Active', totalStoryPoints: 24,
  startDate: '2026-10-05', endDate: '2026-10-18',
}];
const tasks = ['To Do', 'In Progress', 'Review', 'Done'].map((status, i) => ({
  id: i + 1, projectId: 1,
  title: [
    'Design the welcome flow with a clear introduction for every new team member',
    'Build the project dashboard', 'Review account settings', 'Create the shared workspace',
  ][i],
  description: 'Keep the flow clear and accessible.', status,
  dueAt: new Date().toISOString(), storyPoints: [3, 8, 5, 2][i],
  sprintId: 1, storyId: 1, assigneeEmail: user.email, blocked: false,
}));
const activity = [{
  id: 1, summary: 'Moved task “Create the shared workspace” to Done.',
  actorEmail: user.email, createdAt: '2026-10-11T18:10:00Z',
}, {
  id: 2, summary: 'Updated the sprint goal and delivery plan.',
  actorEmail: 'sara@example.com', createdAt: '2026-10-11T17:15:00Z',
}];
const metrics = {
  taskCount: 4, completionRate: 25, throughputLast14Days: 7,
  averageCycleTimeHours: 36, averageLeadTimeHours: 64, velocity: 18,
  statusCounts: { 'To Do': 1, 'In Progress': 1, Review: 1, Done: 1 },
  burndownAvailable: true,
  burndown: Array.from({ length: 14 }, (_, i) => ({
    date: `2026-10-${String(i + 1).padStart(2, '0')}`,
    remainingPoints: [24, 24, 21, 21, 19, 19, 16, 16, 13, 13, 10, 10, 8, 6][i],
  })),
  sprintHealth: { status: 'Healthy', workInProgress: 2, blockedTasks: 0, explanation: 'Work is moving steadily with no blocked tasks.' },
  sprintPace: { available: true, status: 'On track', plannedStoryPoints: 24, deliveredStoryPoints: 18, expectedStoryPoints: 16, elapsedPercent: 67 },
  businessValue: { planned: 24, delivered: 18 },
};

type Options = {
  signedIn?: boolean;
  empty?: boolean;
  language: 'EN' | 'SV';
  theme: 'light' | 'dark';
};

export async function responsiveFixture(page: Page, options: Options) {
  const { signedIn = true, empty = false, language, theme } = options;
  const preferences = { language, theme, version: 1, onboardingCompleted: true, notifications: true, savedFilters: '[]' };
  await page.addInitScript(({ language, theme }) => {
    localStorage.setItem('sprintflow-language', language);
    localStorage.setItem('sprintflow-theme', theme);
  }, { language, theme });
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (!pathname.startsWith('/api/')) return route.continue();
    let body: unknown = [];
    let status = 200;
    if (pathname.endsWith('/auth/me')) {
      body = signedIn ? user : { message: 'Authentication required' };
      status = signedIn ? 200 : 401;
    } else if (pathname.endsWith('/auth/csrf')) {
      body = { token: 'fixture-token', headerName: 'X-CSRF-TOKEN' };
    } else if (pathname.endsWith('/projects')) {
      body = signedIn ? (empty ? [] : [project]) : { message: 'Authentication required' };
      status = signedIn ? 200 : 401;
    } else if (pathname.endsWith('/auth/preferences')) {
      body = preferences;
    } else if (pathname.endsWith('/notifications')) {
      body = Array.from({ length: 12 }, (_, i) => ({
        id: i + 1, title: 'Uppdatering för projektet med ett långt namn',
        message: 'En längre avisering som behöver kunna radbrytas och läsas även i ett lågt fönster.',
        read: false, createdAt: '2026-10-22T12:00:00Z',
      }));
    } else if (pathname.endsWith('/members')) {
      body = [{ id: 1, email: user.email, role: 'Owner' }, { id: 2, email: 'sara@example.com', role: 'Member' }];
    } else if (pathname.endsWith('/roadmap')) body = {
      today: '2026-10-05',
      epics: [{ epic: { id: 1, title: 'Customer portal · Gemensam leverans och uppföljning', description: 'A clear release goal for the whole team.', color: '#4f46e5', startDate: '2026-10-05', targetDate: '2026-10-30', version: 0 }, plannedStart: '2026-10-05', plannedEnd: '2026-10-30', storyCount: 1, totalPoints: 8, donePoints: 0, taskCount: 4, doneTaskCount: 1, progress: .25, storyIds: [1], sprintIds: [1], forecast: { start: '2026-10-05', expectedDays: 6, standardDeviationDays: 1, expectedFinish: '2026-10-12', probabilityOnTime: .99, estimatedTasks: 3, openTasks: 3, unplannedStories: 0, criticalPath: [] } }],
      sprints, epicLinks: [], dependencies: { dependencies: [], blockedTaskIds: [], externalBlocks: [] },
      project: { start: '2026-10-05', expectedDays: 6, standardDeviationDays: 1, expectedFinish: '2026-10-12', probabilityOnTime: null, estimatedTasks: 3, openTasks: 3, unplannedStories: 0, criticalPath: [] },
    };
    else if (pathname.endsWith('/wip-limits')) body = { version: 0, limits: ['To Do','In Progress','Review'].map(status => ({ status, limit: status === 'In Progress' ? 2 : null })) };
    else if (pathname.endsWith('/flow')) body = {
      days: 30, start: '2026-10-01', end: '2026-10-30', generatedAt: '2026-10-30T12:00:00Z',
      cumulativeFlow: Array.from({ length: 30 }, (_,i) => ({ date: `2026-10-${String(i+1).padStart(2,'0')}`, toDo: 6, inProgress: 2, review: 1, done: Math.floor(i/3) })),
      cycleTimeHours: { count: 8, mean: 24, p50: 24, p85: 28, p95: 30 }, leadTimeHours: { count: 8, mean: 48, p50: 48, p85: 52, p95: 54 },
      completedTasks: 8, throughputPerDay: .27,
      completions: Array.from({ length: 8 }, (_,i) => ({ taskId: i+10, key: `QA-${i+10}`, completedAt: `2026-10-${String(i+1).padStart(2,'0')}T12:00:00Z`, cycleTimeHours: 20+i, leadTimeHours: 44+i })),
      agingWip: [{ taskId: 2, key: 'QA-2', title: 'Build the project dashboard', status: 'In Progress', ageHours: 72, statusAgeHours: 24, band: 'ABOVE_P95' }],
      columns: ['To Do','In Progress','Review'].map(status => ({ status, currentWip: 1, averageWip: 1.5, limit: status === 'In Progress' ? 2 : null, overLimit: false, bottleneck: status === 'Review' ? 'HIGHEST_AVERAGE_WIP' : null })),
      controlChart: { available: true, minimumPoints: 6, mean: 24, upperLimit: 32, lowerLimit: 16, movingRangeMean: 3, upperRangeLimit: 9.8, signalCount: 0, points: Array.from({ length: 8 }, (_,i) => ({ taskId: i+10, key: `QA-${i+10}`, completedAt: `2026-10-${String(i+1).padStart(2,'0')}T12:00:00Z`, cycleTimeHours: 20+i, movingRange: i ? 1 : null, signals: [] })) },
      littlesLaw: { status: 'INCONSISTENT', averageWip: 2.5, throughputPerDay: .27, averageCycleTimeDays: 1, expectedWip: .27, ratio: 9.26, tolerance: .25 }, tasksWithoutHistory: 0,
    };
    else if (pathname.endsWith('/dependencies')) body = { dependencies: [], blockedTaskIds: [], externalBlocks: [] };
    else if (pathname.endsWith('/estimates')) body = [];
    else if (pathname.endsWith('/role')) body = { role: 'Owner' };
    else if (pathname.endsWith('/metrics')) body = metrics;
    else if (pathname.endsWith('/activity')) body = activity;
    else if (pathname.endsWith('/stories/project/1')) body = stories;
    else if (pathname.endsWith('/sprints')) body = sprints;
    else if (pathname.endsWith('/tasks')) body = tasks;
    else if (pathname.endsWith('/auth/login')) {
      body = { message: 'Invalid credentials' };
      status = 401;
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  });
}
