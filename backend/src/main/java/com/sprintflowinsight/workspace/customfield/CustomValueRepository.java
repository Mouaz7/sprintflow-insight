package com.sprintflowinsight.workspace.customfield;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

/** Explicit bulk/streaming reads avoid adding collections to every core Task query. */
@Repository
class CustomValueRepository {
  private static final String READ =
      """
      select value.task_id, value.field_id, value.kind, value.text_value,
             value.number_value, value.date_value, value.member_id, member.email
      from task_custom_value value
      join task on task.id = value.task_id and task.project_id = value.project_id
      left join project_member member on member.id = value.member_id
                                     and member.project_id = value.project_id
      where value.project_id = :projectId and task.trashed_at is null
        and (:taskId = 0 or value.task_id = :taskId)
      order by value.task_id, value.field_id
      """;
  private final EntityManager em;

  CustomValueRepository(EntityManager em) {
    this.em = em;
  }

  record Stored(Long taskId, Long fieldId, FieldKind kind, FieldValue value, String display) {
    CustomFieldViews.Value view() {
      return new CustomFieldViews.Value(fieldId, value.value(), display);
    }
  }

  @SuppressWarnings("unchecked")
  List<Stored> forTask(Long projectId, Long taskId) {
    List<Object[]> rows =
        em.createNativeQuery(READ, Object[].class)
            .setParameter("projectId", projectId)
            .setParameter("taskId", taskId)
            .getResultList();
    return rows.stream().map(CustomValueRepository::stored).toList();
  }

  /** The caller must keep a read transaction open and close this stream. */
  @SuppressWarnings("unchecked")
  Stream<Stored> forExport(Long projectId) {
    Stream<Object[]> rows =
        em.createNativeQuery(READ, Object[].class)
            .setParameter("projectId", projectId)
            .setParameter("taskId", 0L)
            .setHint("org.hibernate.fetchSize", 200)
            .getResultStream();
    return rows.map(CustomValueRepository::stored);
  }

  boolean optionInUse(Long projectId, Long fieldId, String option) {
    Number count =
        (Number)
            em.createNativeQuery(
                    """
                    select count(*) from task_custom_value
                    where project_id=:projectId and field_id=:fieldId and text_value=:option
                    """)
                .setParameter("projectId", projectId)
                .setParameter("fieldId", fieldId)
                .setParameter("option", option)
                .getSingleResult();
    return count.longValue() > 0;
  }

  void deleteForTask(Long projectId, Long taskId) {
    em.createNativeQuery(
            "delete from task_custom_value where project_id=:projectId and task_id=:taskId")
        .setParameter("projectId", projectId)
        .setParameter("taskId", taskId)
        .executeUpdate();
  }

  List<Long> matchingTaskIds(Long projectId, PreparedFieldFilter filter) {
    var query =
        em.createNativeQuery(
            "select task.id from task where task.project_id=:projectId"
                + " and task.trashed_at is null and "
                + filter.taskPredicate()
                + " order by task.id");
    query.setParameter("projectId", projectId);
    filter.bind(query);
    List<?> rows = query.getResultList();
    return rows.stream().map(value -> ((Number) value).longValue()).toList();
  }

  void insert(Long projectId, Long taskId, Long fieldId, FieldKind kind, FieldValue value) {
    em.createNativeQuery(
            """
insert into task_custom_value(task_id,field_id,project_id,kind,text_value,number_value,date_value,member_id)
values (:taskId,:fieldId,:projectId,:kind,cast(:textValue as varchar),
        cast(:numberValue as numeric),cast(:dateValue as date),cast(:memberId as bigint))
""")
        .setParameter("taskId", taskId)
        .setParameter("fieldId", fieldId)
        .setParameter("projectId", projectId)
        .setParameter("kind", kind.name())
        .setParameter("textValue", value.text())
        .setParameter("numberValue", value.number())
        .setParameter("dateValue", value.date())
        .setParameter("memberId", value.memberId())
        .executeUpdate();
  }

  private static Stored stored(Object[] row) {
    FieldKind kind = FieldKind.valueOf((String) row[2]);
    FieldValue value =
        switch (kind) {
          case TEXT, SELECT -> new FieldValue((String) row[3], (String) row[3], null, null, null);
          case NUMBER -> {
            var number = ((BigDecimal) row[4]).stripTrailingZeros();
            yield new FieldValue(number.toPlainString(), null, number, null, null);
          }
          case DATE -> {
            LocalDate date =
                row[5] instanceof LocalDate local ? local : ((java.sql.Date) row[5]).toLocalDate();
            yield new FieldValue(date.toString(), null, null, date, null);
          }
          case USER -> {
            Long member = ((Number) row[6]).longValue();
            yield new FieldValue(member.toString(), null, null, null, member);
          }
        };
    String display = kind == FieldKind.USER && row[7] != null ? (String) row[7] : value.value();
    return new Stored(
        ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), kind, value, display);
  }
}
