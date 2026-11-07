package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.bookingapp.exception.ResourceConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Set;

/**
 * Maps service exceptions to RFC 7807 problem responses. Extending
 * ResponseEntityExceptionHandler also covers malformed JSON, bad enum/date values
 * and missing parameters as 400s.
 * <p>
 * Each exception type may appear in exactly one {@code @ExceptionHandler} annotation in
 * this class; Spring refuses to start if two methods claim the same type.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Unique constraint/index names on bookable_resources(type, name). The second one is
     * only needed if you add the case-insensitive index; it is harmless otherwise.
     */
    private static final Set<String> RESOURCE_NAME_UNIQUE_CONSTRAINTS = Set.of(
            "uk_bookable_resources_type_name",
            "uk_bookable_resources_type_name_ci");

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

    /** Admin resource rules: duplicate type+name, resource already used by bookings, etc. */
    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail resourceConflict(ResourceConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    /** Booking and BookableResource have @Version: the row changed between our read and our write. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "This record was changed by another request. Reload it and try again.");
    }

    /** Row locks timed out or deadlocked: transient, safe to retry. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail lockFailure(PessimisticLockingFailureException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Busy", "The system is busy. Please try again.");
    }

    /**
     * Backstop for the duplicate-name race: two admins pass the service's existence check at
     * the same moment and the unique constraint rejects the second write. On update this is
     * thrown at commit, after the service method has returned, so only this advice sees it.
     * Only the known constraint becomes a 409; anything else is a real bug and stays a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
        if (violatesAnyConstraint(ex, RESOURCE_NAME_UNIQUE_CONSTRAINTS)) {
            return problem(HttpStatus.CONFLICT, "Duplicate resource",
                    "A resource with this type and name already exists.");
        }
        log.error("Unexpected data integrity violation", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Data error",
                "The request could not be saved.");
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

    private static boolean violatesAnyConstraint(Throwable ex, Set<String> constraints) {
        // Hibernate exposes the violated constraint's name directly.
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && constraints.stream().anyMatch(c -> c.equalsIgnoreCase(cve.getConstraintName()))) {
                return true;
            }
        }
        // Fallback: PostgreSQL's message quotes the constraint name.
        String message = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        return message != null && constraints.stream().anyMatch(message::contains);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}