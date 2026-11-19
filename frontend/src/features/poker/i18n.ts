import { defineLexicon } from '../../shared/i18n/lexicon';

export const pokerText = defineLexicon({
  EN: {
    title: 'Planning poker',
    eyebrow: 'ESTIMATE TOGETHER',
    description:
      'Vote independently, compare estimates and agree on the next story.',
    noProject: 'Choose or create a project to estimate stories.',
    loading: 'Loading planning poker…',
    failed: 'Could not load planning poker. Try again.',
    readonly:
      'You can watch sessions. Voting and facilitation need edit access to an active project.',
    open: 'Open sessions',
    closed: 'Recent sessions',
    noSessions: 'No sessions here yet',
    noSessionsHint:
      'Start a session for a backlog story and invite the team to this page.',
    newSession: 'Start a session',
    start: 'Start voting',
    story: 'Story',
    chooseStory: 'Choose a story',
    noStories: 'Create a story in the backlog before starting a session.',
    backlog: 'Open backlog',
    deck: 'Card deck',
    FIBONACCI: 'Fibonacci',
    TSHIRT: 'T-shirt',
    CUSTOM: 'Custom',
    cards: 'Custom cards',
    cardsHint:
      '2–20 unique cards separated by commas, in ascending estimate order. Up to 8 letters, digits or symbols per card. ? and ☕ abstain.',
    deckHint:
      'Fibonacci 13 means split the story. T-shirt and custom estimates are mapped to 1, 2, 3, 5 or 8 story points when agreed.',
    customInvalid:
      'Enter 2–20 different cards of 1–8 characters, including at least one estimating card.',
    VOTING: 'Voting',
    REVEALED: 'Revealed',
    ACCEPTED: 'Estimate saved',
    CANCELLED: 'Cancelled',
    round: ({ number }: { number: number }) => `Round ${number}`,
    currentPoints: ({ points }: { points: number }) =>
      `Current story estimate: ${points} points`,
    hidden: 'Other votes stay hidden until the facilitator reveals this round.',
    chooseCard: 'Your card',
    cardLabel: ({ card }: { card: string }) => `Vote ${card}`,
    yourCard: ({ card }: { card: string }) => `Your vote: ${card}`,
    unsure: 'Unsure',
    break: 'Need a break',
    retract: 'Retract my vote',
    voters: ({ count }: { count: number }, f) =>
      f.plural(count, {
        one: '# person has voted',
        other: '# people have voted',
      }),
    anonymous: 'Team member',
    waiting: 'Waiting for the team to vote.',
    reveal: 'Reveal votes',
    revote: 'Start another round',
    cancel: 'Cancel session',
    cancelConfirm:
      'Cancel this session? Votes that have not been revealed will be deleted.',
    facilitatorHint:
      'The facilitator, project owner or an admin can reveal votes, start another round and save the agreed estimate.',
    consensus: 'Estimating cards agree',
    disagreement: 'Discuss the differences before saving an estimate.',
    noEstimates: 'Only abstentions. Start another round to get an estimate.',
    recommendRevote:
      'Estimates are far apart. Consider another round after discussing them.',
    stats: 'Round results',
    distribution: 'Card distribution',
    card: 'Card',
    count: 'Votes',
    min: 'Lowest card',
    max: 'Highest card',
    median: 'Median card',
    mean: 'Mean',
    agreement: 'Agreement',
    abstentions: 'Abstentions',
    statsHint:
      'Median is the lower middle card in deck order. ? and ☕ do not estimate. Mean is shown only for numeric estimates.',
    number: ({ value }: { value: number }, f) =>
      f.number(value, { maximumFractionDigits: 2 }),
    percent: ({ value }: { value: number }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 0 }),
    estimate: 'Agreed story points',
    save: 'Save agreed estimate',
    saveHint:
      'Agree with the team first. This saves the estimate on the story and in project activity.',
    saved: ({ points }: { points: number }) =>
      `Agreed estimate saved: ${points} story points.`,
    storyChanged:
      'The story changed while you were reviewing it. Reload its estimate before saving.',
    reloadEstimate: 'Reload story estimate',
    refresh: 'Refresh session',
    pending: 'Saving…',
    writeFailed:
      'Could not save the change. Refresh the session and try again.',
    history: 'Earlier rounds',
    selected: 'Selected session',
    maxRounds: '50 rounds reached. Cancel this session and start a new one.',
  },
  SV: {
    title: 'Planning poker',
    eyebrow: 'UPPSKATTA TILLSAMMANS',
    description:
      'Rösta självständigt, jämför uppskattningar och enas om nästa berättelse.',
    noProject: 'Välj eller skapa ett projekt för att uppskatta berättelser.',
    loading: 'Laddar planning poker…',
    failed: 'Planning poker kunde inte laddas. Försök igen.',
    readonly:
      'Du kan följa sessioner. Röstning och ledning kräver redigeringsbehörighet i ett aktivt projekt.',
    open: 'Öppna sessioner',
    closed: 'Senaste sessioner',
    noSessions: 'Inga sessioner här ännu',
    noSessionsHint:
      'Starta en session för en berättelse i backloggen och bjud in teamet till sidan.',
    newSession: 'Starta en session',
    start: 'Starta röstning',
    story: 'Berättelse',
    chooseStory: 'Välj en berättelse',
    noStories: 'Skapa en berättelse i backloggen innan du startar en session.',
    backlog: 'Öppna backloggen',
    deck: 'Kortlek',
    FIBONACCI: 'Fibonacci',
    TSHIRT: 'T-shirt',
    CUSTOM: 'Anpassad',
    cards: 'Anpassade kort',
    cardsHint:
      '2–20 unika kort åtskilda med kommatecken, i stigande uppskattningsordning. Högst 8 bokstäver, siffror eller symboler per kort. ? och ☕ avstår.',
    deckHint:
      'Fibonacci 13 betyder att berättelsen behöver delas upp. T-shirt och anpassade uppskattningar översätts till 1, 2, 3, 5 eller 8 poäng när ni enats.',
    customInvalid:
      'Ange 2–20 olika kort med 1–8 tecken och minst ett kort som uppskattar.',
    VOTING: 'Röstning',
    REVEALED: 'Avslöjad',
    ACCEPTED: 'Uppskattning sparad',
    CANCELLED: 'Avbruten',
    round: ({ number }) => `Runda ${number}`,
    currentPoints: ({ points }) =>
      `Berättelsens nuvarande uppskattning: ${points} poäng`,
    hidden: 'Andras röster är dolda tills spelledaren avslöjar rundan.',
    chooseCard: 'Ditt kort',
    cardLabel: ({ card }) => `Rösta ${card}`,
    yourCard: ({ card }) => `Din röst: ${card}`,
    unsure: 'Osäker',
    break: 'Behöver en paus',
    retract: 'Ta tillbaka min röst',
    voters: ({ count }, f) =>
      f.plural(count, {
        one: '# person har röstat',
        other: '# personer har röstat',
      }),
    anonymous: 'Teammedlem',
    waiting: 'Väntar på teamets röster.',
    reveal: 'Avslöja röster',
    revote: 'Starta en ny runda',
    cancel: 'Avbryt session',
    cancelConfirm: 'Avbryt sessionen? Röster som inte avslöjats raderas.',
    facilitatorHint:
      'Spelledaren, projektägaren eller en administratör kan avslöja röster, starta en ny runda och spara uppskattningen.',
    consensus: 'Uppskattningarna är eniga',
    disagreement: 'Diskutera skillnaderna innan ni sparar en uppskattning.',
    noEstimates: 'Alla avstod. Starta en ny runda för att få en uppskattning.',
    recommendRevote:
      'Uppskattningarna skiljer sig mycket. Överväg en ny runda efter diskussion.',
    stats: 'Rundans resultat',
    distribution: 'Fördelning av kort',
    card: 'Kort',
    count: 'Röster',
    min: 'Lägsta kort',
    max: 'Högsta kort',
    median: 'Mediankort',
    mean: 'Medelvärde',
    agreement: 'Enighet',
    abstentions: 'Avstod',
    statsHint:
      'Medianen är det lägre mittenkortet i kortlekens ordning. ? och ☕ uppskattar inte. Medelvärdet visas bara för numeriska uppskattningar.',
    number: ({ value }, f) => f.number(value, { maximumFractionDigits: 2 }),
    percent: ({ value }, f) =>
      f.number(value, { style: 'percent', maximumFractionDigits: 0 }),
    estimate: 'Överenskomna poäng',
    save: 'Spara överenskommen uppskattning',
    saveHint:
      'Enas med teamet först. Uppskattningen sparas på berättelsen och i projektets aktivitetslogg.',
    saved: ({ points }) =>
      `Överenskommen uppskattning sparad: ${points} poäng.`,
    storyChanged:
      'Berättelsen ändrades medan du granskade den. Ladda om uppskattningen innan du sparar.',
    reloadEstimate: 'Ladda om berättelsens uppskattning',
    refresh: 'Uppdatera session',
    pending: 'Sparar…',
    writeFailed:
      'Ändringen kunde inte sparas. Uppdatera sessionen och försök igen.',
    history: 'Tidigare rundor',
    selected: 'Vald session',
    maxRounds: '50 rundor har nåtts. Avbryt sessionen och starta en ny.',
  },
});
