package com.hms.exception;

/** A parsed upload is over its route's file ceiling — the controller's own check behind the pre-parse guard. */
public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException(String message) {
        super(message);
    }
}
