package com.hms.service.import_;

/** The client's header→field mapping was refused. The message names headers and field keys only — never cell content. */
public class InvalidImportMappingException extends RuntimeException {
    public InvalidImportMappingException(String message) {
        super(message);
    }
}
