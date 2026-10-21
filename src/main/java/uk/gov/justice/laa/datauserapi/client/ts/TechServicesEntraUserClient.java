package uk.gov.justice.laa.datauserapi.client.ts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class TechServicesEntraUserClient implements EntraUserClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final Supplier<String> accessToken;
    private final String businessUnit;
    private final String verificationMethod;
    private final String defaultSecurityGroup;
    private final boolean enabled;

    public TechServicesEntraUserClient(RestClient client, ObjectMapper mapper, Supplier<String> accessToken,
                                      String businessUnit, String verificationMethod, String defaultSecurityGroup,
                                      boolean enabled) {
        this.client = client;
        this.mapper = mapper;
        this.accessToken = accessToken;
        this.businessUnit = businessUnit;
        this.verificationMethod = verificationMethod;
        this.defaultSecurityGroup = defaultSecurityGroup;
        this.enabled = enabled;
    }

    @Override
    public Identity createUser(CreateUserAccountCommand command) {
        if (!enabled) {
            throw new CreateUserException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provisioning is disabled");
        }
        if (defaultSecurityGroup == null || defaultSecurityGroup.isBlank()) {
            throw new CreateUserException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Identity provisioning has no default access security group configured");
        }
        final String token;
        try {
            token = accessToken.get();
        } catch (RuntimeException ex) {
            throw new CreateUserException(isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                    "Identity provider authentication failed", ex);
        }
        if (token == null || token.isBlank()) {
            throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider authentication returned no token");
        }
        try {
            ResponseEntity<String> response = client.post().uri("/{businessUnit}/users", businessUnit)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(Map.of("givenName", command.firstName(), "surname", command.lastName(),
                            "mail", command.email(), "verification_method", verificationMethod,
                            "groups", Set.of(defaultSecurityGroup)))
                    .retrieve().toEntity(String.class);
            if (response.getStatusCode().value() != 201 && response.getStatusCode().value() != 200) {
                throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider returned an unexpected status");
            }
            return parseIdentity(response.getBody(), response.getStatusCode().value() == 201);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                throw new CreateUserException(HttpStatus.CONFLICT, "Identity provider reported an existing user", ex);
            }
            throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider returned an unexpected response", ex);
        } catch (ResourceAccessException ex) {
            throw new CreateUserException(isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                    isTimeout(ex) ? "Identity provider did not respond in time" : "Identity provider is unreachable", ex);
        } catch (RestClientException ex) {
            throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider returned an invalid response", ex);
        }
    }

    private Identity parseIdentity(String body, boolean created) {
        try {
            JsonNode response = body == null ? null : mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (response == null || !response.path("success").isBoolean() || !response.path("success").booleanValue()) {
                throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider returned an invalid response");
            }
            String id = response.path("user").path("id").asText("");
            UUID oid = UUID.fromString(id);
            if (!oid.toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("Non-canonical identity ID");
            }
            return new Identity(oid, created);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new CreateUserException(HttpStatus.BAD_GATEWAY, "Identity provider returned an invalid identity", ex);
        }
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
}
