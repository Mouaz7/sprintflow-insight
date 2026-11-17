package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EpicView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.StoryEpicView;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Epics and the stories they contain. */
@Service
public class EpicService {
  /** Keeps a single project's roadmap readable and its queries bounded. */
  static final int MAX_EPICS_PER_PROJECT = 200;

  private final EpicRepository epics;
  private final UserStoryRepository stories;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ActivityService activity;

  EpicService(
      EpicRepository epics,
      UserStoryRepository stories,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity) {
    this.epics = epics;
    this.stories = stories;
    this.access = access;
    this.mutations = mutations;
    this.activity = activity;
  }

  @Transactional(readOnly = true)
  public List<EpicView> list(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return epics.findByProjectIdOrderByIdAsc(projectId).stream().map(EpicView::of).toList();
  }

  @Transactional
  public EpicView create(Long projectId, EpicRequest request, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    if (epics.countByProjectId(projectId) >= MAX_EPICS_PER_PROJECT)
      throw ApiException.conflict(
          "A project can have at most " + MAX_EPICS_PER_PROJECT + " epics.");
    Epic epic = new Epic(projectId, Instant.now());
    request.applyTo(epic);
    Epic saved = epics.saveAndFlush(epic);
    activity.record(
        projectId, "EPIC_CREATED", "Created epic “" + saved.getTitle() + "”.", access.email(session));
    return EpicView.of(saved);
  }

  @Transactional
  public EpicView update(
      Long projectId, Long epicId, EpicRequest request, String ifMatch, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    Epic epic = find(projectId, epicId);
    VersionPrecondition.require(ifMatch, epic.getVersion());
    request.applyTo(epic);
    Epic saved = epics.saveAndFlush(epic);
    activity.record(
        projectId, "EPIC_UPDATED", "Updated epic “" + saved.getTitle() + "”.", access.email(session));
    return EpicView.of(saved);
  }

  /** Deletes the epic; its stories stay in the backlog without an epic. */
  @Transactional
  public void delete(Long projectId, Long epicId, String ifMatch, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    Epic epic = find(projectId, epicId);
    VersionPrecondition.require(ifMatch, epic.getVersion());
    epics.delete(epic);
    epics.flush();
    activity.record(
        projectId, "EPIC_DELETED", "Deleted epic “" + epic.getTitle() + "”.", access.email(session));
  }

  @Transactional
  public StoryEpicView assign(
      Long projectId, Long storyId, StoryEpicRequest request, String ifMatch, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    UserStory story =
        stories
            .findById(storyId)
            .filter(s -> s.getTrashedAt() == null && projectId.equals(s.getProjectId()))
            .orElseThrow(() -> ApiException.notFound("Story not found."));
    VersionPrecondition.require(ifMatch, story.getVersion());
    Epic epic = request.epicId() == null ? null : find(projectId, request.epicId());
    story.setEpicId(epic == null ? null : epic.getId());
    UserStory saved = stories.saveAndFlush(story);
    activity.record(
        projectId,
        "STORY_EPIC_CHANGED",
        epic == null
            ? "Removed story “" + saved.getTitle() + "” from its epic."
            : "Moved story “" + saved.getTitle() + "” to epic “" + epic.getTitle() + "”.",
        access.email(session));
    return new StoryEpicView(saved.getId(), saved.getEpicId(), saved.getVersion());
  }

  private Epic find(Long projectId, Long epicId) {
    return epics
        .findByIdAndProjectId(epicId, projectId)
        .orElseThrow(() -> ApiException.notFound("Epic not found."));
  }
}
