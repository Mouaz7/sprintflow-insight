import { defineLexicon } from '../../shared/i18n/lexicon';

export const fieldsText = defineLexicon({
  EN: {
    title: 'Custom fields',
    eyebrow: 'PROJECT DETAILS',
    description:
      'Define the extra information your project needs. Fill in values on saved tasks, then use them in board and search filters and CSV exports.',
    choose: 'Choose or create a project to manage its custom fields.',
    add: 'New custom field',
    edit: 'Edit custom field',
    remove: 'Delete field',
    empty: 'No custom fields yet',
    emptyHint:
      'An owner or administrator can add up to 20 fields for this project.',
    readonly:
      'You can read the fields. An owner or administrator can manage them in an active project.',
    name: 'Field name',
    kind: 'Field type',
    options: 'Options, one per line',
    nameExample: 'Cost center',
    optionsExample: 'Ready\nIn progress\nWaiting',
    help: 'Choose a short name and a type. Select fields need at least one unique option. Numbers keep exact decimal precision; user fields refer to current project members.',
    immutable:
      'A saved field keeps its type. Create a new field to use a different type.',
    save: 'Save field',
    saving: 'Saving…',
    cancel: 'Cancel',
    tasks: 'Back to tasks',
    failed: 'Could not load custom fields. Check the connection and try again.',
    saveFailed:
      'Could not save the field. Check its name, type and options, then try again.',
    conflict:
      'This field changed. Close and reopen it to review the latest version; your draft is still here.',
    saved: 'Custom field saved.',
    deleted: 'Custom field and its values deleted.',
    deleteConfirm: ({ name }: { name: string }) =>
      `Delete “${name}” and all its saved task values?`,
    count: ({ count }: { count: number }) => `${count} of 20 project fields`,
  },
  SV: {
    title: 'Anpassade fält',
    eyebrow: 'PROJEKTDETALJER',
    description:
      'Definiera den extra information projektet behöver. Ange värden på sparade uppgifter och använd dem sedan i tavlans filter, sökningen och CSV-exporten.',
    choose: 'Välj eller skapa ett projekt för att hantera dess anpassade fält.',
    add: 'Nytt anpassat fält',
    edit: 'Redigera anpassat fält',
    remove: 'Ta bort fält',
    empty: 'Inga anpassade fält ännu',
    emptyHint:
      'En ägare eller administratör kan lägga till upp till 20 fält i projektet.',
    readonly:
      'Du kan läsa fälten. En ägare eller administratör kan hantera dem i ett aktivt projekt.',
    name: 'Fältnamn',
    kind: 'Fälttyp',
    options: 'Val, ett per rad',
    nameExample: 'Kostnadsställe',
    optionsExample: 'Redo\nPågår\nVäntar',
    help: 'Välj ett kort namn och en typ. Valfält behöver minst ett unikt val. Tal behåller sin exakta decimalprecision; användarfält gäller nuvarande projektmedlemmar.',
    immutable:
      'Ett sparat fält behåller sin typ. Skapa ett nytt fält för att använda en annan typ.',
    save: 'Spara fält',
    saving: 'Sparar…',
    cancel: 'Avbryt',
    tasks: 'Tillbaka till uppgifter',
    failed:
      'Kunde inte läsa anpassade fält. Kontrollera anslutningen och försök igen.',
    saveFailed:
      'Kunde inte spara fältet. Kontrollera namn, typ och val och försök igen.',
    conflict:
      'Fältet har ändrats. Stäng och öppna det igen för att granska den senaste versionen; ditt utkast finns kvar.',
    saved: 'Anpassat fält sparat.',
    deleted: 'Fältet och dess värden har tagits bort.',
    deleteConfirm: ({ name }) =>
      `Ta bort ”${name}” och alla dess sparade uppgiftsvärden?`,
    count: ({ count }) => `${count} av 20 projektfält`,
  },
});
