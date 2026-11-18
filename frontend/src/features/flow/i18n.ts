import { defineLexicon, type Translate } from '../../shared/i18n/lexicon';

export const flowText = defineLexicon({
  EN: {
    title: 'Flow analysis',
    eyebrow: 'UNDERSTAND THE WORK',
    description:
      'See where work gathers, how long delivery takes and which tasks need attention.',
    noProject: 'Choose or create a project to analyse its flow.',
    loading: 'Loading flow history…',
    failed: 'Could not load flow history. Check the connection and try again.',
    window: 'History window',
    windowDays: ({ count }: { count: number }, f) =>
      f.plural(count, { one: '# day', other: '# days' }),
    windowHint:
      'UTC calendar days, ending now. Percentiles describe tasks completed in this window; charts include the history needed before it.',
    missing: ({ count }: { count: number }, f) =>
      f.plural(count, {
        one: '# task lacks usable history. Historical charts and times exclude it; current column counts still include it.',
        other:
          '# tasks lack usable history. Historical charts and times exclude them; current column counts still include them.',
      }),
    noHistory: 'No recorded flow yet',
    noHistoryHint:
      'Create tasks and move them across the board. Times appear when the history supports them.',
    completed: 'Completed tasks',
    rate: 'Completions per day',
    cycle: 'Cycle time',
    lead: 'Lead time',
    hours: 'Hours',
    cycleHint:
      'Elapsed time in the latest uninterrupted In Progress / Review run ending in Done. Returning to To Do or reopening starts a new run.',
    leadHint:
      'Elapsed time from task creation to its latest completion. Durations without valid timestamps are omitted.',
    distributionHint:
      'Percentiles use completed tasks only. Ongoing slow tasks are not in that sample; compare their ages below.',
    mean: 'Mean',
    p50: '50th percentile',
    p85: '85th percentile',
    p95: '95th percentile',
    samples: ({ count }: { count: number }, f) =>
      f.plural(count, { one: '# observation', other: '# observations' }),
    cfd: 'Cumulative flow',
    cfdHint:
      'Tasks in each column at the end of each UTC day. Today ends at the report time. This uses current project scope; later trash or deletion can change older counts.',
    toDo: 'To Do',
    inProgress: 'In Progress',
    review: 'Review',
    done: 'Done',
    unknownStatus: 'Unknown column',
    columns: 'Column pressure',
    current: 'Current tasks',
    averageWip: 'Average WIP',
    limit: 'WIP limit',
    noLimit: 'No limit',
    overLimit: 'Over the WIP limit',
    pressure: 'Potential bottleneck',
    pressureHint:
      'An active column over its limit is highlighted first; otherwise the active column with the highest time-averaged WIP. This is a prompt to investigate.',
    configure: 'Set WIP limits',
    limitsTitle: 'Board WIP limits',
    limitHelp:
      'Optional task counts from 1 to 999. Leave empty for no limit. Limits warn; they do not prevent task movement. Only owners and admins can change them.',
    readonly:
      'You can read this analysis. WIP changes need an owner or admin in an active project.',
    save: 'Save limits',
    saving: 'Saving…',
    stale:
      'Another administrator changed the limits. Your draft is kept. Close and reopen to compare it with the latest settings.',
    denied:
      'You cannot change these limits. Check the project status and your role.',
    invalid: 'Use whole task counts from 1 to 999, or leave the field empty.',
    saveFailed:
      'Could not save the limits. Check the connection and try again.',
    aging: 'Aging work in progress',
    ageHint:
      'Age is time in the current uninterrupted active run; column age starts at the current column entry. Bands compare this with completed-task cycle times.',
    task: 'Task',
    age: 'Active age (hours)',
    columnAge: 'Column age (hours)',
    band: 'Compared with completed work',
    below50: 'At or below the 50th percentile',
    below85: 'Between the 50th and 85th percentiles',
    below95: 'Between the 85th and 95th percentiles',
    above95: 'Above the 95th percentile',
    noReference: 'No completed reference',
    noAging: 'No tasks currently in progress or review.',
    more: 'Show more',
    xmr: 'Cycle-time XmR chart',
    individuals: 'Individual cycle times (hours)',
    ranges: 'Moving ranges (hours)',
    xmrHint:
      'Descriptive limits use the entire selected sample. Signals are not deadlines or proof of a special cause. Autocorrelation, skew and several rules can increase false alarms; inspect the work and context.',
    tooFew: ({ count }: { count: number }) =>
      `At least ${count} completed cycle times are needed to draw this descriptive chart.`,
    chartWindow:
      'Charts show the latest 200 observations. Signals and metrics use the full window; the list below contains every signal.',
    lower: 'Lower limit',
    upper: 'Upper limit',
    centre: 'Centre line',
    observations: 'Cycle time',
    signals: 'Statistical signals',
    noSignals: 'No configured signal rules were triggered.',
    signalLimit: 'Outside the three-sigma limits',
    signalTwo: 'Two of three beyond two sigma on one side',
    signalFour: 'Four of five beyond one sigma on one side',
    signalRun: 'Eight successive values on one side',
    signalRange: 'Large moving range',
    law: 'Little’s law review',
    lawHint:
      'Compares time-averaged active WIP with completion rate × mean active-cycle time. The same process boundary is required. A finite window, reopened work or changing demand can cause a mismatch even with correct data.',
    lawEnough: 'Within the review tolerance',
    lawReview: 'Review the boundary and history',
    lawInsufficient: 'More completed cycle times are needed',
    measured: 'Measured average WIP',
    expected: 'Rate × mean cycle time',
    ratio: 'Measured / expected WIP',
    toleranceHint:
      'The heuristic review range is 0.80–1.25, with at least five measured completions. It is not a statistical test or proof of data quality.',
    number: ({ value }: { value: number }, f) =>
      f.number(value, { maximumFractionDigits: 2 }),
    date: ({ value }: { value: string }, f) =>
      f.date(value, { timeZone: 'UTC', dateStyle: 'medium' }),
  },
  SV: {
    title: 'Flödesanalys',
    eyebrow: 'FÖRSTÅ ARBETET',
    description:
      'Se var arbetet samlas, hur lång tid leveransen tar och vilka uppgifter som behöver uppmärksamhet.',
    noProject: 'Välj eller skapa ett projekt för att analysera flödet.',
    loading: 'Laddar flödeshistorik…',
    failed:
      'Flödeshistoriken kunde inte hämtas. Kontrollera anslutningen och försök igen.',
    window: 'Historikperiod',
    windowDays: ({ count }, f) =>
      f.plural(count, { one: '# dag', other: '# dagar' }),
    windowHint:
      'Kalenderdagar i UTC, fram till nu. Percentilerna beskriver uppgifter som avslutats under perioden; diagrammen använder även den tidigare historik som behövs.',
    missing: ({ count }, f) =>
      f.plural(count, {
        one: '# uppgift saknar användbar historik. Historiska diagram och tider utelämnar den; nuvarande kolumnantal räknar med den.',
        other:
          '# uppgifter saknar användbar historik. Historiska diagram och tider utelämnar dem; nuvarande kolumnantal räknar med dem.',
      }),
    noHistory: 'Inget registrerat flöde ännu',
    noHistoryHint:
      'Skapa uppgifter och flytta dem på tavlan. Tider visas när historiken räcker till.',
    completed: 'Avslutade uppgifter',
    rate: 'Avslut per dag',
    cycle: 'Cykeltid',
    lead: 'Ledtid',
    hours: 'Timmar',
    cycleHint:
      'Förfluten tid i den senaste sammanhängande perioden Pågår / Granskning som slutar i Klar. En återgång till Att göra eller återöppning startar en ny period.',
    leadHint:
      'Förfluten tid från att uppgiften skapades till dess senaste avslut. Tider utan giltiga tidsstämplar räknas inte med.',
    distributionHint:
      'Percentilerna använder bara avslutade uppgifter. Långsamt pågående arbete ingår inte i det urvalet; jämför med åldern nedan.',
    mean: 'Medelvärde',
    p50: '50:e percentilen',
    p85: '85:e percentilen',
    p95: '95:e percentilen',
    samples: ({ count }, f) =>
      f.plural(count, { one: '# observation', other: '# observationer' }),
    cfd: 'Kumulativt flöde',
    cfdHint:
      'Uppgifter i varje kolumn vid slutet av UTC-dagen. Dagens värden slutar vid rapporttiden. Urvalet är projektets nuvarande arbete; senare radering eller papperskorg kan ändra äldre antal.',
    toDo: 'Att göra',
    inProgress: 'Pågår',
    review: 'Granskning',
    done: 'Klar',
    unknownStatus: 'Okänd kolumn',
    columns: 'Tryck i kolumnerna',
    current: 'Uppgifter nu',
    averageWip: 'Genomsnittligt WIP',
    limit: 'WIP-gräns',
    noLimit: 'Ingen gräns',
    overLimit: 'Över WIP-gränsen',
    pressure: 'Möjlig flaskhals',
    pressureHint:
      'Först markeras en aktiv kolumn över sin gräns; annars den aktiva kolumn som har högst tidsvägt WIP. Markeringen hjälper er att undersöka orsaken.',
    configure: 'Ange WIP-gränser',
    limitsTitle: 'Tavlans WIP-gränser',
    limitHelp:
      'Valfria antal uppgifter från 1 till 999. Lämna tomt för ingen gräns. Gränserna varnar men hindrar inte flytt. Bara ägare och administratörer kan ändra dem.',
    readonly:
      'Du kan läsa analysen. Ändringar av WIP kräver en ägare eller administratör i ett aktivt projekt.',
    save: 'Spara gränser',
    saving: 'Sparar…',
    stale:
      'En annan administratör ändrade gränserna. Ditt utkast finns kvar. Stäng och öppna igen för att jämföra med senaste inställningarna.',
    denied:
      'Du kan inte ändra gränserna. Kontrollera projektets status och din roll.',
    invalid:
      'Ange hela antal uppgifter från 1 till 999 eller lämna fältet tomt.',
    saveFailed:
      'Gränserna kunde inte sparas. Kontrollera anslutningen och försök igen.',
    aging: 'Åldrande pågående arbete',
    ageHint:
      'Åldern räknas från början av den nuvarande sammanhängande aktiva perioden; kolumnåldern från inträdet i nuvarande kolumn. Intervallen jämför med avslutade uppgifters cykeltid.',
    task: 'Uppgift',
    age: 'Aktiv ålder (timmar)',
    columnAge: 'Kolumnålder (timmar)',
    band: 'Jämfört med avslutat arbete',
    below50: 'Vid eller under 50:e percentilen',
    below85: 'Mellan 50:e och 85:e percentilen',
    below95: 'Mellan 85:e och 95:e percentilen',
    above95: 'Över 95:e percentilen',
    noReference: 'Saknar avslutad referens',
    noAging: 'Inga uppgifter pågår eller granskas just nu.',
    more: 'Visa fler',
    xmr: 'XmR-diagram för cykeltid',
    individuals: 'Enskilda cykeltider (timmar)',
    ranges: 'Rörliga variationsvidder (timmar)',
    xmrHint:
      'Beskrivande gränser använder hela urvalet. Signaler är inga deadlines eller bevis på en särskild orsak. Autokorrelation, snedfördelning och flera regler kan öka falsklarmen; granska arbetet och sammanhanget.',
    tooFew: ({ count }) =>
      `Minst ${count} avslutade cykeltider behövs för det beskrivande diagrammet.`,
    chartWindow:
      'Diagrammen visar de senaste 200 observationerna. Signaler och mått använder hela perioden; listan nedan innehåller varje signal.',
    lower: 'Nedre gräns',
    upper: 'Övre gräns',
    centre: 'Mittlinje',
    observations: 'Cykeltid',
    signals: 'Statistiska signaler',
    noSignals: 'Ingen av de valda signalreglerna utlöstes.',
    signalLimit: 'Utanför tre-sigma-gränserna',
    signalTwo: 'Två av tre bortom två sigma på samma sida',
    signalFour: 'Fyra av fem bortom en sigma på samma sida',
    signalRun: 'Åtta värden i följd på samma sida',
    signalRange: 'Stor rörlig variationsvidd',
    law: 'Granskning med Little’s lag',
    lawHint:
      'Jämför tidsvägt aktivt WIP med avslutstakt × genomsnittlig aktiv cykeltid. Processgränsen måste vara densamma. En avgränsad period, återöppnat arbete eller förändrat inflöde kan ge avvikelser även med korrekt data.',
    lawEnough: 'Inom granskningsintervallet',
    lawReview: 'Granska processgränsen och historiken',
    lawInsufficient: 'Fler avslutade cykeltider behövs',
    measured: 'Uppmätt genomsnittligt WIP',
    expected: 'Takt × genomsnittlig cykeltid',
    ratio: 'Uppmätt / förväntat WIP',
    toleranceHint:
      'Det heuristiska granskningsintervallet är 0,80–1,25 med minst fem uppmätta avslut. Det är inget statistiskt test eller bevis på datakvalitet.',
    number: ({ value }, f) => f.number(value, { maximumFractionDigits: 2 }),
    date: ({ value }, f) =>
      f.date(value, { timeZone: 'UTC', dateStyle: 'medium' }),
  },
});
export type FlowText = Translate<typeof flowText.EN>;
export const columnName = (t: FlowText, status: string) =>
  t(
    status === 'To Do'
      ? 'toDo'
      : status === 'In Progress'
        ? 'inProgress'
        : status === 'Review'
          ? 'review'
          : status === 'Done'
            ? 'done'
            : 'unknownStatus',
  );
