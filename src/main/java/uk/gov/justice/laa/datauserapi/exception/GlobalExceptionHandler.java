package uk.gov.justice.laa.datauserapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.datauserapi.contracts.response.FieldErrorDetail;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

import java.net.URI;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
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

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
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

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
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

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
