package com.sprintflowinsight.workspace.poker;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.events.PokerSessionChanged;
import com.sprintflowinsight.workspace.project.AccessContext;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.story.UserStoryService;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning poker (B7). Every vote and state change takes the session's row lock, so a reveal sees
 * exactly the votes committed before it and no vote lands in a revealed round. Votes of a round
 * leave the server only once that round is revealed: {@link #view} reads values up to {@link
 * PokerSession#getRevealedRound()} and nothing later, apart from the caller's own card. Card values
 * are never logged, never put into exception messages and never put into events.
 *
 * <p>Viewers watch but do not vote: voting is a change to the project's estimates and needs WRITE,
 * like every other change. Revealing, revoting, accepting and cancelling are reserved for the
 * facilitator who started the session and for owners and admins.
 */
@Service
public class PokerService {
  static final int CLOSED_LIMIT = 20;
  static final int OPEN_LIMIT = 100;

  private final PokerSessionRepository sessions;
  private final PokerVotes votes;
  private final UserStoryRepository stories;
  private final UserStoryService storyService;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ActivityService activity;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PokerService(
      PokerSessionRepository sessions,
      PokerVotes votes,
      UserStoryRepository stories,
      UserStoryService storyService,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity,
      ApplicationEventPublisher events) {
    this.sessions = sessions;
    this.votes = votes;
    this.stories = stories;
    this.storyService = storyService;
    this.access = access;
    this.mutations = mutations;
    this.activity = activity;
    this.events = events;
    this.clock = Clock.systemUTC();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<PokerSessionSummary> list(Long projectId, boolean closed, HttpSession http) {
    access.require(projectId, http, Need.READ);
    var statuses =
        closed
            ? EnumSet.of(PokerSession.Status.ACCEPTED, PokerSession.Status.CANCELLED)
            : EnumSet.of(PokerSession.Status.VOTING, PokerSession.Status.REVEALED);
    return sessions.findVisible(projectId, statuses, Limit.of(closed ? CLOSED_LIMIT : OPEN_LIMIT));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PokerSessionView get(Long projectId, Long sessionId, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.READ);
    PokerSession session =
        sessions
            .findById(sessionId)
            .filter(found -> belongs(found, projectId))
            .orElseThrow(PokerService::missing);
    return view(session, context);
  }

  @Transactional
  public PokerSessionView start(Long projectId, StartPokerRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    UserStory story =
        stories
            .findById(request.storyId())
            .filter(found -> found.getTrashedAt() == null && projectId.equals(found.getProjectId()))
            .orElseThrow(() -> ApiException.badRequest("Choose a story in this project."));
    List<String> cards;
    try {
      cards = request.deck().cards(request.cards());
    } catch (IllegalArgumentException invalid) {
      throw ApiException.badRequest(invalid.getMessage());
    }
    boolean alreadyOpen =
        sessions.existsByStoryIdAndStatusIn(
            story.getId(), EnumSet.of(PokerSession.Status.VOTING, PokerSession.Status.REVEALED));
    if (alreadyOpen) throw ApiException.conflict(ALREADY_OPEN);
    if (sessions.countByProjectIdAndStatusIn(
            projectId, EnumSet.of(PokerSession.Status.VOTING, PokerSession.Status.REVEALED))
        >= OPEN_LIMIT)
      throw ApiException.conflict("Close an open session before starting another.");
    Instant now = clock.instant();
    // The partial unique index decides between concurrent starts; the loser gets a 409.
    PokerSession session =
        sessions.saveAndFlush(
            new PokerSession(
                projectId, story.getId(), context.userId(), request.deck(), cards, now));
    activity.record(
        projectId,
        "POKER_STARTED",
        "Started planning poker for story “" + story.getTitle() + "”.",
        context.email(),
        "story",
        story.getId(),
        now);
    return changed(session, context, now);
  }

  /** Casts or changes the caller's card for the current round. */
  @Transactional
  public PokerSessionView vote(
      Long projectId, Long sessionId, VoteRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireVoting(session);
    String card = request.card().strip();
    if (!session.getCards().contains(card))
      throw ApiException.badRequest("Choose a card from this session's deck.");
    Instant now = clock.instant();
    votes.cast(session.getId(), session.getRound(), context.userId(), card, now);
    session.touch(now);
    return changed(session, context, now);
  }

  @Transactional
  public PokerSessionView retract(
      Long projectId, Long sessionId, PokerRoundRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireVoting(session);
    Instant now = clock.instant();
    if (!votes.retract(session.getId(), session.getRound(), context.userId()))
      return view(session, context);
    session.touch(now);
    return changed(session, context, now);
  }

  @Transactional
  public PokerSessionView reveal(
      Long projectId, Long sessionId, PokerRoundRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireFacilitator(session, context);
    requireVoting(session);
    if (votes.count(session.getId(), session.getRound()) == 0)
      throw ApiException.conflict("Nobody has voted in this round yet.");
    Instant now = clock.instant();
    session.reveal(now);
    return changed(session, context, now);
  }

  @Transactional
  public PokerSessionView revote(
      Long projectId, Long sessionId, PokerRoundRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireFacilitator(session, context);
    if (session.getStatus() != PokerSession.Status.REVEALED)
      throw ApiException.conflict("Reveal this round before starting a new one.");
    Instant now = clock.instant();
    if (session.getRound() >= 50)
      throw ApiException.conflict("Start a new session after 50 rounds.");
    session.revote(now);
    return changed(session, context, now);
  }

  /** Closes a revealed session and writes the estimate to the story. */
  @Transactional
  public PokerSessionView accept(
      Long projectId, Long sessionId, AcceptPokerRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    // Project before session, the same order as every other project-wide change.
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireFacilitator(session, context);
    if (session.getStatus() != PokerSession.Status.REVEALED)
      throw ApiException.conflict("Reveal the votes before accepting an estimate.");
    int points = request.storyPoints();
    if (!PokerStatistics.STORY_POINTS.contains(points))
      throw ApiException.badRequest("Story points must be 1, 2, 3, 5, or 8.");
    String value = String.valueOf(points);
    if (!session.getCards().contains(value)) value = currentStatistics(session).suggestion();
    if (currentStatistics(session).suggestion() == null)
      throw ApiException.conflict("Cast an estimating card before accepting an estimate.");
    storyService.applyEstimate(
        projectId, session.getStoryId(), points, request.storyVersion(), context.email());
    Instant now = clock.instant();
    session.accept(value, points, now);
    return changed(session, context, now);
  }

  /** Closes an open session without an estimate. Unrevealed votes are deleted. */
  @Transactional
  public PokerSessionView cancel(
      Long projectId, Long sessionId, PokerRoundRequest request, HttpSession http) {
    AccessContext context = access.require(projectId, http, Need.WRITE);
    mutations.lock(projectId);
    PokerSession session = locked(projectId, sessionId);
    requireRound(session, request.round());
    requireFacilitator(session, context);
    if (!session.getStatus().open()) throw ApiException.conflict("This session is already closed.");
    if (session.getRevealedRound() < session.getRound())
      votes.discard(session.getId(), session.getRound());
    Instant now = clock.instant();
    session.cancel(now);
    return changed(session, context, now);
  }

  static final String ALREADY_OPEN = "This story already has an open planning poker session.";

  private PokerSession locked(Long projectId, Long sessionId) {
    return sessions
        .findLockedById(sessionId)
        .filter(found -> belongs(found, projectId))
        .orElseThrow(PokerService::missing);
  }

  /** A session belongs to the path's project and its story is not in the recycle bin. */
  private boolean belongs(PokerSession session, Long projectId) {
    return projectId.equals(session.getProjectId())
        && stories
            .findById(session.getStoryId())
            .map(story -> story.getTrashedAt() == null)
            .orElse(false);
  }

  private static void requireRound(PokerSession session, int round) {
    if (session.getRound() != round)
      throw ApiException.conflict("The round has changed. Reload the session.");
  }

  private static void requireVoting(PokerSession session) {
    if (session.getStatus() != PokerSession.Status.VOTING)
      throw ApiException.conflict("Voting is closed for this round.");
  }

  private static void requireFacilitator(PokerSession session, AccessContext context) {
    if (!canFacilitate(session, context))
      throw ApiException.forbidden(
          "Only the facilitator, an owner or an admin can run this session.");
  }

  private static boolean canFacilitate(PokerSession session, AccessContext context) {
    return context.role().canWrite()
        && (context.role().canManage()
            || Objects.equals(session.getFacilitatorUserId(), context.userId()));
  }

  private static ApiException missing() {
    return ApiException.notFound("Planning poker session not found.");
  }

  /** Flushes so the version is final, then announces the change without any vote content. */
  private PokerSessionView changed(PokerSession session, AccessContext context, Instant now) {
    sessions.flush();
    events.publishEvent(
        new PokerSessionChanged(
            UUID.randomUUID(),
            session.getProjectId(),
            context.userId(),
            now,
            session.getId(),
            session.getVersion()));
    return view(session, context);
  }

  private PokerStatistics currentStatistics(PokerSession session) {
    List<String> played =
        votes.upTo(session.getId(), session.getRevealedRound()).stream()
            .filter(vote -> vote.round() == session.getRevealedRound())
            .map(PokerVotes.Vote::card)
            .toList();
    return PokerStatistics.of(session.getCards(), played);
  }

  /** What {@code context}'s user may see of {@code session}. */
  PokerSessionView view(PokerSession session, AccessContext context) {
    long id = session.getId();
    // The only read of other people's cards, bounded by the last revealed round.
    Map<Integer, List<PokerSessionView.Vote>> byRound = new LinkedHashMap<>();
    Map<Integer, List<String>> cardsByRound = new LinkedHashMap<>();
    for (PokerVotes.Vote vote : votes.upTo(id, session.getRevealedRound())) {
      byRound
          .computeIfAbsent(vote.round(), round -> new ArrayList<>())
          .add(new PokerSessionView.Vote(vote.userId(), vote.name(), vote.card()));
      cardsByRound.computeIfAbsent(vote.round(), round -> new ArrayList<>()).add(vote.card());
    }
    List<PokerSessionView.Round> rounds = new ArrayList<>();
    byRound.forEach(
        (round, roundVotes) ->
            rounds.add(
                new PokerSessionView.Round(
                    round,
                    List.copyOf(roundVotes),
                    PokerStatistics.of(session.getCards(), cardsByRound.get(round)))));
    List<PokerSessionView.Voter> voters =
        votes.votersOf(id, session.getRound()).stream()
            .map(vote -> new PokerSessionView.Voter(vote.userId(), vote.name()))
            .toList();
    String myCard = votes.own(id, session.getRound(), context.userId()).orElse(null);
    UserStory story = stories.findById(session.getStoryId()).orElse(null);
    boolean writable = context.role().canWrite() && !context.archived();
    return new PokerSessionView(
        id,
        session.getProjectId(),
        session.getStoryId(),
        story == null ? null : story.getTitle(),
        story == null ? null : story.getStoryPoints(),
        story == null ? null : story.getVersion(),
        session.getDeck(),
        session.getCards(),
        session.getStatus(),
        session.getRound(),
        session.getFacilitatorUserId(),
        voters,
        myCard,
        List.copyOf(rounds),
        session.getFinalValue(),
        session.getFinalStoryPoints(),
        writable && session.getStatus() == PokerSession.Status.VOTING,
        writable && session.getStatus().open() && canFacilitate(session, context),
        session.getCreatedAt(),
        session.getClosedAt(),
        session.getVersion());
  }
}
