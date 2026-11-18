import { defineLexicon, type Translate } from '../../shared/i18n/lexicon';
export const forecastText = defineLexicon({
  EN: {
    title: 'Forecasts',
    eyebrow: 'PLAN WITH UNCERTAINTY',
    description:
      'Explore delivery dates, how much work fits a deadline and the chance of reaching a work target.',
    noProject: 'Choose or create a project to forecast its work.',
    loading: 'Simulating delivery…',
    failed:
      'Could not load the forecast. Check the settings and connection, then try again.',
    invalidInputs:
      'Check amounts (0–10,000), epic and date (today to five years ahead). If recorded work is larger, choose an epic or enter explicit amounts and a date.',
    basis: 'History basis',
    throughput: 'Tasks per week',
    velocity: 'Story points per completed sprint',
    epic: 'Work scope',
    all: 'Whole project',
    unavailableEpic: 'Epic unavailable in this project',
    remaining: 'Remaining work (optional)',
    dateField: 'Target date (optional)',
    targetField: 'Target amount (optional)',
    apply: 'Update forecast',
    reset: 'Use project defaults',
    auto: 'Automatic',
    formHint:
      'Empty amounts use recorded unfinished work. Empty dates use the active sprint target, or four history periods from today. Changing the basis or epic clears custom amounts.',
    tasks: 'Tasks',
    points: 'Story points',
    days: 'Days',
    simulations: 'Simulations',
    known: 'Recorded unfinished work',
    planned: 'Work used for the forecast',
    history: 'Observed history',
    period: 'Period',
    amount: 'Completed work',
    dates: 'When will the remaining work be done?',
    byDate: 'How much can be done by the date?',
    target: 'Work target',
    sprintTarget: 'Selected work in the active sprint',
    targetHint:
      'This is the probability of delivering the displayed quantity. It does not assess a qualitative sprint goal or acceptance criteria.',
    dateHistogram: 'Simulated days to completion',
    amountHistogram: 'Simulated work by the date',
    dateHint:
      'The displayed date covers this share of simulated outcomes. Higher percentages allow more time.',
    amountHint:
      'At least this amount is delivered in the displayed share of simulations. Higher percentages give a more cautious amount.',
    unavailable: 'Not forecastable within the horizon',
    needHistory: 'More delivery history is needed',
    needHistoryHint:
      'Record completed work over full weeks or completed sprints. A zero history cannot support a delivery forecast.',
    noHistory: 'No complete historical periods are available yet.',
    noCompletions:
      'The observed periods contain no completed work. This does not prove future delivery is impossible.',
    sparse: ({ count }: { count: number }) =>
      `Fewer than ${count} complete periods are available. Treat this small sample cautiously; the threshold is guidance, not validation.`,
    missing: ({ count }: { count: number }, f) =>
      f.plural(count, {
        one: '# task lacks usable history. It is not included in historical completion counts.',
        other:
          '# tasks lack usable history. They are not included in historical completion counts.',
      }),
    unplanned: ({ count }: { count: number }, f) =>
      f.plural(count, {
        one: '# story has no tasks. Its additional work is not included; enter an amount that includes it if needed.',
        other:
          '# stories have no tasks. Their additional work is not included; enter an amount that includes it if needed.',
      }),
    zeroPoints:
      'Some unfinished tasks have zero story points. A zero-point forecast does not mean those tasks are finished.',
    epicCapacity:
      'This epic uses the whole project’s delivery history. Its dates assume that capacity is allocated to the selected work; parallel work can delay it.',
    overdue:
      'The active sprint deadline has passed. No additional delivery from today can meet that deadline.',
    distant:
      'The active sprint deadline is beyond the five-year horizon; its target probability is unavailable.',
    censored: ({ count, days }: { count: number; days: number }, f) =>
      `${f.number(count)} simulations did not finish within ${f.number(days)} days. They remain in the probability denominator.`,
    method:
      'With usable history, a forecast samples 10,000 shared future paths from full historical weeks or completed sprints. It assumes similar future capacity, independent periods and even delivery within each period. Calendar days include weekends. These are simulated outcomes, not guarantees.',
    percent: ({ value }: { value: number }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 2 }),
    number: ({ value }: { value: number }, f) =>
      f.number(value, { maximumFractionDigits: 0 }),
    date: ({ value }: { value: string }, f) =>
      f.date(value, { timeZone: 'UTC', dateStyle: 'medium' }),
  },
  SV: {
    title: 'Prognoser',
    eyebrow: 'PLANERA MED OSÄKERHET',
    description:
      'Undersök leveransdatum, hur mycket arbete som ryms till ett datum och chansen att nå ett arbetsmål.',
    noProject: 'Välj eller skapa ett projekt för att göra en prognos.',
    loading: 'Simulerar leveransen…',
    failed:
      'Prognosen kunde inte hämtas. Kontrollera inställningar och anslutning och försök igen.',
    invalidInputs:
      'Kontrollera mängder (0–10 000), epic och datum (idag till fem år framåt). Om registrerat arbete är större, välj en epic eller ange egna mängder och ett datum.',
    basis: 'Historikunderlag',
    throughput: 'Uppgifter per vecka',
    velocity: 'Storypoäng per avslutad sprint',
    epic: 'Arbetsurval',
    all: 'Hela projektet',
    unavailableEpic: 'Epic saknas i projektet',
    remaining: 'Återstående arbete (valfritt)',
    dateField: 'Måldatum (valfritt)',
    targetField: 'Målmängd (valfritt)',
    apply: 'Uppdatera prognosen',
    reset: 'Använd projektets standardvärden',
    auto: 'Automatiskt',
    formHint:
      'Tomma mängder använder registrerat återstående arbete. Tomt datum använder den aktiva sprintens mål eller fyra historikperioder från idag. Byte av underlag eller epic rensar egna mängder.',
    tasks: 'Uppgifter',
    points: 'Storypoäng',
    days: 'Dagar',
    simulations: 'Simuleringar',
    known: 'Registrerat återstående arbete',
    planned: 'Mängd som används i prognosen',
    history: 'Observerad historik',
    period: 'Period',
    amount: 'Avslutat arbete',
    dates: 'När blir det återstående arbetet klart?',
    byDate: 'Hur mycket hinns till datumet?',
    target: 'Arbetsmål',
    sprintTarget: 'Valt arbete i den aktiva sprinten',
    targetHint:
      'Detta är sannolikheten att leverera den visade mängden. Ett kvalitativt sprintmål eller acceptanskriterier bedöms inte.',
    dateHistogram: 'Simulerade dagar till avslut',
    amountHistogram: 'Simulerad mängd till datumet',
    dateHint:
      'Det visade datumet täcker denna andel simulerade utfall. Högre procent ger mer tid.',
    amountHint:
      'Minst denna mängd levereras i den visade andelen simuleringar. Högre procent ger en försiktigare mängd.',
    unavailable: 'Kan inte prognostiseras inom horisonten',
    needHistory: 'Mer leveranshistorik behövs',
    needHistoryHint:
      'Registrera avslutat arbete under hela veckor eller avslutade sprintar. En nollhistorik kan inte ge en leveransprognos.',
    noHistory: 'Inga hela historikperioder är tillgängliga ännu.',
    noCompletions:
      'De observerade perioderna saknar avslutat arbete. Det bevisar inte att framtida leverans är omöjlig.',
    sparse: ({ count }) =>
      `Färre än ${count} hela perioder är tillgängliga. Tolka det lilla underlaget försiktigt; gränsen är vägledning, inte validering.`,
    missing: ({ count }, f) =>
      f.plural(count, {
        one: '# uppgift saknar användbar historik och ingår inte i historiska avslut.',
        other:
          '# uppgifter saknar användbar historik och ingår inte i historiska avslut.',
      }),
    unplanned: ({ count }, f) =>
      f.plural(count, {
        one: '# story saknar uppgifter. Dess ytterligare arbete ingår inte; ange en mängd som omfattar det vid behov.',
        other:
          '# stories saknar uppgifter. Deras ytterligare arbete ingår inte; ange en mängd som omfattar det vid behov.',
      }),
    zeroPoints:
      'Vissa återstående uppgifter har noll storypoäng. En nollprognos i poäng betyder inte att uppgifterna är klara.',
    epicCapacity:
      'Denna epic använder hela projektets leveranshistorik. Datumen förutsätter att kapaciteten läggs på det valda arbetet; parallellt arbete kan försena det.',
    overdue:
      'Den aktiva sprintens måldatum har passerat. Ingen ytterligare leverans från idag kan nå det datumet.',
    distant:
      'Den aktiva sprintens måldatum ligger bortom femårshorisonten; målsannolikheten saknas.',
    censored: ({ count, days }, f) =>
      `${f.number(count)} simuleringar avslutades inte inom ${f.number(days)} dagar. De ingår fortfarande i sannolikhetens nämnare.`,
    method:
      'Med användbar historik drar prognosen 10 000 gemensamma framtida förlopp från hela historiska veckor eller avslutade sprintar. Den förutsätter liknande framtida kapacitet, oberoende perioder och jämn leverans inom varje period. Kalenderdagar omfattar även helger. Det är simulerade utfall, inga garantier.',
    percent: ({ value }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 2 }),
    number: ({ value }, f) => f.number(value, { maximumFractionDigits: 0 }),
    date: ({ value }, f) =>
      f.date(value, { timeZone: 'UTC', dateStyle: 'medium' }),
  },
});
export type ForecastText = Translate<typeof forecastText.EN>;
