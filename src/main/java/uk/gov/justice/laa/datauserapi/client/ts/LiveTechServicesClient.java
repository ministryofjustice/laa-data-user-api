package uk.gov.justice.laa.datauserapi.client.ts;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.client.ts.request.CreateUserRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountReactivationRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateSecurityGroupsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorCode;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.UpdateSecurityGroupsResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesResponseMappingException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTokenException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTransportException;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

@Slf4j
public class LiveTechServicesClient implements TechServicesClient {

    private static final String USER_ENDPOINT = "%s/users/%s";
    private static final String USERS_ENDPOINT = "%s/users";
    private final AccessTokenProvider accessTokenProvider;
    private final TechServicesApi techServicesApi;
    private final TechServicesResultMapper resultMapper;
    private final EntraOidValidator validator;
    private final String laaBusinessUnit;
    private final String verificationMethod;
    private final String defaultSecurityGroup;

    public LiveTechServicesClient(
            AccessTokenProvider accessTokenProvider,
            TechServicesApi techServicesApi,
            TechServicesResultMapper resultMapper,
            EntraOidValidator validator,
            String laaBusinessUnit,
            String verificationMethod,
            String defaultSecurityGroup) {
        this.accessTokenProvider = accessTokenProvider;
        this.techServicesApi = techServicesApi;
        this.resultMapper = resultMapper;
        this.validator = validator;
        this.laaBusinessUnit = laaBusinessUnit;
        this.verificationMethod = verificationMethod;
        this.defaultSecurityGroup = defaultSecurityGroup;
    }

    @Override
    public Identity createUser(Registration registration) {
        if (defaultSecurityGroup == null || defaultSecurityGroup.isBlank()) {
            throw new CreateUserException(
                    CreateUserException.Reason.IDENTITY_PROVIDER_FAILURE,
                    HttpStatus.SERVICE_UNAVAILABLE, "Identity provisioning has no default access group configured");
        }

        String accessToken;
        try {
            accessToken = accessTokenProvider.getAccessToken();
        } catch (TechServicesTokenException ex) {
            throw identityProviderUnavailable(ex);
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new CreateUserException(
                    CreateUserException.Reason.IDENTITY_PROVIDER_FAILURE,
                    HttpStatus.BAD_GATEWAY, "Identity provider authentication returned no token");
        }

        CreateUserRequest request = new CreateUserRequest(
                registration.firstName(),
                registration.lastName(),
                registration.email(),
                verificationMethod,
                Set.of(defaultSecurityGroup));
        ResponseEntity<String> response;
        try {
            response = techServicesApi.post(
                    String.format(USERS_ENDPOINT, laaBusinessUnit), request, accessToken);
        } catch (TechServicesTransportException ex) {
            throw identityProviderUnavailable(ex);
        }

        int status = response.getStatusCode().value();
        if (status == HttpStatus.CONFLICT.value()) {
            throw new CreateUserException(CreateUserException.Reason.IDENTITY_ALREADY_EXISTS,
                    HttpStatus.CONFLICT, "Identity provider reported an existing user");
        }
        if (status != HttpStatus.CREATED.value() && status != HttpStatus.OK.value()) {
            throw new CreateUserException(CreateUserException.Reason.IDENTITY_PROVIDER_FAILURE,
                    HttpStatus.BAD_GATEWAY, "Identity provider returned an unexpected response");
        }
        try {
            UUID entraOid = resultMapper.mapCreatedUserId(response.getBody());
            return new Identity(entraOid, status == HttpStatus.CREATED.value());
        } catch (TechServicesResponseMappingException ex) {
            throw new CreateUserException(CreateUserException.Reason.IDENTITY_PROVIDER_FAILURE,
                    HttpStatus.BAD_GATEWAY, "Identity provider returned an invalid identity", ex);
        }
    }

    private CreateUserException identityProviderUnavailable(TechServicesClientException ex) {
        HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        String message = status == HttpStatus.GATEWAY_TIMEOUT
                ? "Identity provider did not respond in time"
                : "Identity provider is unavailable";
        return new CreateUserException(CreateUserException.Reason.IDENTITY_PROVIDER_FAILURE, status, message, ex);
    }

    private boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> reactivateUser(EntraUserDto user) {
        return changeAccountReactivation(user, null, true);
    }

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> deactivateUser(EntraUserDto user, String reason) {
        return changeAccountReactivation(user, reason, false);
    }

    private TechServicesApiResponse<ChangeAccountReactivationResponse> changeAccountReactivation(
            EntraUserDto user, String reason, boolean active) {
        String operation = active ? "reactivation" : "deactivation";
        validator.validateUser(user, operation);
        String entraOid = user.getEntraOid();
        String endpoint = userEndpoint(entraOid);
        ChangeAccountReactivationRequest request = active
                ? new ChangeAccountReactivationRequest(true)
                : new ChangeAccountReactivationRequest(false, reason);

        log.info("Sending user {} request to Tech Services", operation, kv("entraOid", entraOid));
        ResponseEntity<String> response = techServicesApi.patch(endpoint, request, accessTokenProvider.getAccessToken());
        TechServicesApiResponse<ChangeAccountReactivationResponse> result = response.getStatusCode().is2xxSuccessful()
                ? TechServicesApiResponse.success(resultMapper.mapSuccessBody(
                        response.getBody(), ChangeAccountReactivationResponse.class))
                : TechServicesApiResponse.error(resultMapper.mapErrorBody(response.getBody()));

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info(active ? "Tech Services reactivated user" : "Tech Services deactivated user",
                    kv("entraOid", entraOid), kv("status", response.getStatusCode()));
            return result;
        }

