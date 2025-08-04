package com.example.rednote.auth.tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SerializaUtil {
@Autowired
private ObjectMapper objectMapper;

public String toJson(Object obj) throws JsonProcessingException {
    return objectMapper.writeValueAsString(obj);
}

public <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
    return objectMapper.readValue(json, clazz);
}
}
