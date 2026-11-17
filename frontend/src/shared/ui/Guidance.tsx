import type { Language, WorkspaceView } from '../../api/contracts';
import { useLanguage, useT } from '../i18n/LanguageProvider';
import { translator } from '../i18n/lexicon';
import { uiText } from './i18n';

type Copy = readonly [sv: string, en: string];
const text = (copy: Copy, language: Language) =>
  copy[language === 'SV' ? 0 : 1];

const examples = {
  projectName: ['Inloggning till kundportalen', 'Customer portal login'],
  projectDescription: [
    'Bygg en säker inloggning så att kunder kan se sina beställningar.',
    'Build a secure login so customers can view their orders.',
  ],
  storyTitle: ['Kunden kan logga in', 'Customers can sign in'],
  storyDescription: [
    'Kunden loggar in med e-post och lösenord. Visa ett tydligt fel om uppgifterna är fel.',
    'Customers sign in with email and password. Show a clear error if the credentials are incorrect.',
  ],
  criteria: [
    'Rätt lösenord loggar in.\nFel lösenord visar ett fel.\nTomma fält kan inte skickas.',
    'Valid password signs in.\nWrong password shows an error.\nEmpty fields cannot be sent.',
  ],
  sprintName: ['Sprint 1 – Inloggning', 'Sprint 1 – Login'],
  sprintGoal: [
    'Kunder ska kunna logga in och få hjälp om lösenordet är fel.',
    'Customers can sign in and get clear feedback if their password is incorrect.',
  ],
  taskTitle: ['Bygg inloggningsformuläret', 'Build the login form'],
  taskDescription: ['E-post och lösenord', 'Email and password fields'],
  blockReason: [
    'Väntar på API. Följ upp på fredag.',
    'Waiting for the API. Follow up on Friday.',
  ],
  update: [
    'Inloggningsformuläret är byggt och redo för granskning.',
    'The login form is built and ready for review.',
  ],
  retro: [
    'Vi upptäckte fel tidigt genom att testa tillsammans. Fortsätt med gemensamma tester nästa sprint.',
    'Testing together helped us find errors early. Continue joint testing next sprint.',
  ],
  filter: ['Mina blockerade uppgifter', 'My blocked tasks'],
  comment: [
    'Formuläret är klart. Kan någon granska felmeddelandet för fel lösenord?',
    'The form is ready. Could someone review the incorrect-password message?',
  ],
  label: ['Frontend', 'Frontend'],
  checklist: [
    'Kontrollera att fel lösenord visar ett tydligt felmeddelande',
    'Check that an incorrect password shows a clear error',
  ],
  name: ['Alex Andersson', 'Alex Morgan'],
  jobTitle: ['Frontendutvecklare', 'Frontend developer'],
  bio: [
    'Jag bygger användargränssnitt och hjälper teamet med tillgänglighet.',
    'I build user interfaces and help the team with accessibility.',
  ],
} satisfies Record<string, Copy>;

export function fieldExample(field: keyof typeof examples, language: Language) {
  return `${translator(uiText, language)('example')}${text(examples[field], language)}`;
}

/** `fieldExample` in the current language. */
export function useFieldExample() {
  const { language } = useLanguage();
  return (field: keyof typeof examples) => fieldExample(field, language);
}

