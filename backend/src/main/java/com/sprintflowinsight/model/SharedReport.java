package com.sprintflowinsight.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class SharedReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; private Long projectId; private String tokenHash;
 @Column(columnDefinition="text") private String snapshot; private Instant createdAt; private Instant expiresAt; private String createdBy;
 public Long getId(){return id;} public Long getProjectId(){return projectId;} public void setProjectId(Long value){projectId=value;}
 public String getTokenHash(){return tokenHash;} public void setTokenHash(String value){tokenHash=value;}
 public String getSnapshot(){return snapshot;} public void setSnapshot(String value){snapshot=value;}
 public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant value){createdAt=value;}
 public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant value){expiresAt=value;}
 public String getCreatedBy(){return createdBy;} public void setCreatedBy(String value){createdBy=value;}
}
