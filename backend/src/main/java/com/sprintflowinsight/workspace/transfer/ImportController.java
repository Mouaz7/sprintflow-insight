package com.sprintflowinsight.workspace.transfer;

import com.sprintflowinsight.common.audit.AuditService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ImportController {
  private final ImportService service;
  private final ProjectAccessService access;
  private final AuditService audit;

  public ImportController(ImportService service, ProjectAccessService access, AuditService audit) {
    this.service = service;
    this.access = access;
    this.audit = audit;
  }

  @PostMapping("/imports/preview")
  public ImportViews.Preview preview(
      @PathVariable Long projectId,
      @Valid @RequestBody ImportRequest request,
      HttpSession session) {
    return service.preview(projectId, request, session);
  }

  @PostMapping("/imports")
  public ImportViews.Result commit(
      @PathVariable Long projectId,
      @Valid @RequestBody ImportRequest request,
      HttpSession session) {
    return service.commit(projectId, request, session);
  }

  @GetMapping("/imports")
  public List<ImportViews.Batch> list(@PathVariable Long projectId, HttpSession session) {
    return service.batches(projectId, session);
  }

  @GetMapping("/audit")
  public AuditService.Page audit(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size,
      HttpSession session) {
    access.require(projectId, session, AccessContext.Need.MANAGE_ARCHIVED);
    if (size < 1 || size > 100 || before != null && before < 1)
      throw ApiException.badRequest("Choose 1–100 audit rows and a positive cursor.");
    return audit.page(projectId, before, size);
  }
}