type Field = readonly [label: Copy, description: Copy];
type Guide = { intro: Copy; fields: readonly Field[] };
const projectChoice: Field = [
  ['Projekt', 'Project'],
  [
    'När du skapar: välj var arbetet ska sparas. Ett byte rensar val från det tidigare projektet. Redan sparat arbete stannar i sitt projekt.',
    'When creating work, choose where to save it. Changing the project clears previous relationship choices. Saved work stays in its project.',
  ],
];
const points: Field = [
  ['Poäng', 'Story points'],
  [
    'Uppskatta arbetets storlek jämfört med annat arbete, inte antal timmar. Till exempel: 1 är litet, 3 är större och 8 är stort. Kom överens om skalan i teamet.',
    'Estimate the size of the work relative to other work, not hours. For example, 1 is small, 3 is larger and 8 is large. Agree on the scale with your team.',
  ],
];
const formGuides = {
  epic: {
    intro: [
      'En epic samlar stories kring ett leveransmål. Uppgifter och storypoäng räknas ihop automatiskt.',
      'An epic groups stories around a delivery goal. Tasks and story points are rolled up automatically.',
    ],
    fields: [
      [
        ['Namn och beskrivning', 'Title and description'],
        [
          'Beskriv resultatet ni vill leverera. Färgen skiljer staplar åt; namnet visas alltid som text.',
          'Describe the outcome you want to deliver. Colour distinguishes bars; the name is always shown as text.',
        ],
      ],
      [
        ['Planerade datum', 'Planned dates'],
        [
          'Datum är valfria. Saknas de används sprintarnas datum. Måldatumet används för PERT-prognosen.',
          'Dates are optional. Sprint dates provide a fallback. The target date is used for the PERT forecast.',
        ],
      ],
    ],
  },
  storyEpic: {
    intro: [
      'Koppla en story till en epic, eller välj Ingen epic för att ta bort kopplingen.',
      'Assign a story to an epic, or select No epic to detach it.',
    ],
    fields: [
      [
        ['Story och epic', 'Story and epic'],
        [
          'Sök efter storyns namn. Sprint och uppgifter finns kvar när epicen ändras.',
          'Search by the story’s title. Changing its epic preserves its sprint and tasks.',
        ],
      ],
    ],
  },
  dependency: {
    intro: [
      'Ett beroende visar vilken uppgift som måste bli klar först.',
      'A dependency shows which task must finish first.',
    ],
    fields: [
      [
        ['Blockerare och väntande uppgift', 'Blocker and waiting task'],
        [
          'Välj två uppgifter i samma projekt. Cykler avvisas med hela kedjan. Klara blockerare blockerar inte längre tavlan.',
          'Choose two tasks in the same project. Cycles are rejected with their chain. Finished blockers no longer block the board.',
        ],
      ],
    ],
  },
  estimate: {
    intro: [
      'Trepunktsestimat beskriver osäkerheten i uppgiftens återstående arbetstid.',
      'Three-point estimates describe uncertainty in a task’s remaining duration.',
    ],
    fields: [
      [
        [
          'Optimistisk, troligast och pessimistisk',
          'Optimistic, most likely and pessimistic',
        ],
        [
          'Ange arbetsdagar i stigande ordning. PERT använder viktat medelvärde och varians. Prognosen är ungefärlig och kräver att alla återstående uppgifter uppskattas.',
          'Enter working days in increasing order. PERT uses a weighted mean and variance. The forecast is approximate and requires estimates for all remaining tasks.',
        ],
      ],
    ],
  },
  project: {
    intro: [
      'Ett projekt samlar teamet och arbetet mot ett gemensamt mål. Skriv vad ni vill bygga och varför.',
      'A project brings the team and its work together around a goal. Describe what you want to build and why.',
    ],
    fields: [
      [['Projektnamn', 'Project name'], examples.projectName],
      [['Beskrivning', 'Description'], examples.projectDescription],
      [
        ['Projektmall', 'Project template'],
        [
          'Tomt projekt: börja utan innehåll. Scrum: få en planerad sprint, en berättelse och tre uppgifter. Kanban: få tre uppgifter för löpande arbete.',
          'Blank: start without content. Scrum: start with a planned sprint, a story and three tasks. Kanban: start with three tasks for continuous work.',
        ],
      ],
    ],
  },
  story: {
    intro: [
      'En berättelse beskriver vad användaren ska kunna göra. Uppgifter beskriver sedan hur ni bygger det.',
      'A story describes what a user should be able to do. Tasks describe how you will build it.',
    ],
    fields: [
      projectChoice,
      [
        ['Titel och beskrivning', 'Title and description'],
        [
          'Exempel: Som kund vill jag kunna logga in för att se mina beställningar. Beskriv vem som behöver funktionen och varför.',
          'Example: As a customer, I want to log in so I can view my orders. Explain who needs the feature and why.',
        ],
      ],
      [
        ['Acceptanskriterier', 'Acceptance criteria'],
        [
          'Skriv sådant som går att kontrollera. Exempel: rätt lösenord öppnar startsidan; fel lösenord visar ett fel; tomma fält stoppas.',
          'Write checkable outcomes. Example: a valid password opens the dashboard; an incorrect password shows an error; empty fields are rejected.',
        ],
      ],
      points,
      [
        ['Prioritet', 'Priority'],
        [
          'Hög = bör göras först. Medel = viktigt men kan vänta. Låg = görs när viktigare arbete är klart.',
          'High = address first. Medium = important but can wait. Low = address after higher-priority work.',
        ],
      ],
      [
        ['Affärsvärde (1–5)', 'Business value (1–5)'],
        [
          'Hur stor nytta ger resultatet? 1 = liten nytta, 5 = mycket stor nytta. Det är inte samma sak som arbetsmängd.',
          'How useful is the result? 1 = low value, 5 = very high value. This is separate from the amount of work.',
        ],
      ],
      [
        ['Sprint', 'Sprint'],
        [
          'Välj Produktbacklog om arbetet inte är planerat ännu. Väljer du en sprint följer alla kopplade uppgifter med, även när du flyttar berättelsen tillbaka till backloggen.',
          'Choose Product backlog if the work is not scheduled yet. Linked tasks follow the story’s sprint, including when you move it back to the backlog.',
        ],
      ],
    ],
  },
  sprint: {
    intro: [
      'En sprint är en tidsperiod med ett gemensamt mål. Planera vilket resultat teamet ska hinna leverera.',
      'A sprint is a time period with a shared goal. Plan the result your team aims to deliver.',
    ],
    fields: [
      projectChoice,
      [
        ['Namn och sprintmål', 'Name and sprint goal'],
        [
          'Exempel: Sprint 1 – Inloggning. Mål: kunder kan logga in och får tydliga felmeddelanden.',
          'Example: Sprint 1 – Login. Goal: customers can sign in and receive clear error messages.',
        ],
      ],
      [
        ['Status', 'Status'],
        [
          'Planerad = inte startad. Aktiv = pågår; högst en per projekt. Slutförd = avslutad. Avbruten = arbetet har stoppats.',
          'Planned = not started. Active = in progress; only one per project. Completed = finished. Cancelled = stopped.',
        ],
      ],
      [
        ['Planerade poäng', 'Planned points'],
        [
          'Teamets mål för arbetsmängden, till exempel 13 poäng. Det fylls inte i automatiskt från berättelserna.',
          'The team’s planned amount of work, for example 13 points. It is not automatically filled from the stories.',
        ],
      ],
      [
        ['Datum', 'Dates'],
        [
          'Välj när sprinten börjar och slutar. Slutdatum får inte ligga före startdatum. Skriv ÅÅÅÅ-MM-DD eller använd kalenderknappen.',
          'Choose when the sprint starts and ends. The end cannot be before the start. Enter YYYY-MM-DD or use the calendar button.',
        ],
      ],
    ],
  },
  task: {
    intro: [
      'En uppgift är ett konkret arbetssteg, till exempel att bygga formuläret som berättelsen behöver.',
      'A task is a concrete step, such as building the form needed by a story.',
    ],
    fields: [
      projectChoice,
      [
        ['Titel och beskrivning', 'Title and description'],
        [
          'Exempel: Bygg formuläret för inloggning. Beskriv vilka fält och felmeddelanden som ska läggas till.',
          'Example: Build the login form. Describe which fields and error messages to add.',
        ],
      ],
      points,
      [
        ['Slutdatum', 'Due date'],
        [
          'Valfritt: dagen då uppgiften bör vara klar. Datumet visas i kalendern. Lämna tomt om det inte finns en deadline.',
          'Optional: the day this task should be ready. It appears in the calendar. Leave empty if there is no deadline.',
        ],
      ],
      [
        ['Berättelse och sprint', 'User story and sprint'],
        [
          'Välj berättelsen som uppgiften hjälper till att uppfylla. Sprinten följer berättelsen och kan inte väljas separat. Utan berättelse kan du planera uppgiften själv.',
          'Choose the story this task helps deliver. Its sprint follows the story and cannot be chosen separately. A standalone task can be planned independently.',
        ],
      ],
      [
        ['Ansvarig', 'Assignee'],
        [
          'Välj personen som tar nästa steg. Utan ansvarig är uppgiften ännu inte tilldelad.',
          'Choose the person responsible for the next step. Unassigned means nobody has been assigned yet.',
        ],
      ],
    ],
  },
  block: {
    intro: [
      'Förklara vad som hindrar arbetet och vad som behövs för att komma vidare.',
      'Explain what is stopping the work and what is needed to continue.',
    ],
    fields: [[['Orsak till blockering', 'Block reason'], examples.blockReason]],
  },
  activity: {
    intro: [
      'Skriv en kort, saklig beskrivning av vad som hände i projektet.',
      'Write a brief, factual description of what happened in the project.',
    ],
    fields: [[['Uppdateringstext', 'Update summary'], examples.update]],
  },
  retro: {
    intro: [
      'Sammanfatta vad teamet lärde sig av den valda sprinten och vad ni vill förbättra.',
      'Describe what the team learned from the selected sprint and what to improve.',
    ],
    fields: [
      [
        ['Kategori', 'Category'],
        [
          'Välj det som gick bra, det som kan förbättras eller en konkret åtgärd att följa upp.',
          'Choose what went well, what could improve, or a concrete action to follow up.',
        ],
      ],
      [['Anteckning', 'Note'], examples.retro],
    ],
  },
  filter: {
    intro: [
      'Spara de filter som är valda på tavlan så att du snabbt kan återanvända dem.',
      'Save the filters currently selected on the board so you can reuse them.',
    ],
    fields: [
      [
        ['Filternamn', 'Filter name'],
        [
          'Exempel: Mina blockerade uppgifter. Namnet beskriver urvalet; det ändrar inte uppgifterna.',
          'Example: My blocked tasks. The name describes the selection; it does not change the tasks.',
        ],
      ],
    ],
  },
  comment: {
    intro: [
      'Skriv en fråga, återkoppling eller information som hjälper teamet med just den här uppgiften.',
      'Write a question, feedback or information that helps the team with this task.',
    ],
    fields: [[['Kommentar', 'Comment'], examples.comment]],
  },
  member: {
    intro: [
      'Ge en registrerad användare tillgång till projektet med rätt behörighet.',
      'Give a registered user access to the project with the appropriate permissions.',
    ],
    fields: [
      [
        ['E-post', 'Email'],
        [
          'Ange adressen som personen registrerade sitt konto med, till exempel alex@example.com.',
          'Enter the email used to register their account, for example alex@example.com.',
        ],
      ],
      [
        ['Roll', 'Role'],
        [
          'Admin hanterar projektinställningar och medlemmar. Medlem arbetar med uppgifterna. Läsbehörig kan läsa men inte ändra. Bara ägaren kan överlåta projektet.',
          'Admin manages project settings and members. Member works on tasks. Viewer can read but cannot edit. Only the owner can transfer ownership.',
        ],
      ],
    ],
  },
  attachment: {
    intro: [
      'Bifoga material som hjälper teamet att utföra uppgiften.',
      'Attach material that helps the team complete this task.',
    ],
    fields: [
      [
        ['Länk', 'Link'],
        [
          'Klistra in en fullständig https-adress, till exempel till en skiss eller kravbeskrivning.',
          'Paste a complete https address, for example a design or requirements document.',
        ],
      ],
      [
        ['Fil', 'File'],
        [
          'Välj en bild eller PDF, till exempel en skärmbild av inloggningsformuläret. Uppladdade filer kan läsas av projektets medlemmar.',
          'Choose an image or PDF, for example a screenshot of the login form. Project members can read uploaded files.',
        ],
      ],
    ],
  },
  profile: {
    intro: [
      'Hjälp teamet att känna igen dig och förstå vad du arbetar med.',
      'Help your team recognise you and understand what you work on.',
    ],
    fields: [
      [
        ['Namn och roll', 'Name and role'],
        [
          'Exempel: Alex Andersson, frontendutvecklare. Rollen här beskriver ditt arbete och ändrar inga projektbehörigheter.',
          'Example: Alex Morgan, frontend developer. This role describes your work and does not change project permissions.',
        ],
      ],
      [['Om dig', 'About you'], examples.bio],
    ],
  },
} satisfies Record<string, Guide>;

