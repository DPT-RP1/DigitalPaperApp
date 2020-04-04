package net.sony.util;

import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

public class StringUtils {

    @SafeVarargs
    public static String resolve(String template, Map<String, String>... variables) {
        Map<String, String> finalMap = new HashMap<>();
        for (Map<String, String> variable : variables) {
            finalMap.putAll(variable);
        }
        StringSubstitutor stringSubstitutor = new StringSubstitutor(finalMap);
        return stringSubstitutor.replace(template);
    }

    public static Map<String, String> variable(String name, String value) {
        return new HashMap<>() {{
            put(name, value);
        }};
    }

}
