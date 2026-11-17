import { defineLexicon, type Translate } from '../../shared/i18n/lexicon';

export const roadmapText = defineLexicon({
  EN: {
    title: 'Roadmap',
    eyebrow: 'PLAN THE DELIVERY',
    description:
      'Connect epics, sprint dates and the work that must happen first.',
    noProject: 'Choose or create a project to plan its roadmap.',
    loading: 'Loading the roadmap…',
    loadFailed:
      'Could not load the roadmap. Check the connection and try again.',
    newEpic: 'New epic',
    editEpic: 'Edit epic',
    deleteEpic: 'Delete epic',
    epicTitle: 'Epic title',
    epicDescription: 'Description',
    color: 'Colour',
    startDate: 'Start date',
    targetDate: 'Target date',
    titleExample: 'E.g. Customer portal launch',
    descriptionExample: 'What should this release make possible?',
    save: 'Save',
    saved: 'Changes saved.',
    remove: 'Remove',
    removed: 'Removed.',
    saving: 'Saving…',
    selectEpic: 'Focus on an epic',
    allWork: 'Whole project',
    noEpics: 'Plan your first epic',
    noEpicsHint:
      'Create an epic and group stories into it. Task dependencies also work without epics.',
    readonly:
      'You can explore this roadmap. Changes need an active project and a writing role.',
    timeline: 'Delivery timeline',
    timelineHint:
      'Bars show planned dates and progress. Arrows connect epics with task dependencies. Scroll to explore; the table lists every date.',
    timelineTable: 'Dates and progress as a table',
    epic: 'Epic',
    sprint: 'Sprint',
    progress: 'Progress',
    noDates: 'Not scheduled',
    edit: 'Edit',
    assignStory: 'Story epic',
    story: 'Story',
    noEpic: 'No epic',
    chooseStory: 'Choose a story',
    noStories: 'Create stories in the backlog before assigning them.',
    dependencies: 'Task dependencies',
    addDependency: 'Add dependency',
    blocker: 'Must finish first',
    blocked: 'Waiting task',
    chooseTask: 'Choose a task',
    filter: 'Filter choices',
    choiceHint:
      'Showing up to 50 matches. Refine the search to find other work.',
    noTasks: 'Create tasks before adding dependencies or estimates.',
    noDependencies: 'No dependencies yet.',
    satisfied: 'Blocker finished',
    waiting: 'Waiting',
    externalBlocks: 'Outside the active sprint',
    externalBlock: ({ task, blocker }: { task: string; blocker: string }) =>
      `${task} is waiting for ${blocker} outside this sprint.`,
    estimates: 'Three-point estimates',
    editEstimate: 'Estimate a task',
    removeEstimate: 'Remove estimate',
    optimistic: 'Optimistic days',
    likely: 'Most likely days',
    pessimistic: 'Pessimistic days',
    estimateHint:
      'Use working days, Monday to Friday. Enter shortest, most likely and longest durations, with up to two decimals.',
    ordered:
      'Use optimistic ≤ most likely ≤ pessimistic, from 0 to 1000 working days.',
    datesOrdered: 'The target date must be on or after the start date.',
    forecast: 'PERT forecast',
    expectedDays: 'Expected working days',
    expectedFinish: 'Expected finish',
    deviation: 'Standard deviation',
    probability: 'Chance by the target date',
    forecastHint:
      'Approximate planning, using independent estimates along the expected critical path. Near-equal parallel paths can make the chance optimistic. Weekends are excluded; public holidays are not modelled.',
    estimateCoverage: ({
      estimated,
      total,
    }: {
      estimated: number;
      total: number;
    }) => `${estimated} of ${total} remaining tasks estimated`,
    unplanned: ({ count }: { count: number }, f) =>
      f.plural(count, {
        one: '# story still needs tasks.',
        other: '# stories still need tasks.',
      }),
    incomplete:
      'Break down the stories and estimate every remaining task before relying on a finish date or probability.',
    nothingRemaining: 'No remaining tasks in this scope.',
    criticalPath: 'Estimated critical path',
    criticalHint:
      'A delay on this chain delays the forecast. Outside blockers are included.',
    setTarget: 'Set an epic target date to see its approximate probability.',
    date: ({ value }: { value: string }, f) =>
      f.date(value.replace(/^\+(\d{5})-/, '+0$1-'), {
        timeZone: 'UTC',
        dateStyle: 'medium',
      }),
    number: ({ value }: { value: number }, f) =>
      f.number(value, { maximumFractionDigits: 2 }),
    percent: ({ value }: { value: number }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 1 }),
    epicCount: ({ count }: { count: number }) => `${count} epics`,
    storyProgress: ({ done, total }: { done: number; total: number }) =>
      `${done} / ${total} story points complete`,
    deleteConfirm: ({ title }: { title: string }) =>
      `Remove epic “${title}”? Its stories and tasks remain in the project.`,
    clearEstimateConfirm:
      'Remove this estimate? Its task will remain, and the forecast will show incomplete estimates.',
    cycle: ({ path }: { path: string }) =>
      `This would create a dependency cycle: ${path}.`,
    stale:
      'Someone else changed this item. Keep a copy of your draft, then close and reopen it to review the latest values.',
    denied: 'You cannot change this project. Check its status and your role.',
    notFound:
      'This work is no longer available in the project. Refresh and try again.',
    invalid: 'Check the selected work, dates and estimates, then try again.',
    conflict:
      'The change conflicts with the project’s current state. Refresh and review it.',
    failed:
      'The change could not be saved. Check the connection and try again.',
    showMore: 'Show more',
  },
  SV: {
    title: 'Färdplan',
    eyebrow: 'PLANERA LEVERANSEN',
    description:
      'Koppla ihop epics, sprintdatum och arbetet som måste bli klart först.',
    noProject: 'Välj eller skapa ett projekt för att planera dess färdplan.',
    loading: 'Laddar färdplanen…',
    loadFailed:
      'Färdplanen kunde inte hämtas. Kontrollera anslutningen och försök igen.',
    newEpic: 'Ny epic',
    editEpic: 'Redigera epic',
    deleteEpic: 'Radera epic',
    epicTitle: 'Epicens namn',
    epicDescription: 'Beskrivning',
    color: 'Färg',
    startDate: 'Startdatum',
    targetDate: 'Måldatum',
    titleExample: 'T.ex. Lansering av kundportalen',
    descriptionExample: 'Vad ska leveransen göra möjligt?',
    save: 'Spara',
    saved: 'Ändringarna sparades.',
    remove: 'Ta bort',
    removed: 'Borttaget.',
    saving: 'Sparar…',
    selectEpic: 'Fokusera på en epic',
    allWork: 'Hela projektet',
    noEpics: 'Planera din första epic',
    noEpicsHint:
      'Skapa en epic och samla stories i den. Uppgiftsberoenden fungerar även utan epics.',
    readonly:
      'Du kan utforska färdplanen. Ändringar kräver ett aktivt projekt och skrivbehörighet.',
    timeline: 'Leveransens tidslinje',
    timelineHint:
      'Staplar visar planerade datum och framsteg. Pilar kopplar ihop epics med uppgiftsberoenden. Rulla för att utforska; tabellen visar alla datum.',
    timelineTable: 'Datum och framsteg som tabell',
    epic: 'Epic',
    sprint: 'Sprint',
    progress: 'Framsteg',
    noDates: 'Inte schemalagd',
    edit: 'Redigera',
    assignStory: 'Storyns epic',
    story: 'Story',
    noEpic: 'Ingen epic',
    chooseStory: 'Välj en story',
    noStories: 'Skapa stories i backloggen innan du kopplar dem till epics.',
    dependencies: 'Uppgiftsberoenden',
    addDependency: 'Lägg till beroende',
    blocker: 'Måste bli klar först',
    blocked: 'Väntande uppgift',
    chooseTask: 'Välj en uppgift',
    filter: 'Filtrera val',
    choiceHint:
      'Visar högst 50 träffar. Förfina sökningen för att hitta annat arbete.',
    noTasks: 'Skapa uppgifter innan du lägger till beroenden eller estimat.',
    noDependencies: 'Inga beroenden ännu.',
    satisfied: 'Blockeraren är klar',
    waiting: 'Väntar',
    externalBlocks: 'Utanför den aktiva sprinten',
    externalBlock: ({ task, blocker }) =>
      `${task} väntar på ${blocker} utanför sprinten.`,
    estimates: 'Trepunktsestimat',
    editEstimate: 'Uppskatta en uppgift',
    removeEstimate: 'Ta bort estimat',
    optimistic: 'Optimistiska dagar',
    likely: 'Troligaste dagar',
    pessimistic: 'Pessimistiska dagar',
    estimateHint:
      'Använd arbetsdagar, måndag till fredag. Ange kortaste, troligaste och längsta tid med högst två decimaler.',
    ordered:
      'Använd optimistisk ≤ troligast ≤ pessimistisk, från 0 till 1000 arbetsdagar.',
    datesOrdered:
      'Måldatumet måste vara samma dag som startdatumet eller senare.',
    forecast: 'PERT-prognos',
    expectedDays: 'Förväntade arbetsdagar',
    expectedFinish: 'Förväntat slutdatum',
    deviation: 'Standardavvikelse',
    probability: 'Chans till måldatumet',
    forecastHint:
      'Ungefärlig planering med oberoende estimat längs den förväntade kritiska vägen. Nästan lika långa parallella vägar kan ge en för optimistisk chans. Helger räknas bort; helgdagar modelleras inte.',
    estimateCoverage: ({ estimated, total }) =>
      `${estimated} av ${total} återstående uppgifter uppskattade`,
    unplanned: ({ count }, f) =>
      f.plural(count, {
        one: '# story behöver fortfarande uppgifter.',
        other: '# stories behöver fortfarande uppgifter.',
      }),
    incomplete:
      'Dela upp stories och uppskatta varje återstående uppgift innan du använder slutdatumet eller sannolikheten.',
    nothingRemaining: 'Inga återstående uppgifter i det här urvalet.',
    criticalPath: 'Uppskattad kritisk väg',
    criticalHint:
      'En försening på den här kedjan försenar prognosen. Blockerare utifrån räknas med.',
    setTarget:
      'Ange ett måldatum för epicen för att se dess ungefärliga sannolikhet.',
    date: ({ value }, f) =>
      f.date(value.replace(/^\+(\d{5})-/, '+0$1-'), {
        timeZone: 'UTC',
        dateStyle: 'medium',
      }),
    number: ({ value }, f) => f.number(value, { maximumFractionDigits: 2 }),
    percent: ({ value }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 1 }),
    epicCount: ({ count }) => `${count} epics`,
    storyProgress: ({ done, total }) => `${done} / ${total} storypoäng klara`,
    deleteConfirm: ({ title }) =>
      `Ta bort epicen ”${title}”? Dess stories och uppgifter finns kvar i projektet.`,
    clearEstimateConfirm:
      'Ta bort estimatet? Uppgiften finns kvar och prognosen visar ofullständiga estimat.',
    cycle: ({ path }) => `Det skulle skapa en beroendecykel: ${path}.`,
    stale:
      'Någon annan ändrade detta. Behåll en kopia av ditt utkast och stäng och öppna igen för att granska de senaste värdena.',
    denied:
      'Du kan inte ändra projektet. Kontrollera dess status och din roll.',
    notFound:
      'Arbetet finns inte längre tillgängligt i projektet. Uppdatera och försök igen.',
    invalid: 'Kontrollera valt arbete, datum och estimat och försök igen.',
    conflict:
      'Ändringen krockar med projektets nuvarande läge. Uppdatera och granska det.',
    failed:
      'Ändringen kunde inte sparas. Kontrollera anslutningen och försök igen.',
    showMore: 'Visa fler',
  },
});

export type RoadmapText = Translate<typeof roadmapText.EN>;
