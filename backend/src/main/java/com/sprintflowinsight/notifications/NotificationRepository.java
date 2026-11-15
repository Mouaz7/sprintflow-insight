package com.sprintflowinsight.notifications;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  boolean existsBySourceEventIdAndRecipientEmail(
      java.util.UUID sourceEventId, String recipientEmail);

  @org.springframework.data.jpa.repository.Query(
      """
      select notification from Notification notification
      where lower(notification.recipientEmail) = lower(:email) and exists (
        select project.id from Project project where project.id = notification.projectId
        and project.trashedAt is null and
          (lower(project.ownerEmail) = lower(:email) or exists (
            select member.id from ProjectMember member where member.projectId = project.id
            and lower(member.email) = lower(:email))))
      order by notification.createdAt desc, notification.id desc
      """)
  List<Notification> findVisibleTo(
      @org.springframework.data.repository.query.Param("email") String email,
      org.springframework.data.domain.Pageable pageable);

  List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

  boolean existsByProjectIdAndRecipientEmailAndMessageAndCreatedAtAfter(
      Long projectId, String recipientEmail, String message, java.time.Instant createdAt);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      """
      update Notification n set n.isRead = true
      where lower(n.recipientEmail) = lower(:email) and n.isRead = false
      """)
  int markAllRead(@org.springframework.data.repository.query.Param("email") String email);

  void deleteByRecipientEmail(String recipientEmail);

  void deleteByProjectId(Long projectId);
}
