package com.sprintflowinsight.workspace.dashboard;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal preferences only: never accepts another account id or changes project work. */
@Service
public class DashboardService {
  private final DashboardRepository layouts;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;

  public DashboardService(
      DashboardRepository layouts, ProjectAccessService access, ProjectMutationService mutations) {
    this.layouts = layouts;
    this.access = access;
    this.mutations = mutations;
  }

  @Transactional(readOnly = true)
  public DashboardResponse read(Long projectId, HttpSession session) {
    var context = access.require(projectId, session, Need.READ);
    return layouts
        .findByProjectIdAndUserId(projectId, context.userId())
        .map(DashboardService::response)
        .orElseGet(() -> new DashboardResponse(0, defaults()));
  }

  @Transactional
  public DashboardResponse save(
      Long projectId, DashboardRequest request, String ifMatch, HttpSession session) {
    var context = access.require(projectId, session, Need.READ);
    // Viewers may customize their personal layout. Archived projects remain read-only.
    mutations.lock(projectId);
    var layout = layouts.findByProjectIdAndUserId(projectId, context.userId()).orElse(null);
    VersionPrecondition.require(ifMatch, layout == null ? 0L : layout.getVersion() + 1);
    if (request.widgets().stream().map(DashboardWidget::type).distinct().count()
        != request.widgets().size())
      throw ApiException.badRequest("Each dashboard widget may appear only once.");
    if (layout == null) layout = new DashboardLayout(projectId, context.userId());
    layout.setWidgets(request.widgets());
    return response(layouts.saveAndFlush(layout));
  }

  private static DashboardResponse response(DashboardLayout layout) {
    return new DashboardResponse(layout.getVersion() + 1, List.copyOf(layout.getWidgets()));
  }

  static List<DashboardWidget> defaults() {
    return Arrays.stream(WidgetType.values())
        .map(
            type ->
                new DashboardWidget(type, type == WidgetType.BURNDOWN || type == WidgetType.CFD))
        .toList();
  }
}
