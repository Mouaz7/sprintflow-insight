import type { Language } from '../../api/contracts';

const english = {
  open: 'Search',
  openHint: 'Search and commands (Ctrl+K)',
  title: 'Search and commands',
  placeholder: 'Search tasks, stories, sprints, comments or type SF-123',
  inputLabel: 'Search or run a command',
  close: 'Close',
  results: 'Results',
  commands: 'Commands',
  loading: 'Searching…',
  noResults: 'No matches. Try fewer or different words.',
  loadMore: 'Show more results',
  resultCount: '{count} results',
  oneResult: '1 result',
  exactMatch: 'Exact match',
  inProject: 'in {project}',
  task: 'Task',
  story: 'Story',
  sprint: 'Sprint',
  comment: 'Comment',
  project: 'Project',
  goOverview: 'Go to overview',
  goBoard: 'Go to board',
  goBacklog: 'Go to backlog',
  goSprints: 'Go to sprints',
  goMetrics: 'Go to metrics',
  goProjects: 'Go to projects',
  newTask: 'New task',
  showShortcuts: 'Keyboard shortcuts',
  shortcutsTitle: 'Keyboard shortcuts',
  shortcutsIntro:
    'Shortcuts work when you are not typing in a field. Press Esc to close a dialog.',
  keyPalette: 'Open search and commands',
  keySlash: 'Open search',
  keyHelp: 'Show this help',
  keyNewTask: 'New task',
  keyThen: 'then',
  navigation: 'Navigation',
  general: 'General',
  paletteKeys: 'In the search',
  keyArrows: 'Move between results',
  keyEnter:
    'Open the selected result; a task key such as SF-123 opens directly',
  notFound: 'No task {key} in your projects.',
} as const;

export type SearchTextKey = keyof typeof english;

const swedish: Record<SearchTextKey, string> = {
  open: 'Sök',
  openHint: 'Sök och kommandon (Ctrl+K)',
  title: 'Sök och kommandon',
  placeholder:
    'Sök uppgifter, stories, sprintar, kommentarer eller skriv SF-123',
  inputLabel: 'Sök eller kör ett kommando',
  close: 'Stäng',
  results: 'Träffar',
  commands: 'Kommandon',
  loading: 'Söker…',
  noResults: 'Inga träffar. Prova färre eller andra ord.',
  loadMore: 'Visa fler träffar',
  resultCount: '{count} träffar',
  oneResult: '1 träff',
  exactMatch: 'Exakt träff',
  inProject: 'i {project}',
  task: 'Uppgift',
  story: 'Story',
  sprint: 'Sprint',
  comment: 'Kommentar',
  project: 'Projekt',
  goOverview: 'Gå till översikt',
  goBoard: 'Gå till tavlan',
  goBacklog: 'Gå till backlog',
  goSprints: 'Gå till sprintar',
  goMetrics: 'Gå till mätvärden',
  goProjects: 'Gå till projekt',
  newTask: 'Ny uppgift',
  showShortcuts: 'Kortkommandon',
  shortcutsTitle: 'Kortkommandon',
  shortcutsIntro:
    'Kortkommandona fungerar när du inte skriver i ett fält. Tryck Esc för att stänga en dialog.',
  keyPalette: 'Öppna sök och kommandon',
  keySlash: 'Öppna sök',
  keyHelp: 'Visa den här hjälpen',
  keyNewTask: 'Ny uppgift',
  keyThen: 'sedan',
  navigation: 'Navigering',
  general: 'Allmänt',
  paletteKeys: 'I sökrutan',
  keyArrows: 'Flytta mellan träffar',
  keyEnter: 'Öppna vald träff; ett uppgifts-id som SF-123 öppnas direkt',
  notFound: 'Uppgiften {key} finns inte i dina projekt.',
};

const dictionaries: Record<Language, Record<SearchTextKey, string>> = {
  EN: english,
  SV: swedish,
};

export type SearchText = (
  key: SearchTextKey,
  values?: Record<string, string | number>,
) => string;

/** Typed en/sv texts for the search feature; values fill `{name}` placeholders. */
export function searchText(language: Language): SearchText {
  const dictionary = dictionaries[language];
  return (key, values = {}) =>
    dictionary[key].replace(/\{(\w+)\}/g, (whole, name: string) =>
      name in values ? String(values[name]) : whole,
    );
}
