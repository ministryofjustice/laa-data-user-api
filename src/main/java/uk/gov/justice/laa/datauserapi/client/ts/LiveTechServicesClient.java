package uk.gov.justice.laa.datauserapi.client.ts;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountReactivationRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateSecurityGroupsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.UpdateSecurityGroupsResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Slf4j
public class LiveTechServicesClient implements TechServicesClient {

    private static final String USER_ENDPOINT = "%s/users/%s";
    private final AccessTokenProvider accessTokenProvider;
    private final TechServicesApi techServicesApi;
    private final TechServicesResultMapper resultMapper;
    private final UserCommandValidator validator;
    private final String laaBusinessUnit;

    public LiveTechServicesClient(
            AccessTokenProvider accessTokenProvider,
            TechServicesApi techServicesApi,
            TechServicesResultMapper resultMapper,
            UserCommandValidator validator,
            String laaBusinessUnit) {
        this.accessTokenProvider = accessTokenProvider;
        this.techServicesApi = techServicesApi;
        this.resultMapper = resultMapper;
        this.validator = validator;
        this.laaBusinessUnit = laaBusinessUnit;
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
        TechServicesApiResponse<ChangeAccountReactivationResponse> result = resultMapper.mapAccountChange(response);

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

        UpdateSecurityGroupsResponse result = resultMapper.mapSecurityGroups(response);
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
        TechServicesApiResponse<ChangeAccountEnabledResponse> result = resultMapper.mapUserDetails(response);

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
}
