package com.gkcontas.pagination.exception;

import java.util.Collection;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String field, Collection<String> allowedFields) {
        super("Invalid sort field: '%s'. Allowed fields: %s.".formatted(field, String.join(", ", allowedFields)));
    }
}
