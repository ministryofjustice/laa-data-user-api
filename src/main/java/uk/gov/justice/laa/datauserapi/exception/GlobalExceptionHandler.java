package uk.gov.justice.laa.datauserapi.exception;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FieldErrorDetail;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_PARAMETER_VALIDATION_DETAIL = "One or more request parameters failed validation.";

    @ExceptionHandler(CreateUserException.class)
    public ResponseEntity<ProblemDetail> handleCreateUser(
            CreateUserException ex,
            HttpServletRequest request) {
        if (ex.status().is5xxServerError()) {
            log.error("User creation failed",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("status", ex.status().value()), kv("requestUri", request.getRequestURI()), ex);
        } else {
            log.warn("User creation rejected",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("status", ex.status().value()), kv("requestUri", request.getRequestURI()), ex);
        }
        String detail = switch (ex.reason()) {
            case INVALID_REQUEST -> REQUEST_PARAMETER_VALIDATION_DETAIL;
            case IDENTITY_ALREADY_EXISTS -> "A user with this email already exists.";
            case IDENTITY_PROVIDER_FAILURE -> "The identity provider could not complete the request.";
            case LOCAL_PERSISTENCE_FAILURE -> "The user could not be stored locally";
        };
        List<FieldErrorDetail> errors = ex.field() == null
                ? null
                : List.of(new FieldErrorDetail(ex.field(), "The field is invalid."));
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://laa.gov.uk/problems/" + ex.status().value()),
                ex.status().getReasonPhrase(),
                ex.status().value(),
                detail,
                URI.create(request.getRequestURI()),
                errors);
        return problemResponse(ex.status(), problem);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<CreateUserAccountResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("User creation rejected: user already exists",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("requestUri", request.getRequestURI()), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.result());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Request content type is not supported",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://laa.gov.uk/errors/validation-error"),
                "Unsupported Media Type",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Request Content-Type must be application/json",
                URI.create(request.getRequestURI()),
                null);
        return problemResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Request method is not supported",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("about:blank"),
                "Method Not Allowed",
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "The HTTP method is not supported for this endpoint",
                URI.create(request.getRequestURI()),
                null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Resource not found",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/not-found"),
                "Resource Not Found",
                HttpStatus.NOT_FOUND.value(),
                "The requested resource was not found.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.NOT_FOUND, problem);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request) {
        log.warn("User lookup failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        return forbiddenResponse(request);
    }

    @ExceptionHandler(FirmNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleFirmNotFound(
            FirmNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Firm not found",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/not-found"),
                "Resource Not Found",
                HttpStatus.NOT_FOUND.value(),
                FirmNotFoundException.CLIENT_DETAIL,
                URI.create(request.getRequestURI()),
                null
        );
        return problemResponse(HttpStatus.NOT_FOUND, problem);
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserProfileNotFound(
            UserProfileNotFoundException ex,
            HttpServletRequest request) {
        log.warn("User profile lookup failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("reason", ex.reason()),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        return forbiddenResponse(request);
    }

    @ExceptionHandler(UserAlreadyMultiFirmException.class)
    public ResponseEntity<ProblemDetail> handleUserAlreadyMultiFirm(
            UserAlreadyMultiFirmException ex,
            HttpServletRequest request) {
        log.warn("Request conflicts with resource state",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()), kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/conflict"),
                "Conflict",
                HttpStatus.CONFLICT.value(),
                "The request conflicts with the current resource state.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.CONFLICT, problem);
    }

    @ExceptionHandler(ReferenceDataNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReferenceDataNotFound(
            ReferenceDataNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Reference data not found",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/server-error"),
                "Reference Data Not Found",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Required reference data is unavailable.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.warn("Request body validation failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
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
        log.warn("Request constraint validation failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(this::toFieldErrorDetail)
                .toList();

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                REQUEST_PARAMETER_VALIDATION_DETAIL,
                URI.create(request.getRequestURI()),
                fieldErrors
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Request body is missing or malformed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()), kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "The request body is missing or malformed.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        log.warn("Required request parameter is missing",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("parameterName", ex.getParameterName()),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                REQUEST_PARAMETER_VALIDATION_DETAIL,
                URI.create(request.getRequestURI()),
                List.of(new FieldErrorDetail(ex.getParameterName(), "is required"))
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(InvalidUuidFormatException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUuidFormat(
            InvalidUuidFormatException ex,
            HttpServletRequest request) {
        log.warn("UUID format validation failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "A request identifier must match UUID format.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn("Request parameter type validation failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("parameterName", ex.getName()),
                kv("requestUri", request.getRequestURI()),
                ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                REQUEST_PARAMETER_VALIDATION_DETAIL,
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
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Request access denied",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

        return forbiddenResponse(request);
    }

    private ResponseEntity<ProblemDetail> forbiddenResponse(HttpServletRequest request) {
        ProblemDetail problem = new ProblemDetail(
                ForbiddenResponseConstants.TYPE,
                ForbiddenResponseConstants.TITLE,
                HttpStatus.FORBIDDEN.value(),
                ForbiddenResponseConstants.DETAIL,
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler({UserAlreadyDeactivatedException.class, UserNotDeactivatedException.class})
    public ResponseEntity<ProblemDetail> handleUserAccountStatusConflict(
            RuntimeException ex,
            HttpServletRequest request) {
        log.warn("User account status conflict",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/conflict"),
                "Conflict",
                HttpStatus.CONFLICT.value(),
                "The user account state does not permit this operation.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.CONFLICT, problem);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ProblemDetail> handleJwtException(
            JwtException ex,
            HttpServletRequest request) {
        log.warn("JWT validation failed",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

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

    @ExceptionHandler(MissingOidException.class)
    public ResponseEntity<ProblemDetail> handleMissingOid(
            MissingOidException ex,
            HttpServletRequest request) {
        log.warn("Authenticated user is missing the required OID claim",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("requestUri", request.getRequestURI()),
                ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/unauthorized"),
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.UNAUTHORIZED, problem);
    }

    @ExceptionHandler(ScopeMissingException.class)
    public ResponseEntity<ProblemDetail> handleScopeMissing(
            ScopeMissingException ex,
            HttpServletRequest request) {
        log.warn("Required scope is missing",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

        return forbiddenResponse(request);
    }

    @ExceptionHandler(InvalidActorContextException.class)
    public ResponseEntity<ProblemDetail> handleInvalidActorContext(
            InvalidActorContextException ex,
            HttpServletRequest request) {
        log.warn("Actor context is invalid",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("reason", ex.reason()),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);

        ProblemDetail problem = new ProblemDetail(
                ForbiddenResponseConstants.TYPE,
                ForbiddenResponseConstants.TITLE,
                HttpStatus.FORBIDDEN.value(),
                "Your account context does not permit this operation.",
                URI.create(request.getRequestURI()),
                null
        );

        return problemResponse(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ProblemDetail> handleTaskRejected(
            TaskRejectedException ex,
            HttpServletRequest request) {
        log.warn("Background task rejected because the executor is saturated",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/service-unavailable"),
                "Service Unavailable",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "The service is busy processing other requests. Please retry later.",
                URI.create(request.getRequestURI()),
                null
        );
        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled request error",
                ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("exceptionMessage", ex.getMessage()),
                kv("requestUri", request.getRequestURI()),
                ex);
        return internalServerErrorResponse(request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest(
            InvalidRequestException ex, HttpServletRequest request) {
        log.warn("Request validation failed", ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("reason", ex.reason()),
                kv("exceptionMessage", ex.getMessage()), kv("requestUri", request.getRequestURI()), ex);
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/validation-error"),
                "Validation Error", HttpStatus.BAD_REQUEST.value(),
                REQUEST_PARAMETER_VALIDATION_DETAIL, URI.create(request.getRequestURI()), null);
        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(TechServicesClientException.class)
    public ResponseEntity<ProblemDetail> handleTechServicesFailure(
            TechServicesClientException ex, HttpServletRequest request) {
        log.error("Tech Services request failed", ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("reason", ex.getReason()),
                kv("requestUri", request.getRequestURI()), ex);
        return internalServerErrorResponse(request);
    }

    @ExceptionHandler(CcmsNotificationException.class)
    public ResponseEntity<ProblemDetail> handleCcmsNotificationFailure(
            CcmsNotificationException ex, HttpServletRequest request) {
        log.error("CCMS notification failed", ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                kv("reason", ex.reason()),
                kv("requestUri", request.getRequestURI()), ex);
        return internalServerErrorResponse(request);
    }

    private ResponseEntity<ProblemDetail> internalServerErrorResponse(HttpServletRequest request) {
        ProblemDetail problem = new ProblemDetail(
                URI.create("https://silas.laa.gov.uk/errors/internal-server-error"),
                "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected downstream or system error occurred.", URI.create(request.getRequestURI()), null);
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
