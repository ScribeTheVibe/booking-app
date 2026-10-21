package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps service exceptions to RFC 7807 problem responses. Extending
 * ResponseEntityExceptionHandler also covers malformed JSON, bad enum/date values
 * and missing parameters as 400s.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail notFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    /** Rule violations raised by the service (closed day, started session, bad ticket count...). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(InsufficientAvailabilityException.class)
    public ProblemDetail noAvailability(InsufficientAvailabilityException ex) {
        return problem(HttpStatus.CONFLICT, "Not enough availability", ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ProblemDetail invalidState(InvalidBookingStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid booking state", ex.getMessage());
    }

    /** Booking has @Version: someone changed it between our read and our write. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The booking was changed by another request. Reload it and try again.");
    }

    /** Resource row locks timed out or deadlocked: transient, safe to retry. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail lockFailure(PessimisticLockingFailureException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Busy", "The system is busy. Please try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail body = ex.getBody();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}