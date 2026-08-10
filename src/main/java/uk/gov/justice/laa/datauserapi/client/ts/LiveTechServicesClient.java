package uk.gov.justice.laa.datauserapi.client.ts;

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
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountEnabledRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.config.CachingConfig;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    public TechServicesApiResponse<ChangeAccountEnabledResponse> enableUser(EntraUserDto user) {
        try {
            if (user == null || user.getEntraOid() == null) {
                log.error("Invalid user details provided for enable user request to Tech Services.");
                throw new TechServicesClientException("Invalid user details provided for enable user request to Tech Services.");
            }

            String accessToken = getAccessToken();

            ChangeAccountEnabledRequest request = new ChangeAccountEnabledRequest(true);

            log.info("Sending enable user request to Tech Services for: {}", user.getEntraOid());

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
                log.info("Enable user request by Tech Services is successful for entra user {}",
                        user.getEntraOid());
                ChangeAccountEnabledResponse successResponse = objectMapper.readValue(response.getBody(), ChangeAccountEnabledResponse.class);
                return TechServicesApiResponse.success(successResponse);
            } else {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(response.getBody(), TechServicesErrorResponse.class);
                log.error("Failed to enable entra user: {}", user.getEntraOid());
                return TechServicesApiResponse.error(errorResponse);
            }

        } catch (HttpClientErrorException | HttpServerErrorException httpEx) {
            String errorJson = httpEx.getResponseBodyAsString();
            try {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(errorJson, TechServicesErrorResponse.class);
                if (HttpStatus.TOO_EARLY.equals(httpEx.getStatusCode())) {
                    log.info("Failed to enable entra user {}, the root cause is {} ({})",
                            user.getEntraOid(), errorResponse.getMessage(), errorResponse.getCode(), httpEx);
                    return TechServicesApiResponse.error(errorResponse);
                }
                log.error("Failed to enable user {}, the root cause is {} ({})",
                        user.getFirstName() + " " + user.getLastName(), errorResponse.getMessage(), errorResponse.getCode(), httpEx);
                return TechServicesApiResponse.error(errorResponse);
            } catch (Exception ex) {
                log.error("Error while sending enable user request to Tech Services.", ex);
                throw new TechServicesClientException("Error while sending enable user request to Tech Services.", ex);
            }
        } catch (Exception ex) {
            log.error("Error while sending enable user request to Tech Services.", ex);
            throw new TechServicesClientException("Error while sending enable user request to Tech Services.", ex);
        }
    }

    @Override
    public TechServicesApiResponse<ChangeAccountEnabledResponse> disableUser(EntraUserDto user, String reason) {
        try {
            if (user == null || user.getEntraOid() == null) {
                log.error("Invalid user details provided for disable user request to Tech Services.");
                throw new TechServicesClientException("Invalid user details provided for disable user request to Tech Services.");
            }

            String accessToken = getAccessToken();

            ChangeAccountEnabledRequest request = new ChangeAccountEnabledRequest(false, reason);

            log.info("Sending disable user request to Tech Services for: {}", user.getEntraOid());

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
                log.info("Disable user request by Tech Services is successful for entra user{}",
                        user.getEntraOid());
                ChangeAccountEnabledResponse successResponse = objectMapper.readValue(response.getBody(), ChangeAccountEnabledResponse.class);
                return TechServicesApiResponse.success(successResponse);
            } else {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(response.getBody(), TechServicesErrorResponse.class);
                log.error("Failed to disable entra user: {}", user.getEntraOid());
                return TechServicesApiResponse.error(errorResponse);
            }

        } catch (HttpClientErrorException | HttpServerErrorException httpEx) {
            String errorJson = httpEx.getResponseBodyAsString();
            try {
                TechServicesErrorResponse errorResponse = objectMapper.readValue(errorJson, TechServicesErrorResponse.class);
                if (HttpStatus.TOO_EARLY.equals(httpEx.getStatusCode())) {
                    log.info("Failed to disable entra user: {}, the root cause is {} ({})",
                            user.getEntraOid(), errorResponse.getMessage(), errorResponse.getCode(), httpEx);
                    return TechServicesApiResponse.error(errorResponse);
                }
                if (HttpStatus.NOT_FOUND.equals(httpEx.getStatusCode())) {
                    log.warn("User {} not found in Tech Services during disable request, the root cause is {} ({})",
                            user.getEntraOid(), errorResponse.getMessage(), errorResponse.getCode());
                    return TechServicesApiResponse.error(errorResponse);
                }
                log.error("Failed to disable user {}, the root cause is {} ({})",
                        user.getEntraOid(), errorResponse.getMessage(), errorResponse.getCode(), httpEx);
                return TechServicesApiResponse.error(errorResponse);
            } catch (Exception ex) {
                log.error("Error while sending disable user request to Tech Services.", ex);
                throw new TechServicesClientException("Error while sending disable user request to Tech Services.", ex);
            }
        } catch (Exception ex) {
            log.error("Error while sending disable user request to Tech Services.", ex);
            throw new TechServicesClientException("Error while sending disable user request to Tech Services.", ex);
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
                log.info("Error while getting access token from cache", ex);
            }

        }

        String accessToken = Objects.requireNonNull(clientSecretCredential.getToken(new TokenRequestContext()
                .setScopes(List.of(accessTokenRequestScope))).timeout(Duration.of(60, ChronoUnit.SECONDS)).block()).getToken();
        Objects.requireNonNull(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).put(ACCESS_TOKEN, accessToken);

        return accessToken;
    }

}
