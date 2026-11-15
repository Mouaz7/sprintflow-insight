package com.sprintflowinsight.notifications;

import com.sprintflowinsight.OptionalPage;
import com.sprintflowinsight.auth.SessionUser;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class NotificationController {
  private final NotificationService notifications;
  private final SessionUser user;

  public NotificationController(NotificationService notifications, SessionUser user) {
    this.notifications = notifications;
    this.user = user;
  }

  @GetMapping("/notifications")
  public List<Notification> notifications(
      HttpSession session,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "100") int size) {
    return notifications.forRecipient(user.email(session), OptionalPage.of(page, size));
  }

  @PutMapping("/notifications/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable Long id, HttpSession session) {
    notifications.markRead(id, user.email(session));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/notifications/read")
  public ResponseEntity<Void> markAllRead(HttpSession session) {
    notifications.markAllRead(user.email(session));
    return ResponseEntity.noContent().build();
  }
}
