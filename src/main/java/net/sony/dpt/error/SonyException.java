package net.sony.dpt.error;

public class SonyException extends Exception {
    private String code;
    private String message;

    public SonyException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
