package com.sprintflowinsight.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class RetrospectiveEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long projectId; private Long sprintId; private String authorEmail; private String category;
 @Column(length=1000) private String message; private Instant createdAt; @Version private Long version;
 public Long getId(){return id;} public Long getProjectId(){return projectId;} public void setProjectId(Long value){projectId=value;}
 public Long getSprintId(){return sprintId;} public void setSprintId(Long value){sprintId=value;}
 public String getAuthorEmail(){return authorEmail;} public void setAuthorEmail(String value){authorEmail=value;}
 public String getCategory(){return category;} public void setCategory(String value){category=value;}
 public String getMessage(){return message;} public void setMessage(String value){message=value;}
 public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant value){createdAt=value;}
 public Long getVersion(){return version;}
}
