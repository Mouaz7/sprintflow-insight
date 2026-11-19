package com.sprintflowinsight.workspace.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "custom_field")
class CustomField {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(nullable = false, length = 80)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private FieldKind kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> options = List.of();

  @Version private Long version;

  protected CustomField() {}

  CustomField(Long projectId, String name, FieldKind kind, List<String> options) {
    this.projectId = projectId;
    this.name = name;
    this.kind = kind;
    this.options = List.copyOf(options);
  }

  Long id() {
    return id;
  }

  Long projectId() {
    return projectId;
  }

  String name() {
    return name;
  }

  FieldKind kind() {
    return kind;
  }

  List<String> options() {
    return options;
  }

  Long version() {
    return version;
  }

  void update(String name, List<String> options) {
    this.name = name;
    this.options = List.copyOf(options);
  }
}
