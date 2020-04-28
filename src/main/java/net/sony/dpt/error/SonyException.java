package net.sony.dpt.error;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SonyException extends IOException {

    @JsonProperty("error_code")
    private String code;

    private String message;

    public SonyException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public SonyException() {

    }

    public String getCode() {
        return code;
    }

    public ErrorCode getCodeParsed() {
        return ErrorCode.findByCode(Integer.parseInt(code));
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String print() {
        return code + " : " + message;
    }

    public enum ErrorCode {
        BAD_PARAMETER(40301), RESOURCE_NOT_FOUND(40401), UNKNOWN(-1);

        private static final Map<Integer, ErrorCode> errorCodeMap;

        static {
            errorCodeMap = new HashMap<>();
            for (ErrorCode errorCode : ErrorCode.values()) {
                errorCodeMap.put(errorCode.code, errorCode);
            }
        }

        public static ErrorCode findByCode(int code) {
            return errorCodeMap.getOrDefault(code, UNKNOWN);
        }

        private final int code;

        ErrorCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
