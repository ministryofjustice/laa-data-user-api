package uk.gov.justice.laa.datauserapi.client.ts;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountEnabledRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.config.CachingConfig;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveTechServicesClientTest {

    private final String mockToken = "mocked-jwt-token";
    @Mock
    private ClientSecretCredential clientSecretCredential;
    @Mock
    private RestClient restClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    private LiveTechServicesClient client;
    private EntraUserDto validUser;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        client = new LiveTechServicesClient(clientSecretCredential, restClient, objectMapper, cacheManager, jwtDecoder);
        ReflectionTestUtils.setField(client, "laaBusinessUnit", "test-unit");
        ReflectionTestUtils.setField(client, "accessTokenRequestScope", "test-scope");

        validUser = new EntraUserDto();
        validUser.setEntraOid("user-123");
        validUser.setFirstName("John");
        validUser.setLastName("Doe");

        logger = (Logger) LoggerFactory.getLogger(LiveTechServicesClient.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    private void mockTokenGeneration() {
        when(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).thenReturn(cache);
        when(cache.get(LiveTechServicesClient.ACCESS_TOKEN, String.class)).thenReturn(null);

        AccessToken accessToken = mock(AccessToken.class);
        when(accessToken.getToken()).thenReturn(mockToken);
        when(clientSecretCredential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(accessToken));
    }

    private void mockFluentRestClient(ResponseEntity<String> responseEntity) {
        when(restClient.patch()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer " + mockToken))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class)).thenReturn(responseEntity);
    }

    @Nested
    class EnableUserTests {

        @Test
        void shouldReturnSuccessAndLogWhenApiReturns2xx() throws Exception {
            mockTokenGeneration();
            String jsonResponse = "{\"status\":\"success\"}";
            mockFluentRestClient(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

            ChangeAccountEnabledResponse expectedDto = new ChangeAccountEnabledResponse();
            when(objectMapper.readValue(jsonResponse, ChangeAccountEnabledResponse.class)).thenReturn(expectedDto);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.enableUser(validUser);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(expectedDto);
            verify(cache).put(LiveTechServicesClient.ACCESS_TOKEN, mockToken);

            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending enable user request to Tech Services for: user-123",
                            "Enable user request by Tech Services is successful for entra user user-123");
        }

        @Test
        void shouldReturnErrorAndLogWhenApiReturnsNon2xx() throws Exception {
            mockTokenGeneration();
            String jsonErrorResponse = "{\"code\":\"ERR01\",\"message\":\"Invalid user state\"}";
            mockFluentRestClient(new ResponseEntity<>(jsonErrorResponse, HttpStatus.BAD_REQUEST));

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("ERR01").message("Invalid user state").build();
            when(objectMapper.readValue(jsonErrorResponse, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.enableUser(validUser);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);

            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage).contains("Failed to enable entra user: user-123");
        }

        @Test
        void shouldLogInfoOnHttpClientErrorExceptionTooEarly() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"TOO_EARLY\",\"message\":\"Request too early\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.TOO_EARLY, "Too Early", HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("TOO_EARLY").message("Request too early").build();
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.enableUser(validUser);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);
            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(msg -> msg.contains("Failed to enable entra user user-123, the root cause is Request too early (TOO_EARLY)"));
        }

        @Test
        void shouldLogInfoOnHttpClientErrorException() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"ERROR\",\"message\":\"Error\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Error",
                    HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("ERROR").message("Error").build();
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.enableUser(validUser);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);
            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending enable user request to Tech Services for: user-123",
                            "Failed to enable user John Doe, the root cause is Error (ERROR)");
        }

        @Test
        void shouldLogInfoOnHttpClientErrorExceptionUnexpectedResponse() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"ERROR\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Error",
                    HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenThrow(new RuntimeException("Error Parsing"));

            assertThatThrownBy(() -> client.enableUser(validUser)).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error while sending enable user request to Tech Services.");
        }

        @Test
        void shouldThrowRuntimeExceptionWhenUserIsNull() {
            assertThatThrownBy(() -> client.enableUser(null)).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error while sending enable user request to Tech Services.");
        }

        @Test
        void shouldThrowRuntimeExceptionWhenUserOidIsNull() {
            EntraUserDto userNoOid = new EntraUserDto();
            assertThatThrownBy(() -> client.enableUser(userNoOid)).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error while sending enable user request to Tech Services.");
        }
    }

    @Nested
    class DisableUserTests {

        @Test
        void shouldReturnSuccessWhenApiReturns2xx() throws Exception {
            mockTokenGeneration();
            String jsonResponse = "{\"status\":\"disabled\"}";
            mockFluentRestClient(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

            ChangeAccountEnabledResponse expectedDto = new ChangeAccountEnabledResponse();
            when(objectMapper.readValue(jsonResponse, ChangeAccountEnabledResponse.class)).thenReturn(expectedDto);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.disableUser(validUser, "Leaver");

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(expectedDto);
        }

        @Test
        void shouldLogWarnWhenNotFoundOccurs() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"404\",\"message\":\"Not Found\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("404").message("Not Found").build();
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.disableUser(validUser, "Leaver");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);

            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending disable user request to Tech Services for: user-123",
                            "User user-123 not found in Tech Services during disable request, the root cause is Not Found (404)");
        }

        @Test
        void shouldReturnErrorAndLogWhenApiReturnsNon2xx() throws Exception {
            mockTokenGeneration();
            String jsonErrorResponse = "{\"code\":\"ERR01\",\"message\":\"Invalid user state\"}";
            mockFluentRestClient(new ResponseEntity<>(jsonErrorResponse, HttpStatus.BAD_REQUEST));

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("ERR01").message("Invalid user state").build();
            when(objectMapper.readValue(jsonErrorResponse, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.disableUser(validUser, "test");

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);

            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending disable user request to Tech Services for: user-123",
                            "Failed to disable entra user: user-123");
        }

        @Test
        void shouldLogInfoOnHttpClientErrorExceptionTooEarly() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"TOO_EARLY\",\"message\":\"Request too early\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.TOO_EARLY, "Too Early", HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("TOO_EARLY").message("Request too early").build();
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.disableUser(validUser, "test");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);
            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending disable user request to Tech Services for: user-123",
                            "Failed to disable entra user: user-123, the root cause is Request too early (TOO_EARLY)");
        }

        @Test
        void shouldLogInfoOnHttpClientErrorExceptionBadRequest() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"ERROR\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Error",
                    HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);

            TechServicesErrorResponse expectedError = TechServicesErrorResponse.builder()
                    .success(false).code("BAD_REQUEST").message("Bad Request").build();
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenReturn(expectedError);

            TechServicesApiResponse<ChangeAccountEnabledResponse> result = client.disableUser(validUser, "test");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(expectedError);
            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Sending disable user request to Tech Services for: user-123",
                            "Failed to disable user user-123, the root cause is Bad Request (BAD_REQUEST)");
        }

        @Test
        void shouldLogInfoOnHttpClientErrorExceptionParsingError() throws Exception {
            mockTokenGeneration();
            String jsonError = "{\"code\":\"ERROR\"}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Error",
                    HttpHeaders.EMPTY, jsonError.getBytes(), null);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenThrow(ex);
            when(objectMapper.readValue(jsonError, TechServicesErrorResponse.class)).thenThrow(new RuntimeException("Error Parsing"));

            assertThatThrownBy(() -> client.disableUser(validUser, "test")).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error while sending disable user request to Tech Services.");
        }

        @Test
        void shouldThrowRuntimeExceptionWhenUserIsNull() {
            assertThatThrownBy(() -> client.disableUser(null, "Reason"))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("Error while sending disable user request to Tech Services.");
        }
    }

    @Nested
    class TokenCachingAndResilienceTests {

        @Test
        void shouldReturnCachedTokenIfValidAndNotExpired() {
            when(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).thenReturn(cache);
            when(cache.get(LiveTechServicesClient.ACCESS_TOKEN, String.class)).thenReturn("cached-token");

            Jwt mockJwt = mock(Jwt.class);
            when(mockJwt.getExpiresAt()).thenReturn(Instant.now().plus(60, ChronoUnit.SECONDS));
            when(jwtDecoder.decode("cached-token")).thenReturn(mockJwt);

            ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);

            when(restClient.patch()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer cached-token"))).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any(ChangeAccountEnabledRequest.class))).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(String.class)).thenReturn(responseEntity);

            client.enableUser(validUser);

            verifyNoInteractions(clientSecretCredential);
        }

        @Test
        void shouldGenerateNewTokenIfCachedTokenIsExpired() {
            when(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).thenReturn(cache);
            when(cache.get(LiveTechServicesClient.ACCESS_TOKEN, String.class)).thenReturn("expired-token");

            Jwt mockJwt = mock(Jwt.class);
            when(mockJwt.getExpiresAt()).thenReturn(Instant.now().plus(5, ChronoUnit.SECONDS));
            when(jwtDecoder.decode("expired-token")).thenReturn(mockJwt);

            AccessToken newAccessToken = mock(AccessToken.class);
            when(newAccessToken.getToken()).thenReturn(mockToken);
            when(clientSecretCredential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(newAccessToken));

            ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
            mockFluentRestClient(responseEntity);

            client.enableUser(validUser);

            verify(clientSecretCredential, times(1)).getToken(any(TokenRequestContext.class));
            verify(cache).put(LiveTechServicesClient.ACCESS_TOKEN, mockToken);
        }

        @Test
        void shouldLogInfoOnCacheExceptionHandling() {
            when(cacheManager.getCache(CachingConfig.TECH_SERVICES_DETAILS_CACHE)).thenReturn(cache);
            when(cache.get(LiveTechServicesClient.ACCESS_TOKEN, String.class)).thenThrow(new RuntimeException("Cache layer offline"));

            AccessToken accessToken = mock(AccessToken.class);
            when(accessToken.getToken()).thenReturn(mockToken);
            when(clientSecretCredential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(accessToken));

            ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
            mockFluentRestClient(responseEntity);

            client.enableUser(validUser);

            assertThat(listAppender.list).extracting(ILoggingEvent::getFormattedMessage).contains("Error while getting access token from cache");
        }
    }
}
