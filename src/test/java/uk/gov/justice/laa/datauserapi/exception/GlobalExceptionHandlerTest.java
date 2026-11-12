package uk.gov.justice.laa.datauserapi.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpInputMessage;
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
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/queries/users");
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void handleNotFound_shouldReturn404WithProblemDetail() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Resource Not Found", response.getBody().title());
    }

    @Test
    void handleUserNotFound_shouldReturnGeneric403WithProblemDetail() {
        UserNotFoundException ex = new UserNotFoundException("user-id");

        ResponseEntity<ProblemDetail> response = handler.handleUserNotFound(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("You do not possess the required scopes or permissions to perform this operation.",
                response.getBody().detail());
        assertThat(appender.list.getFirst().getThrowableProxy().getClassName())
                .isEqualTo(UserNotFoundException.class.getName());
        assertEquals(handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("Access denied"),
                request).getBody(), response.getBody());
    }

    @ParameterizedTest
    @EnumSource(UserProfileNotFoundReason.class)
    void handleUserProfileNotFound_shouldReturnGeneric403WithProblemDetail(UserProfileNotFoundReason reason) {
        java.util.UUID id = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserProfileNotFoundException ex = switch (reason) {
            case PROFILE_ID_NOT_FOUND -> new UserProfileNotFoundException(id);
            case ACTIVE_PROFILE_NOT_FOUND -> UserProfileNotFoundException.activeProfileForUser(id.toString());
        };

        ResponseEntity<ProblemDetail> response = handler.handleUserProfileNotFound(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("You do not possess the required scopes or permissions to perform this operation.",
                response.getBody().detail());
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("reason=" + reason);
        assertThat(appender.list.getFirst().getThrowableProxy().getClassName())
                .isEqualTo(UserProfileNotFoundException.class.getName());
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("exception_group=user_profile_not_found",
                        "exception_reason=" + reason.name().toLowerCase(java.util.Locale.ROOT));
        assertEquals(handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("Access denied"),
                request).getBody(), response.getBody());
    }

    @Test
    void handleFirmNotFound_shouldReturn404WithProblemDetail() {
        when(request.getRequestURI()).thenReturn("/api/v1/queries/firms/999999");
        FirmNotFoundException ex = new FirmNotFoundException("999999");

        ResponseEntity<ProblemDetail> response = handler.handleFirmNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Resource Not Found", response.getBody().title());
        assertEquals("Firm not found", response.getBody().detail());
        assertEquals(java.net.URI.create("/api/v1/queries/firms/999999"), response.getBody().instance());
    }

    @Test
    void handleUserAccountStatusConflict_shouldReturn409WithProblemDetail() {
        UserAlreadyDeactivatedException ex = new UserAlreadyDeactivatedException(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ResponseEntity<ProblemDetail> response = handler.handleUserAccountStatusConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().title());
    }

    @Test
    void handleConflict_shouldReturn409WithProblemDetail() {
        ConflictException ex = new ConflictException("User is already a multi-firm user");

        ResponseEntity<ProblemDetail> response = handler.handleConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().title());
        assertEquals("The request conflicts with the current resource state.", response.getBody().detail());
    }

    @Test
    void handleUnreadableBody_shouldReturn400WithProblemDetail() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", mock(HttpInputMessage.class));

        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation Error", response.getBody().title());
        assertEquals("The request body is missing or malformed.", response.getBody().detail());
    }

    @Test
    void handleScopeMissing_shouldReturn403WithProblemDetail() {
        ScopeMissingException ex = new ScopeMissingException("Missing user.read or user.admin scope");

        ResponseEntity<ProblemDetail> response = handler.handleScopeMissing(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("You do not possess the required scopes or permissions to perform this operation.",
                response.getBody().detail());
    }

    @ParameterizedTest
    @EnumSource(InvalidActorContextReason.class)
    void handleInvalidActorContext_shouldReturn403WithProblemDetail(InvalidActorContextReason reason) {
        InvalidActorContextException ex = switch (reason) {
            case NO_ACTIVE_PROFILE -> InvalidActorContextException.noActiveProfile(
                    "11111111-1111-1111-1111-111111111111");
            case UNDETERMINED_USER_TYPE -> InvalidActorContextException.undeterminedUserType();
            case MISSING_ACTOR_IDENTITY -> InvalidActorContextException.missingActorIdentity();
            case INVALID_ACTOR_IDENTITY -> InvalidActorContextException.invalidActorIdentity(
                    new IllegalArgumentException("Invalid UUID"));
        };

        ResponseEntity<ProblemDetail> response = handler.handleInvalidActorContext(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("Your account context does not permit this operation.", response.getBody().detail());
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("reason=" + reason);
        assertThat(response.getBody().toString()).doesNotContain(reason.name(), "11111111-1111-1111-1111-111111111111");
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("exception_group=invalid_actor_context",
                        "exception_reason=" + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void handleReferenceDataNotFound_shouldHideConfigurationDetails() {
        ReferenceDataNotFoundException ex = new ReferenceDataNotFoundException("Unknown delete reason code: INTERNAL_CODE");

        ResponseEntity<ProblemDetail> response = handler.handleReferenceDataNotFound(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals("Required reference data is unavailable.", response.getBody().detail());
        assertEquals("Reference Data Not Found", response.getBody().title());
    }

    @Test
    void handleGenericException_shouldHideDownstreamDetails() {
        Exception ex = new IllegalStateException("SQL failure on internal-host:5432; downstream response: private");

        ResponseEntity<ProblemDetail> response = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals("An unexpected downstream or system error occurred.", response.getBody().detail());
    }

    @Test
    void handleInvalidUuidFormat_shouldReturn400WithProblemDetail() {
        InvalidUuidFormatException ex = new InvalidUuidFormatException(
                "userEntraObjectId must be a valid UUID format");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidUuidFormat(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation Error", response.getBody().title());
        assertEquals("A request identifier must match UUID format.", response.getBody().detail());
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
        assertProblemContentType(response);
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
        assertProblemContentType(response);
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
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("Unauthorized", response.getBody().title());
        assertEquals("Invalid or malformed authentication token", response.getBody().detail());
    }

    @Test
    void forbiddenResponseDoesNotEchoResourceOrAuthorizationDetails() {
        var ex = new org.springframework.security.access.AccessDeniedException(
                "Target user secret-user belongs to secret-firm; missing secret-role");

        ResponseEntity<ProblemDetail> response = handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemContentType(response);
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().title());
        assertEquals("You do not possess the required scopes or permissions to perform this operation.",
                response.getBody().detail());
        org.junit.jupiter.api.Assertions.assertNull(response.getBody().errors());
    }

    private void assertProblemContentType(ResponseEntity<ProblemDetail> response) {
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    }

    @Test
    void diagnosticMessagesNeverBecomeClientResponseDetails() {
        String diagnostic = "secret-user secret-firm secret-role secret-config";
        List<ResponseEntity<ProblemDetail>> responses = List.of(
                handler.handleNotFound(new ResourceNotFoundException(diagnostic), request),
                handler.handleConflict(new ConflictException(diagnostic), request),
                handler.handleInvalidUuidFormat(new InvalidUuidFormatException(diagnostic), request),
                handler.handleReferenceDataNotFound(new ReferenceDataNotFoundException(diagnostic), request),
                handler.handleJwtException(new JwtException(diagnostic), request),
                handler.handleGenericException(new IllegalStateException(diagnostic), request),
                handler.handleTechServicesFailure(new TechServicesClientException(diagnostic), request),
                handler.handleInvalidRequest(new InvalidRequestException(
                        InvalidRequestException.Reason.INVALID_FIRM_SORT, diagnostic), request),
                handler.handleUserAccountStatusConflict(new UserAlreadyDeactivatedException(
                        UUID.fromString("11111111-1111-1111-1111-111111111111")), request));

        for (ResponseEntity<ProblemDetail> response : responses) {
            assertProblemContentType(response);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().toString()).doesNotContain(
                    diagnostic, "11111111-1111-1111-1111-111111111111");
        }
    }

    @ParameterizedTest
    @EnumSource(InvalidRequestException.Reason.class)
    void invalidRequestReasonsAreLoggedButNotReturned(InvalidRequestException.Reason reason) {
        ResponseEntity<ProblemDetail> response = handler.handleInvalidRequest(
                new InvalidRequestException(reason, "private diagnostic"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertThat(response.getBody().detail()).isEqualTo("One or more request parameters failed validation.");
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("reason=" + reason);
        assertThat(appender.list.getFirst().getArgumentArray()).extracting(Object::toString)
                .contains("exception_group=invalid_request", "exception_reason=" + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(TechServicesClientException.Reason.class)
    void logsHandledTechServicesFailureToken(TechServicesClientException.Reason reason) {
        handler.handleTechServicesFailure(new TechServicesClientException(reason, "private diagnostic"), request);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=tech_services_client",
                            "exception_reason=" + reason.name().toLowerCase(java.util.Locale.ROOT));
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @ParameterizedTest
    @EnumSource(CcmsNotificationException.Reason.class)
    void logsHandledCcmsFailureToken(CcmsNotificationException.Reason reason) {
        var response = handler.handleCcmsNotificationFailure(
                new CcmsNotificationException(reason, "private diagnostic", null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().detail()).doesNotContain("private diagnostic", reason.name());
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=ccms_notification", "exception_reason=" + reason.name().toLowerCase(java.util.Locale.ROOT));
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void handleTaskRejected_shouldReturn503WithoutDiagnostics() {
        when(request.getRequestURI()).thenReturn("/api/v1/commands/firms/bulk-deactivate-users");

        ResponseEntity<ProblemDetail> response = handler.handleTaskRejected(
                new org.springframework.core.task.TaskRejectedException("queue full: executor internals"), request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("The service is busy processing other requests. Please retry later.",
                response.getBody().detail());
    }
}
