package org.correomqtt.di;

public class SoyDiException extends RuntimeException {
    public SoyDiException(String msg) {
        super(msg);
    }

    public SoyDiException(String msg, Exception e) {
        super(msg, e);
    }
}
