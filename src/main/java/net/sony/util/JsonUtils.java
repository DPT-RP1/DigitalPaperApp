package net.sony.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJSON(String json) throws IOException {
        return (Map<String, Object>) mapper.readValue(json, Map.class);
    }

    public static <T> T fromJSON(String json, Class<T> clazz) throws IOException {
        return mapper.readValue(json, clazz);
    }

    public static String writeValueAsString(Object serializable) throws JsonProcessingException {
        return mapper.writeValueAsString(serializable);
    }

}
