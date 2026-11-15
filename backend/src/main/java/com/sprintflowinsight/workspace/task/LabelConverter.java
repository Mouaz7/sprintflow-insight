package com.sprintflowinsight.workspace.task;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class LabelConverter implements AttributeConverter<List<String>, String> {
  private static final ObjectMapper JSON = new ObjectMapper();

  public String convertToDatabaseColumn(List<String> value) {
    try {
      return JSON.writeValueAsString(value == null ? List.of() : value);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid labels.", ex);
    }
  }

  public List<String> convertToEntityAttribute(String value) {
    try {
      return value == null
          ? List.of()
          : JSON.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid stored labels.", ex);
    }
  }
}
