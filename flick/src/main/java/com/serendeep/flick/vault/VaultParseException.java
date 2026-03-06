package com.serendeep.flick.vault;

public class VaultParseException extends Exception {
    public VaultParseException(String msg) {
        super(msg);
    }

    public VaultParseException(Throwable cause) {
        super(cause);
    }

    public VaultParseException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
