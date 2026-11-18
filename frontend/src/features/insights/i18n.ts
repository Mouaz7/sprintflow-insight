import { icu } from '../../shared/i18n/icu';
import type { Language } from '../../api/contracts';
import { defineLexicon, type Translate } from '../../shared/i18n/lexicon';

export const insightsText = defineLexicon({
  EN: {
    // Overview
    yourWorkspace: 'YOUR WORKSPACE',
    chooseProject: 'Choose a project',
    sharedSpace: 'Start with a shared space for your team.',
    nextInitiative: 'Your next initiative starts here',
    createProjectHint: 'Create a project for tasks, sprints and delivery data.',
    createProject: 'Create project',
    tasks: 'Tasks',
    inThisProject: 'In this project',
    completion: 'Completion',
    ofAllWork: 'Of all work',
    throughput: 'Throughput',
    last14Days: 'Last 14 days',
    deliveryHealth: 'Delivery health',
    currentStatus: 'Current status',
    healthHealthy: 'Healthy',
    healthAtRisk: 'At risk',
    healthWatch: 'Watch WIP',
    flowWatch: 'Watch WIP',
    sectionFailed: 'This section could not load. Try refreshing the page.',
    loading: 'Loading…',
    projectOverview: 'PROJECT OVERVIEW',
    teamNextSteps: 'Your team’s next steps, in one place.',
    newTask: 'New task',
    nextSteps: 'NEXT STEPS',
    workInMotion: 'Work in motion',
    openBoard: 'Open board',
    unassigned: 'Unassigned',
    noOpenTasks: 'No open tasks. The next step is yours.',
    teamFocus: 'TEAM FOCUS',
    activeSprint: 'Active sprint',
    activeBadge: 'ACTIVE',
    addSprintGoal: 'Add a shared sprint goal.',
    plannedPoints: 'planned points',
    until: ({ date }: { date: string }) => `Until ${date}`,
    noActiveSprint: 'No active sprint yet.',
    viewSprints: 'View sprints',
    latestActivity: 'LATEST ACTIVITY',
    fromTheTeam: 'From the team',
    viewHistory: 'View history',
    noActivity: 'No activity yet for this project.',
    // Task statuses: the board wording and the report wording differ in Swedish.
    boardToDo: 'To Do',
    boardInProgress: 'In Progress',
    boardReview: 'Review',
    boardDone: 'Done',
    flowToDo: 'To Do',
    flowInProgress: 'In Progress',
    flowReview: 'Review',
    flowDone: 'Done',
    // Metrics cards
    cycleTime: 'Cycle time',
    cycleTimeDetail: 'Active work to completion',
    leadTime: 'Lead time',
    leadTimeDetail: 'Creation to completion',
    throughputSprint: 'Completed in the selected sprint',
    throughput14: 'Completed in the last 14 days',
    velocity: 'Velocity',
    velocitySprint: 'Points completed in the selected sprint',
    velocityAll: 'Completed story points',
    completionDetail: 'Share of completed tasks',
    overdue: 'Overdue',
    overdueDetail: 'Open tasks past their due date',
    burndownNoTasks: 'Add tasks to start tracking delivery',
    burndownIncomplete: 'History is not complete yet',
    burndownShort: 'Not enough history for a chart',
    allWorkComplete: 'All work is complete',
    burndownNoRemaining: 'No remaining points in this period',
    burndownNoTasksDetail:
      'Create tasks and add story points to see delivery history here.',
    burndownIncompleteDetail:
      'Every task needs status history before we can show a trustworthy chart.',
    burndownShortDetail:
      'The chart appears when at least two days of saved activity are available.',
    burndownFlatDetail:
      'There is no meaningful trend to plot for the selected period.',
    // Metrics view
    loadingMetrics: 'Loading metrics…',
    dataScope: 'Data scope',
    entireProject: 'Entire project',
    howCalculated: 'How these metrics are calculated',
    cycleTimeLabel: 'Cycle time:',
    cycleTimeMethod:
      'from the latest In Progress transition to the latest Done transition.',
    leadTimeLabel: 'Lead time:',
    leadTimeMethod: 'from task creation to its Done transition.',
    throughputLabel: 'Throughput:',
    throughputMethodSprint: 'tasks completed in the selected sprint.',
    throughputMethod14: 'tasks completed in the last 14 days.',
    velocityLabel: 'Velocity:',
    velocityMethod: 'story points on tasks with a persisted Done transition.',
    // History
    history: 'HISTORY',
    completedSprints: 'Completed sprints',
    completed: 'Completed',
    pts: 'pts',
    tasksCompleted: 'tasks completed',
    noHistory: 'Completed sprints will appear here when history is available.',
    // Sprint analysis
    sprintPace: 'SPRINT PACE',
    deliveryAgainstPlan: 'Delivery against plan',
    onTrack: 'On track',
    behind: 'Behind',
    datesNeeded: 'Dates needed',
    paceSummary: ({
      delivered,
      planned,
      expected,
    }: {
      delivered: string;
      planned: string;
      expected: string;
    }) =>
      `${delivered} of ${planned} points delivered; ${expected} expected so far.`,
    paceMissing: 'Add sprint start and end dates to compare delivery pace.',
    businessValue: 'BUSINESS VALUE',
    valueDelivered: 'Value delivered',
    valueSummary: ({
      delivered,
      expected,
      stories,
    }: {
      delivered: string;
      expected: string;
      stories: string;
    }) =>
      `${delivered} of ${expected} value points delivered across ${stories} stories.`,
    valueMissing:
      'Link stories with business values from 1–5 to tasks in this sprint.',
    // Flow analysis
    flow: 'FLOW',
    sprintBurndown: 'Sprint burndown',
    burndown14: '14-day burndown',
    pointsLeft: 'points left',
    burndownChart:
      'Burndown: remaining story points per day during the selected period',
    pointsRemaining: 'points remaining',
    remainingPoints: 'Remaining points',
    idealPace: 'Ideal pace',
    sprintHealth: 'SPRINT HEALTH',
    deliveryRisk: 'Delivery risk',
    noRisk: 'No current delivery risk detected.',
    inProgress: 'In progress',
    blocked: 'Blocked',
    // Shared reports
    confirmShare:
      'Create a shared report? Anyone with the link can read the project description and aggregate metrics for seven days.',
    confirmRevoke: 'Revoke this report link?',
    shareableReport: 'Shareable project report',
    shareableDetail:
      'A read-only snapshot without member lists, comments or account credentials.',
    createReportLink: 'Create report link',
    reportLink: 'Report link',
    copyManually: 'Copy the link manually from the field.',
    copied: 'Copied',
    copyLink: 'Copy link',
    preview: 'Preview',
    expires: 'Expires',
    revoke: 'Revoke',
    sharedReport: 'SHARED REPORT',
    projectReport: 'Project report',
    otherLanguage: 'Svenska',
    print: 'Print',
    loadingReport: 'Loading report…',
    generated: 'Generated',
    completedPercent: 'Completed (%)',
    deliveredPoints: 'Delivered points',
    cycleTimeHours: 'Cycle time (h)',
    workflow: 'Workflow',
    snapshotNote:
      'This is a snapshot. The project owner can revoke this report link.',
    // Activity timeline
    confirmDeleteUpdate: 'Delete this update?',
    confirmClearHistory: 'Delete all project history? This cannot be undone.',
    clearFailed: 'Could not delete history.',
    eventCount: icu('eventCount'),
    clearing: 'Clearing…',
    clearHistory: 'Clear all history',
    edit: 'Edit',
    delete: 'Delete',
    storyStarts: 'The story starts here',
    activityAppears:
      'Project activity appears here when the team starts working.',
    enterSummary: 'Enter an update summary.',
    editUpdate: 'Edit update',
    updateSummary: 'Update summary',
    saving: 'Saving…',
    save: 'Save',
  },
  SV: {
    yourWorkspace: 'DIN ARBETSYTA',
    chooseProject: 'Välj ett projekt',
    sharedSpace: 'Börja med en gemensam plats för ditt team.',
    nextInitiative: 'Ditt nästa initiativ börjar här',
    createProjectHint:
      'Skapa ett projekt för uppgifter, sprintar och leveransdata.',
    createProject: 'Skapa projekt',
    tasks: 'Uppgifter',
    inThisProject: 'I projektet',
    completion: 'Slutfört',
    ofAllWork: 'Av allt arbete',
    throughput: 'Genomströmning',
    last14Days: 'Senaste 14 dagarna',
    deliveryHealth: 'Leveransstatus',
    currentStatus: 'Aktuell status',
    healthHealthy: 'Stabil',
    healthAtRisk: 'Risk',
    healthWatch: 'Håll koll',
    flowWatch: 'Håll koll på WIP',
    sectionFailed: 'Den här delen kunde inte laddas. Försök ladda om sidan.',
    loading: 'Laddar…',
    projectOverview: 'PROJEKTÖVERSIKT',
    teamNextSteps: 'Ditt teams nästa steg, på ett ställe.',
    newTask: 'Ny uppgift',
    nextSteps: 'NÄSTA STEG',
    workInMotion: 'Arbetet som pågår',
    openBoard: 'Öppna tavlan',
    unassigned: 'Ingen ansvarig',
    noOpenTasks: 'Inga öppna uppgifter. Nästa steg är ditt.',
    teamFocus: 'TEAMETS FOKUS',
    activeSprint: 'Aktiv sprint',
    activeBadge: 'AKTIV',
    addSprintGoal: 'Lägg till ett gemensamt sprintmål.',
    plannedPoints: 'planerade poäng',
    until: ({ date }) => `Till ${date}`,
    noActiveSprint: 'Ingen aktiv sprint ännu.',
    viewSprints: 'Visa sprintar',
    latestActivity: 'SENASTE HÄNDELSER',
    fromTheTeam: 'Från teamet',
    viewHistory: 'Visa historik',
    noActivity: 'Ingen aktivitet i projektet ännu.',
    boardToDo: 'Att göra',
    boardInProgress: 'Pågår',
    boardReview: 'Granskning',
    boardDone: 'Klart',
    flowToDo: 'Att göra',
    flowInProgress: 'Pågående',
    flowReview: 'Granskning',
    flowDone: 'Klart',
    cycleTime: 'Cykeltid',
    cycleTimeDetail: 'Aktivt arbete till klart',
    leadTime: 'Ledtid',
    leadTimeDetail: 'Skapad till klart',
    throughputSprint: 'Avslutade i vald sprint',
    throughput14: 'Klart senaste 14 dagarna',
    velocity: 'Hastighet',
    velocitySprint: 'Poäng klara i vald sprint',
    velocityAll: 'Slutförda poäng',
    completionDetail: 'Andel klara uppgifter',
    overdue: 'Försenade',
    overdueDetail: 'Öppna uppgifter efter slutdatum',
    burndownNoTasks: 'Lägg till uppgifter för att följa arbetet',
    burndownIncomplete: 'Historiken är inte komplett ännu',
    burndownShort: 'För lite historik för en graf',
    allWorkComplete: 'Allt arbete är klart',
    burndownNoRemaining: 'Inga återstående poäng i perioden',
    burndownNoTasksDetail:
      'Skapa uppgifter och ange story points så visas leveranshistoriken här.',
    burndownIncompleteDetail:
      'Alla uppgifter behöver statushistorik innan vi kan visa ett tillförlitligt diagram.',
    burndownShortDetail:
      'Diagrammet visas när det finns minst två dagar med sparad aktivitet.',
    burndownFlatDetail:
      'Det finns ingen meningsfull trend att visa för den valda perioden.',
    loadingMetrics: 'Laddar mätvärden…',
    dataScope: 'Visa data',
    entireProject: 'Hela projektet',
    howCalculated: 'Så beräknas mätvärdena',
    cycleTimeLabel: 'Cykeltid:',
    cycleTimeMethod: 'från senaste start i Pågår till senaste Klart-händelsen.',
    leadTimeLabel: 'Ledtid:',
    leadTimeMethod: 'från att uppgiften skapades till Klart-händelsen.',
    throughputLabel: 'Genomströmning:',
    throughputMethodSprint:
      'antal uppgifter som slutförts i den valda sprinten.',
    throughputMethod14:
      'antal uppgifter som slutförts under de senaste 14 dagarna.',
    velocityLabel: 'Hastighet:',
    velocityMethod:
      'story points för uppgifter som har en sparad Klart-händelse.',
    history: 'HISTORIK',
    completedSprints: 'Avslutade sprintar',
    completed: 'Avslutad',
    pts: 'poäng',
    tasksCompleted: 'uppgifter klara',
    noHistory: 'Avslutade sprintar visas här när historik finns.',
    sprintPace: 'SPRINTTAKT',
    deliveryAgainstPlan: 'Leverans mot plan',
    onTrack: 'I fas',
    behind: 'Efter plan',
    datesNeeded: 'Datum saknas',
    paceSummary: ({ delivered, planned, expected }) =>
      `${delivered} av ${planned} poäng klara; ${expected} förväntade hittills.`,
    paceMissing:
      'Lägg till start- och slutdatum på sprinten för att jämföra leveranstakt.',
    businessValue: 'AFFÄRSVÄRDE',
    valueDelivered: 'Levererat värde',
    valueSummary: ({ delivered, expected, stories }) =>
      `${delivered} av ${expected} värdepoäng levererade från ${stories} berättelser.`,
    valueMissing:
      'Koppla berättelser med affärsvärde 1–5 till sprintens uppgifter.',
    flow: 'FLÖDE',
    sprintBurndown: 'Sprintens burndown',
    burndown14: 'Burndown, 14 dagar',
    pointsLeft: 'poäng kvar',
    burndownChart:
      'Burndown: återstående poäng per dag under den valda perioden',
    pointsRemaining: 'poäng kvar',
    remainingPoints: 'Återstående poäng',
    idealPace: 'Ideal takt',
    sprintHealth: 'SPRINTSTATUS',
    deliveryRisk: 'Leveransrisk',
    noRisk: 'Ingen risk upptäckt.',
    inProgress: 'Pågående',
    blocked: 'Blockerade',
    confirmShare:
      'Skapa en delbar rapport? Alla med länken kan läsa projektbeskrivningen och sammanställda mätvärden i sju dagar.',
    confirmRevoke: 'Återkalla rapportlänken?',
    shareableReport: 'Delbar projektrapport',
    shareableDetail:
      'En skrivskyddad ögonblicksbild utan medlemslistor, kommentarer eller inloggningsuppgifter.',
    createReportLink: 'Skapa rapportlänk',
    reportLink: 'Rapportlänk',
    copyManually: 'Kopiera länken manuellt från fältet.',
    copied: 'Kopierad',
    copyLink: 'Kopiera länk',
    preview: 'Förhandsvisa',
    expires: 'Giltig till',
    revoke: 'Återkalla',
    sharedReport: 'DELAD RAPPORT',
    projectReport: 'Projektrapport',
    otherLanguage: 'English',
    print: 'Skriv ut',
    loadingReport: 'Laddar rapport…',
    generated: 'Skapad',
    completedPercent: 'Klart (%)',
    deliveredPoints: 'Levererade poäng',
    cycleTimeHours: 'Cykeltid (h)',
    workflow: 'Arbetsflöde',
    snapshotNote:
      'Det här är en ögonblicksbild. Rapportlänken kan återkallas av projektägaren.',
    confirmDeleteUpdate: 'Ta bort den här uppdateringen?',
    confirmClearHistory:
      'Radera all historik i projektet? Det går inte att ångra.',
    clearFailed: 'Historiken kunde inte raderas.',
    eventCount: icu('eventCount'),
    clearing: 'Raderar…',
    clearHistory: 'Radera all historik',
    edit: 'Redigera',
    delete: 'Ta bort',
    storyStarts: 'Här börjar historien',
    activityAppears: 'Projektets aktivitet visas här när teamet börjar arbeta.',
    enterSummary: 'Ange en uppdateringstext.',
    editUpdate: 'Redigera uppdatering',
    updateSummary: 'Uppdateringstext',
    saving: 'Sparar…',
    save: 'Spara',
  },
});

