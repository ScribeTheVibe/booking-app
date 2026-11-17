package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.exception.DuplicateFieldException;
import com.bookingapp.bookingapp.exception.EmailAlreadyInUseException;
import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.bookingapp.exception.InvalidUserCredentialException;
import com.bookingapp.bookingapp.exception.PhoneAlreadyInUseException;
import com.bookingapp.bookingapp.exception.ResourceConflictException;
import com.bookingapp.bookingapp.exception.UserNotFoundException;
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

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Set<String> RESOURCE_NAME_UNIQUE_CONSTRAINTS = Set.of(
            "uk_bookable_resources_type_name",
            "uk_bookable_resources_type_name_ci");

    // ---------- users / auth ----------

    @ExceptionHandler(InvalidUserCredentialException.class)
    public ProblemDetail invalidCredentials(InvalidUserCredentialException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail userNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler({EmailAlreadyInUseException.class,
            PhoneAlreadyInUseException.class,
            DuplicateFieldException.class})
    public ProblemDetail duplicateUserField(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // ---------- bookings / resources ----------

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail notFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

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

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail resourceConflict(ResourceConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // ---------- persistence ----------

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "This record was changed by another request. Reload it and try again.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail lockFailure(PessimisticLockingFailureException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Busy", "The system is busy. Please try again.");
    }

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

    // ---------- validation ----------

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

    // ---------- helpers ----------

    private static boolean violatesAnyConstraint(Throwable ex, Set<String> constraints) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && constraints.stream().anyMatch(c -> c.equalsIgnoreCase(cve.getConstraintName()))) {
                return true;
            }
        }
        String message = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        return message != null && constraints.stream().anyMatch(message::contains);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}