package com.sprintflowinsight.portability;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountExportController {
  private final AccountExportService service;

  public AccountExportController(AccountExportService service) {
    this.service = service;
  }

  @PostMapping("/export")
  public ResponseEntity<byte[]> export(HttpSession session) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sprintflow-account.zip\"")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(service.export(session));
  }
}
