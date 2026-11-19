package com.sprintflowinsight.workspace.poker;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Stores a session's cards as a JSON array, keeping deck order. */
@Converter
class CardsConverter implements AttributeConverter<List<String>, String> {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<String> cards) {
    return JSON.writeValueAsString(cards == null ? List.of() : cards);
  }

  @Override
  public List<String> convertToEntityAttribute(String stored) {
    return stored == null
        ? List.of()
        : List.copyOf(JSON.readValue(stored, new TypeReference<List<String>>() {}));
  }
}
