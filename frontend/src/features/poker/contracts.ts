export type PokerStatus = 'VOTING' | 'REVEALED' | 'ACCEPTED' | 'CANCELLED';
export type PokerDeck = 'FIBONACCI' | 'TSHIRT' | 'CUSTOM';
export type PokerSummary = {
  id: number;
  storyId: number;
  storyTitle: string;
  status: PokerStatus;
  round: number;
  finalStoryPoints: number | null;
};
export type PokerStatistics = {
  votes: number;
  abstentions: number;
  distribution: { card: string; count: number }[];
  min: string | null;
  max: string | null;
  median: string | null;
  modes: string[];
  consensus: boolean;
  agreement: number;
  mean: number | null;
  spread: number;
  suggestion: string | null;
  suggestedStoryPoints: number | null;
  revoteRecommended: boolean;
};
export type PokerRound = {
  number: number;
  votes: { userId: number; name: string; card: string }[];
  statistics: PokerStatistics;
};
export type PokerSession = PokerSummary & {
  projectId: number;
  storyPoints: number;
  storyVersion: number;
  deck: PokerDeck;
  cards: string[];
  facilitatorUserId: number | null;
  voters: { userId: number; name: string }[];
  myCard: string | null;
  rounds: PokerRound[];
  finalValue: string | null;
  canVote: boolean;
  canFacilitate: boolean;
  createdAt: string;
  closedAt: string | null;
  version: number;
};
export type StartPoker = { storyId: number; deck: PokerDeck; cards?: string[] };
export type PokerCommand =
  | { action: 'vote'; round: number; card: string }
  | { action: 'retract' | 'reveal' | 'revote' | 'cancel'; round: number }
  | {
      action: 'accept';
      round: number;
      storyPoints: number;
      storyVersion: number;
    };
