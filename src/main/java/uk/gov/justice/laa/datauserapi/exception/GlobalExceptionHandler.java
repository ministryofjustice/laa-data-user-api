package uk.gov.justice.laa.datauserapi.exception;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.datauserapi.contracts.response.FieldErrorDetail;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CcmsSyncException.class)
    public ResponseEntity<ProblemDetail> handleCcmsSyncFailure(
            CcmsSyncException ex, HttpServletRequest request) {
        log.warn("CCMS role sync failed",
                kv("status", ex.getStatus().value()),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/ccms-sync-failed"),
                ex.getStatus().getReasonPhrase(),
                ex.getStatus().value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );
        return problemResponse(ex.getStatus(), problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/not-found"),
                "Resource Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.NOT_FOUND, problem);
    }

    @ExceptionHandler(ReferenceDataNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReferenceDataNotFound(
            ReferenceDataNotFoundException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/server-error"),
                "Reference Data Not Found",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "One or more fields in the request body failed validation.",
                URI.create(request.getRequestURI()),
                fieldErrors
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(this::toFieldErrorDetail)
                .toList();

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "One or more request parameters failed validation.",
                URI.create(request.getRequestURI()),
                fieldErrors
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "One or more request parameters failed validation.",
                URI.create(request.getRequestURI()),
                List.of(new FieldErrorDetail(ex.getParameterName(), "is required"))
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(InvalidUuidFormatException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUuidFormat(
            InvalidUuidFormatException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "One or more request parameters failed validation.",
                URI.create(request.getRequestURI()),
                List.of(new FieldErrorDetail(ex.getName(), toTypeMismatchMessage(ex)))
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    private String toTypeMismatchMessage(MethodArgumentTypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            String allowedValues = Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "must be one of " + allowedValues;
        }
        return "is invalid";
    }

    private FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new FieldErrorDetail(field, violation.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/forbidden"),
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "You do not possess the required scopes or permissions to perform this operation.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ProblemDetail> handleJwtException(
            JwtException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/unauthorized"),
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid or malformed authentication token",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.UNAUTHORIZED, problem);
    }

    @ExceptionHandler(ScopeMissingException.class)
    public ResponseEntity<ProblemDetail> handleScopeMissing(
            ScopeMissingException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/forbidden"),
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler(InvalidActorContextException.class)
    public ResponseEntity<ProblemDetail> handleInvalidActorContext(
            InvalidActorContextException ex,
            HttpServletRequest request) {
        log.warn(ex.getMessage(), ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/forbidden"),
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error(ex.getMessage(), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/internal-server-error"),
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected downstream or system error occurred.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
