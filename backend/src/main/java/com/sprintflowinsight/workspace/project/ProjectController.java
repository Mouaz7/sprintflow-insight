package com.sprintflowinsight.workspace.project;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @GetMapping
  public List<Project> getAllProjects(
      HttpSession session,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "100") int size) {
    return service.getAllProjects(session, page, size);
  }

  @PostMapping
  public ResponseEntity<?> createProject(
      @Valid @RequestBody ProjectRequest request, HttpSession session) {
    return service.createProject(request, session);
  }

  @GetMapping("/{id}")
  public Project getProject(@PathVariable Long id, HttpSession session) {
    return service.getProject(id, session);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateProject(
      @PathVariable Long id,
      @Valid @RequestBody ProjectRequest details,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.updateProject(id, details, ifMatch, session);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteProject(@PathVariable Long id, HttpSession session) {
    return service.deleteProject(id, session);
  }

  @GetMapping("/{id}/role")
  public ResponseEntity<?> currentRole(@PathVariable Long id, HttpSession session) {
    return service.currentRole(id, session);
  }

  @GetMapping("/{id}/members")
  public ResponseEntity<?> listMembers(@PathVariable Long id, HttpSession session) {
    return service.listMembers(id, session);
  }

  @PostMapping("/{id}/members")
  public ResponseEntity<?> addMember(
      @PathVariable Long id, @Valid @RequestBody MemberRequest request, HttpSession session) {
    return service.addMember(id, request, session);
  }

  @DeleteMapping("/{id}/members/{email}")
  public ResponseEntity<?> removeMember(
      @PathVariable Long id, @PathVariable String email, HttpSession session) {
    return service.removeMember(id, email, session);
  }

  @PutMapping("/{id}/owner")
  public ResponseEntity<?> transferOwnership(
      @PathVariable Long id, @Valid @RequestBody OwnershipRequest request, HttpSession session) {
    return service.transferOwnership(id, request, session);
  }
}
