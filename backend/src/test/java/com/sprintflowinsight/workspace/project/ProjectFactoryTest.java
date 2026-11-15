package com.sprintflowinsight.workspace.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.workspace.activity.ActivityService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ProjectFactoryTest {
  private static final String OWNER = "owner@example.test";

  private final ProjectRepository projects = mock(ProjectRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final ProjectFactory factory =
      new ProjectFactory(
          projects,
          mock(ProjectMemberRepository.class),
          mock(ActivityService.class),
          users(),
          transactionManager);

  /** Keys committed by other creators; the database rejects inserts of these. */
  private final Set<String> committed = new java.util.HashSet<>();

  private final List<String> attempted = new ArrayList<>();

  private static UserRepository users() {
    UserRepository users = mock(UserRepository.class);
    User owner = new User();
    owner.setId(1L);
    when(users.findByEmail(OWNER)).thenReturn(Optional.of(owner));
    return users;
  }

  @BeforeEach
  void database() {
    when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    when(projects.findByName(any())).thenReturn(Optional.empty());
    when(projects.findKeysStartingWith(any()))
        .thenAnswer(call -> committed.stream().toList());
    when(projects.save(any(Project.class)))
        .thenAnswer(
            call -> {
              Project project = call.getArgument(0);
              attempted.add(project.getKey());
              if (committed.contains(project.getKey())) throw keyConflict();
              project.setId(10L);
              return project;
            });
  }

  private static DataIntegrityViolationException keyConflict() {
    return new DataIntegrityViolationException(
        "could not execute statement",
        new RuntimeException(
            "ERROR: duplicate key value violates unique constraint \"projects_key_unique\""));
  }

  private Project create(String key) {
    return factory.inTransactionRetryingDerivedKey(
        key, () -> factory.create("Alpha", "d", OWNER, key, null));
  }

  @Test
  void aDerivedKeyTakenConcurrentlyIsDerivedAgain() {
    // A concurrent creator commits ALPH between our key lookup and our insert.
    when(projects.findKeysStartingWith("ALPH"))
        .thenAnswer(
            call -> {
              List<String> seen = committed.stream().toList();
              committed.add("ALPH");
              return seen;
            });

    Project project = create(null);

    assertThat(attempted).containsExactly("ALPH", "ALPHA");
    assertThat(project.getKey()).isEqualTo("ALPHA");
    verify(transactionManager, times(1)).rollback(any());
    verify(transactionManager, times(1)).commit(any());
  }

  @Test
  void aRequestedKeyKeepsItsConflict() {
    when(projects.existsByProjectKey("ALPH")).thenReturn(false);
    committed.add("ALPH");

    assertThatThrownBy(() -> create("ALPH")).isInstanceOf(DataIntegrityViolationException.class);
    assertThat(attempted).containsExactly("ALPH");
  }

  @Test
  void otherConstraintViolationsAreNotRetried() {
    when(projects.save(any(Project.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "x", new RuntimeException("violates unique constraint \"projects_name_key\"")));

    assertThatThrownBy(() -> create(null)).isInstanceOf(DataIntegrityViolationException.class);
    verify(projects, times(1)).save(any(Project.class));
  }

  @Test
  void givesUpAfterABoundedNumberOfAttempts() {
    when(projects.save(any(Project.class))).thenThrow(keyConflict());

    assertThatThrownBy(() -> create(null)).isInstanceOf(DataIntegrityViolationException.class);
    verify(projects, times(ProjectFactory.DERIVED_KEY_ATTEMPTS)).save(any(Project.class));
  }

  @Test
  void recognisesTheKeyConstraintByNameOrMessage() {
    assertThat(ProjectFactory.isKeyConflict(keyConflict())).isTrue();
    assertThat(
            ProjectFactory.isKeyConflict(
                new org.hibernate.exception.ConstraintViolationException(
                    "x", null, "PROJECTS_KEY_UNIQUE")))
        .isTrue();
    assertThat(
            ProjectFactory.isKeyConflict(
                new org.hibernate.exception.ConstraintViolationException(
                    "projects_key_unique in message only", null, "projects_name_key")))
        .isFalse();
    assertThat(ProjectFactory.isKeyConflict(new RuntimeException((String) null))).isFalse();
  }
}
