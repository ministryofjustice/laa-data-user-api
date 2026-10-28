package uk.gov.justice.laa.datauserapi.client.ts;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountReactivationRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateSecurityGroupsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.UpdateSecurityGroupsResponse;
import uk.gov.justice.laa.datauserapi.config.CachingConfig;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public class LiveTechServicesClient implements TechServicesClient {

    public static final String ACCESS_TOKEN = "access_token";
    private static final String TECH_SERVICES_UPDATE_USER_GRP_ENDPOINT = "%s/users/%s";
    private final ClientSecretCredential clientSecretCredential;
    private final RestClient restClient;
    private final CacheManager cacheManager;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;
    @Value("${app.tech.services.laa.business.unit}")
    private String laaBusinessUnit;
    @Value("${spring.security.tech.services.credentials.scope}")
    private String accessTokenRequestScope;

    public LiveTechServicesClient(ClientSecretCredential clientSecretCredential, RestClient restClient, ObjectMapper objectMapper,
                                  CacheManager cacheManager, @Qualifier("tokenExpiryJwtDecoder") JwtDecoder jwtDecoder) {
        this.clientSecretCredential = clientSecretCredential;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> reactivateUser(EntraUserDto user) {
        try {
            if (user == null || user.getEntraOid() == null) {
                log.error("Invalid user details for Tech Services reactivation request",
                        kv("entraOid", user == null ? null : user.getEntraOid()));
                throw new TechServicesClientException("Invalid user details provided for reactivate user request to Tech Services.");
            }

            String accessToken = getAccessToken();

            ChangeAccountReactivationRequest request = new ChangeAccountReactivationRequest(true);

            log.info("Sending user reactivation request to Tech Services",
                    kv("entraOid", user.getEntraOid()));

            String uri = String.format(TECH_SERVICES_UPDATE_USER_GRP_ENDPOINT, laaBusinessUnit, user.getEntraOid());

            ResponseEntity<String> response = restClient
                    .patch()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Tech Services reactivated user",
                        kv("entraOid", user.getEntraOid()),
                        kv("status", response.getStatusCode()));
                ChangeAccountReactivationResponse successResponse = objectMapper.readValue(response.getBody(), ChangeAccountReactivationResponse.class);
                return TechServicesApiResponse.success(successResponse);
            } else {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(response.getBody(), TechServicesErrorResponse.class);
                log.error("Tech Services rejected user reactivation",
                        kv("entraOid", user.getEntraOid()),
                        kv("status", response.getStatusCode()));
                return TechServicesApiResponse.error(errorResponse);
            }

        } catch (HttpClientErrorException | HttpServerErrorException httpEx) {
            String errorJson = httpEx.getResponseBodyAsString();
            try {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(errorJson, TechServicesErrorResponse.class);
                if (HttpStatus.TOO_EARLY.equals(httpEx.getStatusCode())) {
                    log.info("Tech Services deferred user reactivation",
                            kv("entraOid", user.getEntraOid()),
                            kv("errorMessage", errorResponse.getMessage()),
                            kv("errorCode", errorResponse.getCode()),
                            httpEx);
                    return TechServicesApiResponse.error(errorResponse);
                }
                log.error("Tech Services failed to reactivate user",
                        kv("entraOid", user.getEntraOid()),
                        kv("errorMessage", errorResponse.getMessage()),
                        kv("errorCode", errorResponse.getCode()),
                        httpEx);
                return TechServicesApiResponse.error(errorResponse);
            } catch (Exception ex) {
                log.error("Error sending reactivation request to Tech Services",
                        kv("entraOid", user == null ? null : user.getEntraOid()),
                        ex);
                throw new TechServicesClientException("Error while sending reactivate user request to Tech Services.", ex);
            }
        } catch (Exception ex) {
            log.error("Error sending reactivation request to Tech Services",
                    kv("entraOid", user == null ? null : user.getEntraOid()),
                    ex);
            throw new TechServicesClientException("Error while sending reactivate user request to Tech Services.", ex);
        }
    }

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> deactivateUser(EntraUserDto user, String reason) {
        try {
            if (user == null || user.getEntraOid() == null) {
                log.error("Invalid user details for Tech Services deactivation request",
                        kv("entraOid", user == null ? null : user.getEntraOid()));
                throw new TechServicesClientException("Invalid user details provided for deactivate user request to Tech Services.");
            }

            String accessToken = getAccessToken();

            ChangeAccountReactivationRequest request = new ChangeAccountReactivationRequest(false, reason);

            log.info("Sending user deactivation request to Tech Services",
                    kv("entraOid", user.getEntraOid()));

            String uri = String.format(TECH_SERVICES_UPDATE_USER_GRP_ENDPOINT, laaBusinessUnit, user.getEntraOid());

            ResponseEntity<String> response = restClient
                    .patch()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Tech Services deactivated user",
                        kv("entraOid", user.getEntraOid()),
                        kv("status", response.getStatusCode()));
                ChangeAccountReactivationResponse successResponse = objectMapper.readValue(response.getBody(), ChangeAccountReactivationResponse.class);
                return TechServicesApiResponse.success(successResponse);
            } else {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(response.getBody(), TechServicesErrorResponse.class);
                log.error("Tech Services rejected user deactivation",
                        kv("entraOid", user.getEntraOid()),
                        kv("status", response.getStatusCode()));
                return TechServicesApiResponse.error(errorResponse);
            }

        } catch (HttpClientErrorException | HttpServerErrorException httpEx) {
            String errorJson = httpEx.getResponseBodyAsString();
            try {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(errorJson, TechServicesErrorResponse.class);
                if (HttpStatus.TOO_EARLY.equals(httpEx.getStatusCode())) {
                    log.info("Tech Services deferred user deactivation",
                            kv("entraOid", user.getEntraOid()),
                            kv("errorMessage", errorResponse.getMessage()),
                            kv("errorCode", errorResponse.getCode()),
                            httpEx);
                    return TechServicesApiResponse.error(errorResponse);
                }
                if (HttpStatus.NOT_FOUND.equals(httpEx.getStatusCode())) {
                    log.warn("User not found in Tech Services during deactivation",
                            kv("entraOid", user.getEntraOid()),
                            kv("errorMessage", errorResponse.getMessage()),
                            kv("errorCode", errorResponse.getCode()));
                    return TechServicesApiResponse.error(errorResponse);
                }
                log.error("Tech Services failed to deactivate user",
                        kv("entraOid", user.getEntraOid()),
                        kv("errorMessage", errorResponse.getMessage()),
                        kv("errorCode", errorResponse.getCode()),
                        httpEx);
                return TechServicesApiResponse.error(errorResponse);
            } catch (Exception ex) {
                log.error("Error sending deactivation request to Tech Services",
                        kv("entraOid", user == null ? null : user.getEntraOid()),
                        ex);
                throw new TechServicesClientException("Error while sending deactivate user request to Tech Services.", ex);
            }
        } catch (Exception ex) {
            log.error("Error sending deactivation request to Tech Services",
                    kv("entraOid", user == null ? null : user.getEntraOid()),
                    ex);
            throw new TechServicesClientException("Error while sending deactivate user request to Tech Services.", ex);
        }
    }

    @Override
    public void deleteRoleAssignment(EntraUserDto entraUser) {
        try {
            UpdateSecurityGroupsRequest request = UpdateSecurityGroupsRequest
                    .builder()
                    .groups(Collections.emptySet())
                    .build();

            String uri = String.format(TECH_SERVICES_UPDATE_USER_GRP_ENDPOINT, laaBusinessUnit, entraUser.getEntraOid());

            log.info("Sending group removal request to Tech Services",
                    kv("userId", entraUser.getId()),
                    kv("entraOid", entraUser.getEntraOid()),
                    kv("uri", uri),
                    kv("groups", request.getGroups()));

            String accessToken = getAccessToken();
            ResponseEntity<UpdateSecurityGroupsResponse> response = restClient
                    .patch()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(UpdateSecurityGroupsResponse.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                UpdateSecurityGroupsResponse responseBody = response.getBody();
                assert responseBody != null;
                log.info("Tech Services removed user security groups",
                        kv("userId", entraUser.getId()),
                        kv("entraOid", entraUser.getEntraOid()),
                        kv("groupsRemoved", responseBody.getGroupsRemoved()));
            } else {
                log.error("Tech Services rejected security group removal",
                        kv("userId", entraUser.getId()),
                        kv("entraOid", entraUser.getEntraOid()),
                        kv("status", response.getStatusCode()));
                throw new TechServicesClientException("Failed to remove security groups for user: " + entraUser.getId() + " with entra oid: " + entraUser.getEntraOid()
                        + " with error code " + response.getStatusCode());
            }
        } catch (HttpClientErrorException | HttpServerErrorException httpEx) {
            String errorJson = httpEx.getResponseBodyAsString();
            // Do not throw error for 404, user may already be deleted in entra
            if (httpEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Tech Services returned 404 during group removal; continuing with local deletion",
                        kv("userId", entraUser.getId()),
                        kv("entraOid", entraUser.getEntraOid()),
                        kv("responseBody", errorJson));
                return;
            }
            log.error("Tech Services group removal request failed",
                    kv("userId", entraUser.getId()),
                    kv("entraOid", entraUser.getEntraOid()),
                    kv("status", httpEx.getStatusCode()),
                    kv("responseBody", errorJson),
                    httpEx);
            throw new TechServicesClientException("Error while sending security group removal to Tech Services: " + httpEx.getStatusCode(), httpEx);
        } catch (Exception ex) {
            log.error("Error sending security group removal request to Tech Services",
                    kv("userId", entraUser.getId()),
                    kv("entraOid", entraUser.getEntraOid()),
                    ex);
            throw new TechServicesClientException("Error while sending security group removal to Tech Services.", ex);
        }

    }

    private String getAccessToken() {
        Cache cache = cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE);
        if (cache != null) {
            try {
                String accessTokenFromCache = cache.get(ACCESS_TOKEN, String.class);
                if (accessTokenFromCache != null) {
                    Jwt jwt = jwtDecoder.decode(accessTokenFromCache);
                    assert jwt.getExpiresAt() != null;
                    if (jwt.getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
                        return accessTokenFromCache;
                    }
                }
            } catch (Exception ex) {
                log.info("Could not retrieve a valid Tech Services access token from cache",
                        kv("cacheName", CachingConfig.TECH_SERVICES_DETAILS_CACHE),
                        ex);
            }

        }

        String accessToken = Objects.requireNonNull(clientSecretCredential.getToken(new TokenRequestContext()
                .setScopes(List.of(accessTokenRequestScope))).timeout(Duration.of(60, ChronoUnit.SECONDS)).block()).getToken();
        Objects.requireNonNull(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).put(ACCESS_TOKEN, accessToken);

        return accessToken;
    }

}