export function FormGuide({ topic }: { topic: keyof typeof formGuides }) {
  const guide: Guide = formGuides[topic];
  const { language } = useLanguage();
  const t = useT(uiText);
  return (
    <aside className="form-guide" aria-label={t('formHelp')}>
      <p>{text(guide.intro, language)}</p>
      <details className="guidance">
        <summary>{t('explanations')}</summary>
        <dl>
          {guide.fields.map(([label, description]) => (
            <div key={label[1]}>
              <dt>{text(label, language)}</dt>
              <dd>{text(description, language)}</dd>
            </div>
          ))}
        </dl>
      </details>
    </aside>
  );
}

const pageGuides = {
  roadmap: [
    'Samla stories i epics och se planerade datum, sprintar och beroenden. En pil betyder att den första uppgiften måste vara klar före den andra. PERT använder tre uppskattningar i arbetsdagar; sannolikheten är ungefärlig och visas först när arbetet är uppdelat och uppskattat.',
    'Group stories into epics and see planned dates, sprints and dependencies. An arrow means the first task must finish before the second. PERT uses three estimates in working days; probability is approximate and appears only when work is broken down and estimated.',
  ],
  overview: [
    'Välj först ett projekt. Skapa berättelser i backloggen, planera dem i sprintar och följ uppgifternas status på tavlan. Översikten visar hur arbetet går i det valda projektet.',
    'Choose a project first. Create stories in the backlog, plan them into sprints and track task status on the board. The overview summarises the selected project.',
  ],
  projects: [
    'Skapa ett projekt för ett gemensamt mål, till exempel en inloggningssida. Välj projektet för att arbeta i det. Ägare och administratörer kan lägga till registrerade teammedlemmar.',
    'Create a project for a shared goal, such as a login page. Select it to work in it. Owners and admins can add registered team members.',
  ],
  backlog: [
    'Lägg till önskade resultat som berättelser. Skriv acceptanskriterier som visar när resultatet är klart. Produktbacklog betyder att berättelsen ännu inte är planerad i en sprint.',
    'Add desired outcomes as stories. Write acceptance criteria that show when each outcome is ready. Product backlog means the story has not been scheduled into a sprint.',
  ],
  sprints: [
    'Skapa en tidsperiod och ett tydligt sprintmål. Planera berättelser via backloggen; deras uppgifter följer med. Bara en sprint per projekt kan vara aktiv samtidigt.',
    'Create a time period and a clear sprint goal. Plan stories through the backlog; their tasks follow them. Only one sprint per project can be active at a time.',
  ],
  tasks: [
    'Dela upp berättelser i konkreta arbetssteg. Flytta uppgifter från Att göra via Pågår och Granskning till Klart. Obligatoriska checklistesteg måste vara klara före Klart. Filtren ändrar bara vad som visas.',
    'Break stories into concrete steps. Move tasks from To Do through In Progress and Review to Done. Required checklist steps must be complete before Done. Filters only change what is shown.',
  ],
  metrics: [
    'Välj projekt och vid behov sprint. Poäng beskriver uppskattad arbetsmängd, inte timmar. Aktuella mått visar läget nu; avslutade sprintars leverans visar vad som var klart vid avslutet.',
    'Choose a project and optionally a sprint. Points describe estimated effort, not hours. Current metrics show the state now; completed-sprint delivery shows what was done at closure.',
  ],
  updates: [
    'Här visas vem som gjorde vad och när i projektet. Använd historiken för att följa förändringar. Bara ägare och administratörer kan redigera eller radera uppdateringar.',
    'See who did what and when in the project. Use this history to follow changes. Only owners and admins can edit or delete updates.',
  ],
  profile: [
    'Fyll i hur teamet ska se ditt namn, din arbetsroll och din presentation. Arbetsrollen ger inga projektbehörigheter. Under Säkerhet kan du byta lösenord och hantera ditt konto.',
    'Set the name, job title and introduction your team sees. Your job title does not grant project permissions. Use Security to change your password and manage your account.',
  ],
  calendar: [
    'Kalendern visar uppgifter med slutdatum och projektets sprintperioder. Saknas en uppgift? Lägg till slutdatum i uppgiften och kontrollera att rätt projekt är valt.',
    'The calendar shows tasks with due dates and the project’s sprint periods. Missing a task? Add its due date and check that the correct project is selected.',
  ],
  retrospective: [
    'Välj sprinten ni vill följa upp. Skriv vad som gick bra, vad ni vill förbättra och konkreta åtgärder. Exempel: testa tillsammans tidigare i nästa sprint.',
    'Choose the sprint to review. Record what went well, what to improve and concrete actions. Example: test together earlier in the next sprint.',
  ],
  trash: [
    'Återställ sådant som togs bort av misstag. Återställ föräldraprojektet, sprinten eller berättelsen först om ett barn inte kan återställas. Permanent radering kan inte ångras och kräver ägarbehörighet.',
    'Restore work removed by mistake. Restore the parent project, sprint or story first if a child cannot be restored. Permanent deletion cannot be undone and requires owner permission.',
  ],
} satisfies Record<WorkspaceView, Copy>;

export function PageGuide({ view }: { view: WorkspaceView }) {
  const { language } = useLanguage();
  const t = useT(uiText);
  return (
    <details className="guidance page-guide">
      <summary>{t('howPageWorks')}</summary>
      <p>{text(pageGuides[view], language)}</p>
    </details>
  );
}
