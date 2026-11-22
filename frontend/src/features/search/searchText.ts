import type { Language } from '../../api/contracts';
import { icu } from '../../shared/i18n/icu';
import {
  defineLexicon,
  translator,
  type Translate,
} from '../../shared/i18n/lexicon';

export const searchLexicon = defineLexicon({
  EN: {
    open: 'Search',
    openHint: 'Search and commands (Ctrl+K)',
    title: 'Search and commands',
    placeholder:
      'Search tasks, stories, epics, sprints, comments or type SF-123',
    inputLabel: 'Search or run a command',
    close: 'Close',
    results: 'Results',
    commands: 'Commands',
    loading: 'Searching…',
    noResults: 'No matches. Try fewer or different words.',
    loadMore: 'Show more results',
    resultCount: icu('searchResultCount'),
    exactMatch: 'Exact match',
    inProject: ({ project }: { project: string }) => `in ${project}`,
    epic: 'Epic',
    task: 'Task',
    story: 'Story',
    sprint: 'Sprint',
    comment: 'Comment',
    project: 'Project',
    goOverview: 'Go to overview',
    goBoard: 'Go to board',
    goBacklog: 'Go to backlog',
    goSprints: 'Go to sprints',
    goRoadmap: 'Go to roadmap',
    goMetrics: 'Go to metrics',
    goProjects: 'Go to projects',
    newTask: 'New task',
    showShortcuts: 'Keyboard shortcuts',
    shortcutsTitle: 'Keyboard shortcuts',
    shortcutsIntro:
      'Letter and symbol shortcuts work outside fields when enabled. Press Esc to close this dialog.',
    characterShortcuts: 'Enable shortcuts made only of letters or symbols',
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
    notFound: ({ key }: { key: string }) => `No task ${key} in your projects.`,
  },
  SV: {
    open: 'Sök',
    openHint: 'Sök och kommandon (Ctrl+K)',
    title: 'Sök och kommandon',
    placeholder:
      'Sök uppgifter, stories, epics, sprintar, kommentarer eller skriv SF-123',
    inputLabel: 'Sök eller kör ett kommando',
    close: 'Stäng',
    results: 'Träffar',
    commands: 'Kommandon',
    loading: 'Söker…',
    noResults: 'Inga träffar. Prova färre eller andra ord.',
    loadMore: 'Visa fler träffar',
    resultCount: icu('searchResultCount'),
    exactMatch: 'Exakt träff',
    inProject: ({ project }: { project: string }) => `i ${project}`,
    epic: 'Epic',
    task: 'Uppgift',
    story: 'Story',
    sprint: 'Sprint',
    comment: 'Kommentar',
    project: 'Projekt',
    goOverview: 'Gå till översikt',
    goBoard: 'Gå till tavlan',
    goBacklog: 'Gå till backlog',
    goSprints: 'Gå till sprintar',
    goRoadmap: 'Gå till färdplan',
    goMetrics: 'Gå till mätvärden',
    goProjects: 'Gå till projekt',
    newTask: 'Ny uppgift',
    showShortcuts: 'Kortkommandon',
    shortcutsTitle: 'Kortkommandon',
    shortcutsIntro:
      'Kortkommandon med bokstäver och symboler fungerar utanför fält när de är aktiverade. Tryck Esc för att stänga dialogen.',
    characterShortcuts:
      'Aktivera kortkommandon med enbart bokstäver eller symboler',
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
    notFound: ({ key }: { key: string }) =>
      `Uppgiften ${key} finns inte i dina projekt.`,
  },
});

export type SearchTextKey = keyof typeof searchLexicon.EN;
export type SearchText = Translate<typeof searchLexicon.EN>;

export function searchText(language: Language): SearchText {
  return translator(searchLexicon, language);
}
