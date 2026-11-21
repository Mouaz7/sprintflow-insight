package com.sprintflowinsight.common;

import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {
  /** Keep normalized HTTP route names. Raw URLs can carry credentials or user identities. */
  @Bean
  ServerRequestObservationConvention requestObservationConvention() {
    return new DefaultServerRequestObservationConvention() {
      @Override
      public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        return KeyValues.empty();
      }
    };
  }
}
