package com.sprintflowinsight.notifications;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  @org.springframework.data.jpa.repository.Query(
      """
      select n from Notification n where lower(n.recipientEmail)=lower(:email)
        and n.isRead=false and n.createdAt>:since and n.createdAt<=:until
        and exists (select p.id from Project p where p.id=n.projectId and p.trashedAt is null
          and exists (select m.id from ProjectMember m where m.projectId=p.id and m.userId=:userId))
      order by n.createdAt desc,n.id desc
      """)
  List<Notification> digestFor(
      @org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("email") String email,
      @org.springframework.data.repository.query.Param("since") java.time.Instant since,
      @org.springframework.data.repository.query.Param("until") java.time.Instant until,
      org.springframework.data.domain.Pageable page);

  boolean existsBySourceEventIdAndRecipientEmail(
      java.util.UUID sourceEventId, String recipientEmail);

  /**
   * The caller's notifications for projects they can still read. Notifications are addressed by
   * email, but project membership is matched on the immutable account id, like every other access
   * query, so a stale or reused address in {@code project_member.email} grants nothing.
   */
  @org.springframework.data.jpa.repository.Query(
      """
      select notification from Notification notification
      where lower(notification.recipientEmail) = lower(:email) and exists (
        select project.id from Project project where project.id = notification.projectId
        and project.trashedAt is null and exists (
          select member.id from ProjectMember member
          where member.projectId = project.id and member.userId = :userId))
      order by notification.createdAt desc, notification.id desc
      """)
  List<Notification> findVisibleTo(
      @org.springframework.data.repository.query.Param("userId") Long userId,
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
