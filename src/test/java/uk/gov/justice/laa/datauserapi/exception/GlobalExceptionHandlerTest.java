package uk.gov.justice.laa.datauserapi.exception;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import jakarta.servlet.http.HttpServletRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/queries/users");
    }

    @Test
    void handleNotFound_shouldReturn404WithProblemDetail() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Resource Not Found", response.getBody().title());
    }

    @Test
    void handleScopeMissing_shouldReturn403WithProblemDetail() {
        ScopeMissingException ex = new ScopeMissingException("Missing user.read or user.admin scope");

        ResponseEntity<ProblemDetail> response = handler.handleScopeMissing(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("Missing user.read or user.admin scope", response.getBody().detail());
    }

    @Test
    void handleInvalidActorContext_shouldReturn403WithProblemDetail() {
        InvalidActorContextException ex = new InvalidActorContextException(
                "Actor has no active profile");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidActorContext(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("Actor has no active profile", response.getBody().detail());
    }

    @Test
    void handleInvalidUuidFormat_shouldReturn400WithProblemDetail() {
        InvalidUuidFormatException ex = new InvalidUuidFormatException(
                "userEntraObjectId must be a valid UUID format");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidUuidFormat(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation Error", response.getBody().title());
        assertEquals("userEntraObjectId must be a valid UUID format", response.getBody().detail());
    }

    @Test
    void handleValidationErrors_shouldReturn400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("criteria", "pageSize", "must be <= 100");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ProblemDetail> response = handler.handleValidationErrors(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation Error", response.getBody().title());
    }

    @Test
    void handleMissingRequestParameter_shouldReturn400WithFieldError() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("query", "String");

        ResponseEntity<ProblemDetail> response = handler.handleMissingRequestParameter(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation Error", response.getBody().title());
        assertEquals("One or more request parameters failed validation.", response.getBody().detail());
        assertEquals("query", response.getBody().errors().getFirst().field());
        assertEquals("is required", response.getBody().errors().getFirst().detail());
    }

    @Test
    void handleJwtException_shouldReturn401WithProblemDetail() {
        JwtException ex = new JwtException("Invalid or malformed token");

        ResponseEntity<ProblemDetail> response = handler.handleJwtException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("Unauthorized", response.getBody().title());
        assertEquals("Invalid or malformed authentication token", response.getBody().detail());
    }
}