export type InsightsT = Translate<(typeof insightsText)['EN']>;

const boardStatuses = {
  'To Do': 'boardToDo',
  'In Progress': 'boardInProgress',
  Review: 'boardReview',
  Done: 'boardDone',
} as const;

const flowStatuses = {
  'To Do': 'flowToDo',
  'In Progress': 'flowInProgress',
  Review: 'flowReview',
  Done: 'flowDone',
} as const;

/** Task status as the board names it ("Pågår" in Swedish). */
export function boardStatus(t: InsightsT, status: string): string {
  return Object.hasOwn(boardStatuses, status)
    ? t(boardStatuses[status as keyof typeof boardStatuses])
    : status;
}

/** Task status in flow and report summaries ("Pågående" in Swedish). */
export function flowStatus(t: InsightsT, status: string): string {
  return Object.hasOwn(flowStatuses, status)
    ? t(flowStatuses[status as keyof typeof flowStatuses])
    : status;
}

/**
 * Sprint health as a badge. English shows any server status as sent; Swedish
 * names the known ones and uses `fallback` for the rest.
 */
export function healthStatus(
  t: InsightsT,
  language: Language,
  status: string,
  fallback: 'healthWatch' | 'healthHealthy',
): string {
  if (status === 'At risk') return t('healthAtRisk');
  if (status === 'Watch WIP' && fallback === 'healthHealthy')
    return t('flowWatch');
  if (status === 'Healthy') return t('healthHealthy');
  return language === 'EN' ? status : t(fallback);
}
