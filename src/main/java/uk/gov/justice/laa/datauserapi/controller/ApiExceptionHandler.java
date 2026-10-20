package uk.gov.justice.laa.datauserapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(CreateUserException.class)
    ResponseEntity<ProblemDetail> handleCreateUser(CreateUserException ex, HttpServletRequest request) {
        return problem(ex.status(), ex.getMessage(), request, null);
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
                                                   HttpServletRequest request,
                                                   List<?> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("https://laa.gov.uk/problems/" + status.value()));
        body.setInstance(URI.create(request.getRequestURI()));
        if (errors != null) {
            body.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body);
    }
}
