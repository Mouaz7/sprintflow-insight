package com.sprintflowinsight.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class TaskAttachment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long taskId; private Long projectId; private String name; @Column(length=2000) private String url; private String storageKey; private Instant createdAt;
 public Long getId(){return id;} public void setId(Long id){this.id=id;} public Long getTaskId(){return taskId;} public void setTaskId(Long taskId){this.taskId=taskId;} public Long getProjectId(){return projectId;} public void setProjectId(Long projectId){this.projectId=projectId;} public String getName(){return name;} public void setName(String name){this.name=name;} public String getUrl(){return url;} public void setUrl(String url){this.url=url;} public String getStorageKey(){return storageKey;} public void setStorageKey(String storageKey){this.storageKey=storageKey;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
}
