package com.sprintflowinsight.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Email is required.")
  @Email(message = "Email must be valid.")
  @Column(nullable = false, unique = true)
  private String email;

  @NotBlank(message = "Password is required.")
  @Size(min = 6, message = "Password must contain at least 6 characters.")
  private String password;

  @Column(length = 80)
  private String displayName;

  @Column(length = 80)
  private String jobTitle;

  @Column(length = 500)
  private String bio;

  @Column(columnDefinition = "text")
  private String avatarImage;

  private String recoveryCodeHash;

  @Transient private boolean mfaEnrollmentRequired;

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
  public boolean isMfaEnrollmentRequired() {
    return mfaEnrollmentRequired;
  }

  void setMfaEnrollmentRequired(boolean required) {
    this.mfaEnrollmentRequired = required;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public void setJobTitle(String jobTitle) {
    this.jobTitle = jobTitle;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getAvatarImage() {
    return avatarImage;
  }

  public void setAvatarImage(String avatarImage) {
    this.avatarImage = avatarImage;
  }

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getRecoveryCodeHash() {
    return recoveryCodeHash;
  }

  public void setRecoveryCodeHash(String recoveryCodeHash) {
    this.recoveryCodeHash = recoveryCodeHash;
  }
}
