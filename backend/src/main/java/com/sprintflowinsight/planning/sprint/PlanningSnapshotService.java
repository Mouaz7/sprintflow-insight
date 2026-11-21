package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.planning.sprint.PlanningSnapshot.Effort;
import com.sprintflowinsight.planning.sprint.PlanningSnapshot.Work;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Eligibility;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Member;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Options;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Story;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.time.CapacityService;
import com.sprintflowinsight.workspace.time.CapacityUnit;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PlanningSnapshotService {
  private final ProjectAccessService access;
  private final CapacityService capacity;
  private final JdbcTemplate jdbc;

  PlanningSnapshotService(
      ProjectAccessService access, CapacityService capacity, JdbcTemplate jdbc) {
    this.access = access;
    this.capacity = capacity;
    this.jdbc = jdbc;
  }

  record Backlog(long id, String title, int points, int value, Long sprintId) {}

  record Participant(long id, String email) {}

  record Edge(long blocker, long blocked) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PlanningSnapshot load(
      long projectId, long sprintId, CapacityUnit unit, HttpSession session) {
    access.read(projectId, session);
    var status =
        jdbc.queryForList(
            "select status from sprint where project_id=? and id=? and trashed_at is null",
            String.class,
            projectId,
            sprintId);
    if (status.isEmpty()) throw ApiException.notFound("Sprint not found in this project.");
    if (!Set.of("Planned", "Active").contains(status.getFirst()))
      throw ApiException.conflict("Choose a planned or active sprint.");

    var backlog =
        jdbc.query(
            """
            select id,title,story_points,coalesce(business_value,0),sprint_id
            from user_stories where project_id=? and trashed_at is null
              and (sprint_id is null or sprint_id=?)
            order by backlog_position,id limit 201
            """,
            (r, n) ->
                new Backlog(
                    r.getLong(1),
                    r.getString(2),
                    r.getInt(3),
                    r.getInt(4),
                    r.getObject(5, Long.class)),
            projectId,
            sprintId);
    if (backlog.size() > 200)
      throw ApiException.badRequest(
          "Sprint planning supports at most 200 backlog and committed stories.");

    var people =
        jdbc.query(
            """
select user_id,email from project_member where project_id=? and role in ('Owner','Admin','Member')
order by user_id limit 101
""",
            (r, n) -> new Participant(r.getLong(1), r.getString(2)),
            projectId);
    if (people.size() > 100)
      throw ApiException.badRequest("Sprint planning supports at most 100 working members.");
    var memberIds = new HashMap<String, Long>();
    people.forEach(p -> memberIds.put(p.email().toLowerCase(Locale.ROOT), p.id()));
    List<Work> tasks =
        jdbc.query(
            """
            select id,story_id,sprint_id,status,assignee_email,estimate_minutes,story_points
            from task where project_id=? and trashed_at is null order by id limit 2001
            """,
            (r, n) -> {
              String email = r.getString(5);
              return new Work(
                  r.getLong(1),
                  r.getObject(2, Long.class),
                  r.getObject(3, Long.class),
                  r.getString(4),
                  email == null ? null : memberIds.get(email.toLowerCase(Locale.ROOT)),
                  r.getObject(6, Integer.class),
                  r.getInt(7));
            },
            projectId);
    if (tasks.size() > 2000)
      throw ApiException.badRequest("Sprint planning supports at most 2000 live project tasks.");
    var edges =
        jdbc.query(
            "select blocker_task_id,blocked_task_id from task_dependency where project_id=? order"
                + " by id",
            (r, n) -> new Edge(r.getLong(1), r.getLong(2)),
            projectId);

    var configured = capacity.optimizerCapacity(projectId, sprintId, unit);
    var byStory =
        tasks.stream()
            .filter(t -> t.storyId() != null)
            .collect(Collectors.groupingBy(Work::storyId));
    var workById = tasks.stream().collect(Collectors.toMap(Work::id, t -> t));
    var reservedIds = new HashSet<Long>();
    var reservedMembers = new HashMap<Long, Integer>();
    int reserved = 0;
    int missing = 0;
    var stories = new ArrayList<Story>();

    for (Backlog story : backlog) {
      var linked = byStory.getOrDefault(story.id(), List.of());
      var unfinished = linked.stream().filter(t -> !t.done()).toList();
      if (!linked.isEmpty() && unfinished.isEmpty()) continue;
      if (story.sprintId() != null) {
        var planned =
            unfinished.stream()
                .filter(t -> t.sprintId() == null || t.sprintId() == sprintId)
                .toList();
        var effort = Effort.forWork(unit, planned, story.points());
        reserved += effort.total();
        if (!effort.complete()) missing++;
        effort.assigned().forEach((id, amount) -> reservedMembers.merge(id, amount, Integer::sum));
        planned.forEach(t -> reservedIds.add(t.id()));
        continue;
      }
      var effort = Effort.forWork(unit, unfinished, story.points());
      Eligibility eligibility;
      if (unfinished.stream().anyMatch(t -> t.sprintId() != null))
        eligibility = Eligibility.HAS_SCHEDULED_TASK;
      else if (!effort.complete() || effort.total() == 0)
        eligibility = Eligibility.MISSING_ESTIMATE;
      else eligibility = Eligibility.READY;
      stories.add(
          new Story(
              story.id(),
              story.title(),
              story.value(),
              effort.total(),
              effort.assigned(),
              eligibility));
    }

    var standalone =
        tasks.stream()
            .filter(
                t ->
                    !t.done()
                        && Objects.equals(t.sprintId(), sprintId)
                        && !reservedIds.contains(t.id()))
            .toList();
    if (!standalone.isEmpty()) {
      Effort effort = Effort.forWork(unit, standalone, 0);
      reserved += effort.total();
      if (!effort.complete()) missing++;
      effort.assigned().forEach((id, amount) -> reservedMembers.merge(id, amount, Integer::sum));
      standalone.forEach(t -> reservedIds.add(t.id()));
    }

    var dependencies = storyDependencies(edges, workById, reservedIds);
    var members =
        people.stream()
            .map(
                p ->
                    new Member(
                        p.id(),
                        p.email(),
                        configured.memberCapacities().getOrDefault(Long.toString(p.id()), 0),
                        reservedMembers.getOrDefault(p.id(), 0)))
            .toList();
    return new PlanningSnapshot(
        new Options(
            sprintId, unit, configured.totalCapacity(), reserved, missing, members, stories),
        dependencies.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))));
  }

  static Map<Long, TreeSet<String>> storyDependencies(
      List<Edge> edges, Map<Long, Work> workById, Set<Long> committed) {
    Map<Long, List<Edge>> incoming = edges.stream().collect(Collectors.groupingBy(Edge::blocked));
    Map<Long, TreeSet<String>> dependencies = new HashMap<>();
    for (var node : incoming.entrySet()) {
      Work blocked = workById.get(node.getKey());
      if (blocked == null || blocked.done() || blocked.storyId() == null) continue;
      var required = dependencies.computeIfAbsent(blocked.storyId(), ignored -> new TreeSet<>());
      var pending = new ArrayDeque<Long>();
      node.getValue().forEach(edge -> pending.add(edge.blocker()));
      var visited = new HashSet<Long>();
      while (!pending.isEmpty()) {
        long id = pending.removeFirst();
        if (!visited.add(id)) continue;
        Work blocker = workById.get(id);
        if (blocker != null && blocker.done()) continue;
        if (committed.contains(id)) {
          // The blocker is already in this sprint. Carry its prerequisites into the proposal.
          incoming.getOrDefault(id, List.of()).forEach(edge -> pending.add(edge.blocker()));
        } else if (blocker == null || blocker.storyId() == null) {
          required.add("task:" + id);
        } else if (!Objects.equals(blocker.storyId(), blocked.storyId())) {
          required.add(Long.toString(blocker.storyId()));
        }
      }
    }
    return dependencies;
  }
}
