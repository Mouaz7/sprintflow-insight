package com.sprintflowinsight.auth;
import jakarta.persistence.*;
@Entity
public class UserPreferences {
 @Id private Long userId;
 private String language="EN";
 private String theme="light";
 private boolean notifications=true;
 private boolean onboardingCompleted;
 @Column(columnDefinition="text") private String savedFilters="[]";
 @Version private Long version;
 public Long getUserId(){return userId;} public void setUserId(Long id){userId=id;}
 public String getLanguage(){return language;} public void setLanguage(String value){language=value;}
 public String getTheme(){return theme;} public void setTheme(String value){theme=value;}
 public boolean isNotifications(){return notifications;} public void setNotifications(boolean value){notifications=value;}
 public boolean isOnboardingCompleted(){return onboardingCompleted;} public void setOnboardingCompleted(boolean value){onboardingCompleted=value;}
 public String getSavedFilters(){return savedFilters;} public void setSavedFilters(String value){savedFilters=value;}
 public Long getVersion(){return version;}
}
