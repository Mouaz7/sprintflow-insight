package com.sprintflowinsight.workspace.dashboard;

import jakarta.persistence.*;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboard_layout")
public class DashboardLayout {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long projectId;
  private Long userId;
  @Version private Long version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private List<DashboardWidget> widgets;

  protected DashboardLayout() {}

  DashboardLayout(Long projectId, Long userId) {
    this.projectId = projectId;
    this.userId = userId;
  }

  public Long getVersion() {
    return version;
  }

  public List<DashboardWidget> getWidgets() {
    return widgets;
  }

  public void setWidgets(List<DashboardWidget> widgets) {
    this.widgets = List.copyOf(widgets);
  }
}
