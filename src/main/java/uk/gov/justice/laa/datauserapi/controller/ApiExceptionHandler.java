package uk.gov.justice.laa.datauserapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = CreateUserAccountController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> handleDatabaseLookup(DataAccessException ex, HttpServletRequest request) {
        log.error("User creation failed correlationId={} step=local_lookup system=PostgreSQL exceptionType={}",
                MDC.get("correlationId"), ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "The local user data could not be accessed", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request Content-Type must be application/json", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("User creation failed correlationId={} step=request_processing exceptionType={}",
                MDC.get("correlationId"), ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected system error occurred", request, null);
    }

    @ExceptionHandler(CreateUserException.class)
    ResponseEntity<ProblemDetail> handleCreateUser(CreateUserException ex, HttpServletRequest request) {
        return problem(ex.status(), ex.getMessage(), request,
                ex.field() == null ? null : List.of(Map.of("field", ex.field(), "detail", ex.getMessage())));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<CreateUserAccountResult> handleDuplicate(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(ex.result());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "An internal actor with CREATE_EXTERNAL_USER permission is required", request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request body must contain valid JSON with the documented field types",
                request, List.of(Map.of("field", "body", "detail", "Malformed or missing request body")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                  HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(),
                        "detail", error.getDefaultMessage())).toList();
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", request, errors);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail,
                                                 HttpServletRequest request, List<?> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("https://laa.gov.uk/problems/" + status.value()));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("correlationId", MDC.get("correlationId"));
        if (errors != null) {
            body.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body);
    }
}
