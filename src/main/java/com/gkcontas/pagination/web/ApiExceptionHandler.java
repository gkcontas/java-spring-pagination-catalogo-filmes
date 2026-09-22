package com.gkcontas.pagination.web;

import com.gkcontas.pagination.exception.InvalidCursorException;
import com.gkcontas.pagination.exception.InvalidSortException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCursorException.class)
    public ProblemDetail handleInvalidCursor(InvalidCursorException exception) {
        return problem("Invalid cursor", exception.getMessage());
    }

    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException exception) {
        return problem("Invalid sort", exception.getMessage());
    }

    /**
     * Raised by Spring's built-in method validation for the {@code @Min}/{@code @Max}
     * constraints on the request parameters.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException exception) {
        return problem("Invalid request parameter", "One or more request parameters are out of range.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem("Invalid request parameter", exception.getMessage());
    }

    private static ProblemDetail problem(String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
