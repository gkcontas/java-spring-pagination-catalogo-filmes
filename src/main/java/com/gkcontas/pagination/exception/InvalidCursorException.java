package com.gkcontas.pagination.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: '%s'. Use the 'nextCursor' returned by the previous page.".formatted(cursor));
    }
}
