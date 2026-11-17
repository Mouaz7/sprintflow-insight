package com.sprintflowinsight.workspace.roadmap;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the shared B3 storage contract without claiming the unfinished B3 API/UI. */
class EpicStorageIntegrationTest extends ApiIntegrationTest {
  @Autowired EpicRepository epics;
  @Autowired JdbcTemplate jdbc;

  @Test
  void keepsStoryAssociationsInsideTheirProjectAndClearsThemWhenTheEpicIsDeleted() {
    var project = project(user(), null);
    var foreign = project(user(), null);
    var created = new Epic(project.getId(), Instant.now());
    created.setTitle("Storage foundation");
    created.setDescription("Shared with B3");
    created.setColor("#4f46e5");
    created.setStartDate(LocalDate.of(2026, 1, 1));
    created.setTargetDate(LocalDate.of(2026, 2, 1));
    var saved = epics.saveAndFlush(created);
    var loaded = epics.findByIdAndProjectId(saved.getId(), project.getId()).orElseThrow();
    assertThat(loaded.getProjectId()).isEqualTo(project.getId());
    assertThat(loaded.getTitle()).isEqualTo("Storage foundation");
    assertThat(loaded.getDescription()).isEqualTo("Shared with B3");
    assertThat(loaded.getColor()).isEqualTo("#4f46e5");
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getVersion()).isZero();
    assertThat(loaded.getStartDate()).isBefore(loaded.getTargetDate());
    assertThat(epics.findByProjectIdOrderByIdAsc(project.getId()))
        .extracting(Epic::getId)
        .containsExactly(saved.getId());
    assertThat(epics.countByProjectId(project.getId())).isEqualTo(1);
    assertThat(epics.findByIdAndProjectId(saved.getId(), foreign.getId())).isEmpty();
    long story =
        jdbc.queryForObject(
            "insert into"
                + " user_stories(title,acceptance_criteria,story_points,priority_id,project_id,epic_id,version)"
                + " values ('Story','Done when shipped',3,1,?,?,0) returning id",
            Long.class,
            project.getId(),
            saved.getId());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update user_stories set project_id=? where id=?", foreign.getId(), story))
        .isInstanceOf(DataIntegrityViolationException.class);
    epics.deleteById(saved.getId());
    assertThat(
            jdbc.queryForObject("select epic_id from user_stories where id=?", Long.class, story))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select project_id from user_stories where id=?", Long.class, story))
        .isEqualTo(project.getId());
  }

  @Test
  void rejectsReversedEpicDatesAtTheDatabaseBoundary() {
    var project = project(user(), null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into epic(project_id,title,created_at,start_date,target_date) values"
                        + " (?,'Invalid',now(),date '2026-02-01',date '2026-01-01')",
                    project.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
