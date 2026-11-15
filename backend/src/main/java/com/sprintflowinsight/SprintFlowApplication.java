package com.sprintflowinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@org.springframework.scheduling.annotation.EnableAsync
@SpringBootApplication
@EnableScheduling
public class SprintFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(SprintFlowApplication.class, args);
  }
}
