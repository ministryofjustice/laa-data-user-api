package uk.gov.justice.laa.datauserapi.service;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Component
public class GraphEntraUserClient implements EntraUserClient {
    private final TokenCredential credential;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public GraphEntraUserClient(
            @Value("${azure.tenant-id}") String tenantId,
            @Value("${azure.client-id}") String clientId,
            @Value("${azure.client-secret}") String clientSecret,
            @Value("${azure.graph.base-url}") String baseUrl,
            ClientHttpRequestFactory requestFactory,
            ObjectMapper objectMapper) {
        this.credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId).clientId(clientId).clientSecret(clientSecret).build();
        this.client = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String createUser(CreateUserAccountCommand command) {
        final String token;
        try {
            token = credential.getToken(new TokenRequestContext()
                    .addScopes("https://graph.microsoft.com/.default"))
                    .timeout(Duration.ofSeconds(30)).block().getToken();
        } catch (Exception ex) {
            throw new CreateUserException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Entra ID is unavailable", ex);
        }

        Map<String, Object> body = Map.of(
                "accountEnabled", true,
                "displayName", command.firstName() + " " + command.lastName(),
                "mailNickname", command.email().replaceAll("[^A-Za-z0-9]", ""),
                "userPrincipalName", command.email(),
                "givenName", command.firstName(),
                "surname", command.lastName(),
                "passwordProfile", Map.of(
                        "forceChangePasswordNextSignIn", true,
                        "password", "Temporary-" + UUID.randomUUID() + "!aA1"));
        try {
            String response = client.post().uri("/users")
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(body).retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(response);
            if (!json.hasNonNull("id")) {
                throw new CreateUserException(HttpStatus.BAD_GATEWAY,
                        "Entra ID returned an invalid response");
            }
            return json.get("id").asText();
        } catch (RestClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            if (status == HttpStatus.CONFLICT) {
                throw new CreateUserException(HttpStatus.CONFLICT,
                        "The Entra ID user already exists");
            }
            throw new CreateUserException(HttpStatus.BAD_GATEWAY,
                    "Entra ID returned an unexpected response", ex);
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException
                    || ex.getCause() instanceof TimeoutException) {
                throw new CreateUserException(HttpStatus.GATEWAY_TIMEOUT,
                        "Entra ID did not respond in time", ex);
            }
            throw new CreateUserException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Entra ID is unreachable", ex);
        } catch (CreateUserException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CreateUserException(HttpStatus.BAD_GATEWAY,
                    "Entra ID returned an invalid response", ex);
        }
    }
}
