package com.sprintflowinsight.model;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
@Converter
public class ChecklistConverter implements AttributeConverter<List<ChecklistStep>,String> {
 private static final ObjectMapper JSON=new ObjectMapper();
 public String convertToDatabaseColumn(List<ChecklistStep> value){try{return JSON.writeValueAsString(value==null?List.of():value);}catch(Exception ex){throw new IllegalArgumentException("Invalid checklist.",ex);}}
 public List<ChecklistStep> convertToEntityAttribute(String value){try{return value==null?List.of():JSON.readValue(value,new TypeReference<List<ChecklistStep>>(){});}catch(Exception ex){throw new IllegalStateException("Invalid stored checklist.",ex);}}
}
