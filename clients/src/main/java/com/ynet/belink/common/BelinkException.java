package com.ynet.belink.common;

/**
 * Created by goldratio on 27/06/2017.
 */
public class BelinkException extends RuntimeException {

    private final static long serialVersionUID = 1L;

    public BelinkException(String message, Throwable cause) {
        super(message, cause);
    }

    public BelinkException(String message) {
        super(message);
    }

    public BelinkException(Throwable cause) {
        super(cause);
    }

    public BelinkException() {
        super();
    }

}