        TechServicesErrorResponse error = result.getError();
        if (HttpStatus.TOO_EARLY.equals(response.getStatusCode())) {
            log.info("Tech Services deferred user {}", operation,
                    kv("entraOid", entraOid), kv("errorMessage", error.getMessage()), kv("errorCode", error.getCode()));
        } else if (!active && HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            log.warn("User not found in Tech Services during deactivation",
                    kv("entraOid", entraOid), kv("errorMessage", error.getMessage()), kv("errorCode", error.getCode()));
        } else {
            log.error(active ? "Tech Services failed to reactivate user" : "Tech Services failed to deactivate user",
                    kv("entraOid", entraOid), kv("errorMessage", error.getMessage()), kv("errorCode", error.getCode()));
        }
        return result;
    }

    @Override
    public void deleteRoleAssignment(EntraUserDto entraUser) {
        validator.validateUser(entraUser, "security group removal");
        String endpoint = userEndpoint(entraUser.getEntraOid());
        UpdateSecurityGroupsRequest request = UpdateSecurityGroupsRequest.builder()
                .groups(Collections.emptySet())
                .build();

        log.info("Sending group removal request to Tech Services",
                kv("userId", entraUser.getId()), kv("entraOid", entraUser.getEntraOid()),
                kv("uri", endpoint), kv("groups", request.getGroups()));
        ResponseEntity<String> response =
                techServicesApi.patch(endpoint, request, accessTokenProvider.getAccessToken());

        if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            log.warn("Tech Services returned 404 during group removal; continuing with local deletion",
                    kv("userId", entraUser.getId()), kv("entraOid", entraUser.getEntraOid()));
            return;
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Tech Services rejected security group removal",
                    kv("userId", entraUser.getId()), kv("entraOid", entraUser.getEntraOid()),
                    kv("status", response.getStatusCode()));
            throw new TechServicesClientException(
                    "Failed to remove security groups for user: " + entraUser.getId()
                            + " with entra oid: " + entraUser.getEntraOid()
                            + " with error code " + response.getStatusCode());
        }

        UpdateSecurityGroupsResponse result = resultMapper.mapSecurityGroups(response.getBody());
        log.info("Tech Services removed user security groups",
                kv("userId", entraUser.getId()), kv("entraOid", entraUser.getEntraOid()),
                kv("groupsRemoved", result.getGroupsRemoved()));
    }

    @Override
    public TechServicesApiResponse<ChangeAccountEnabledResponse> updateUserDetails(
            String entraOid, String firstName, String lastName, String email) {
        validator.validateEntraOid(entraOid, "user details update");
        String endpoint = userEndpoint(entraOid);
        UpdateUserDetailsRequest request = new UpdateUserDetailsRequest(firstName, lastName, email);

        log.info("Sending user details update request to Tech Services", kv("entraOid", entraOid));
        ResponseEntity<String> response =
                techServicesApi.patch(endpoint, request, accessTokenProvider.getAccessToken());
        TechServicesApiResponse<ChangeAccountEnabledResponse> result;
        if (response.getStatusCode() == HttpStatus.OK) {
            result = TechServicesApiResponse.success(resultMapper.mapSuccessBody(
                    response.getBody(), ChangeAccountEnabledResponse.class));
        } else if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
            TechServicesErrorResponse error = response.getBody() == null || response.getBody().isBlank()
                    ? errorForStatus(response)
                    : resultMapper.mapErrorBody(response.getBody());
            result = TechServicesApiResponse.error(error);
        } else {
            throw new TechServicesResponseMappingException(
                    "Tech Services returned an unexpected response status: " + response.getStatusCode());
        }

        if (result.isSuccess()) {
            log.info("Tech Services updated user details",
                    kv("entraOid", entraOid), kv("status", response.getStatusCode()), kv("endpoint", endpoint));
        } else if (response.getStatusCode().is4xxClientError()) {
            log.info("Tech Services rejected user details update",
                    kv("entraOid", entraOid), kv("status", response.getStatusCode()), kv("endpoint", endpoint),
                    kv("errorMessage", result.getError().getMessage()), kv("errorCode", result.getError().getCode()));
        } else if (response.getStatusCode().is5xxServerError()) {
            log.warn("Tech Services failed to update user details",
                    kv("entraOid", entraOid), kv("status", response.getStatusCode()), kv("endpoint", endpoint),
                    kv("errorMessage", result.getError().getMessage()), kv("errorCode", result.getError().getCode()));
        } else {
            log.warn("Unexpected response from Tech Services user details update",
                    kv("entraOid", entraOid), kv("status", response.getStatusCode()), kv("endpoint", endpoint));
        }
        return result;
    }

    private String userEndpoint(String entraOid) {
        return String.format(USER_ENDPOINT, laaBusinessUnit, entraOid);
    }

    private TechServicesErrorResponse errorForStatus(ResponseEntity<String> response) {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        return TechServicesErrorResponse.builder()
                .success(false)
                .code(new TechServicesErrorCode(response.getStatusCode().toString()))
                .message(status == null ? response.getStatusCode().toString() : status.getReasonPhrase())
                .build();
    }
}
