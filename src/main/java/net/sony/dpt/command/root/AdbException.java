package net.sony.dpt.command.root;

public class AdbException extends RuntimeException {


    public AdbException(String message) {
        super(message);
    }

    public AdbException(String message, Throwable e) {
        super(message, e);
    }
}
