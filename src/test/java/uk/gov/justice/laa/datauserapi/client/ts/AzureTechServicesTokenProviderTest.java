package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.AzureException;
import com.azure.identity.ClientSecretCredential;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.core.publisher.Mono;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTokenException;

@ExtendWith(MockitoExtension.class)
class AzureTechServicesTokenProviderTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String CACHE_NAME = "tech_services_details_cache";
    @Mock
    private ClientSecretCredential credential;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private Mono<AccessToken> tokenRequest;
    @Mock
    private CompletableFuture<AccessToken> pendingToken;
    private AzureTechServicesTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AzureTechServicesTokenProvider(credential, cacheManager, jwtDecoder,
                "test-scope", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsCachedTokenWhenExpiryIsBeyondSkew() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(AzureTechServicesTokenProvider.ACCESS_TOKEN, String.class)).thenReturn("cached");
        Jwt jwt = mock(Jwt.class);
        when(jwt.getExpiresAt()).thenReturn(NOW.plusSeconds(31));
        when(jwtDecoder.decode("cached")).thenReturn(jwt);

        assertThat(provider.getAccessToken()).isEqualTo("cached");
        verifyNoInteractions(credential);
    }

    @Test
    void refreshesTokenWhenCachedTokenIsWithinExpirySkew() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(AzureTechServicesTokenProvider.ACCESS_TOKEN, String.class)).thenReturn("stale");
        Jwt jwt = mock(Jwt.class);
        when(jwt.getExpiresAt()).thenReturn(NOW.plusSeconds(30));
        when(jwtDecoder.decode("stale")).thenReturn(jwt);
        AccessToken freshToken = accessToken("fresh");
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(freshToken));

        assertThat(provider.getAccessToken()).isEqualTo("fresh");
        verify(cache).put(AzureTechServicesTokenProvider.ACCESS_TOKEN, "fresh");
    }

    @Test
    void invalidCachedJwtIsLoggedAndReplaced() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(AzureTechServicesTokenProvider.ACCESS_TOKEN, String.class)).thenReturn("invalid");
        when(jwtDecoder.decode("invalid")).thenThrow(new JwtException("invalid signature"));
        AccessToken freshToken = accessToken("fresh");
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(freshToken));

        assertThat(provider.getAccessToken()).isEqualTo("fresh");
    }

    @Test
    void missingCacheUsesUncachedTokenAcquisition() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);
        AccessToken uncachedToken = accessToken("uncached");
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(uncachedToken));

        assertThat(provider.getAccessToken()).isEqualTo("uncached");
    }

    @Test
    void cacheInfrastructureFailureIsNotSilentlyIgnored() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(AzureTechServicesTokenProvider.ACCESS_TOKEN, String.class))
                .thenThrow(new IllegalStateException("cache unavailable"));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache unavailable");
    }

    @Test
    void wrapsAzureAcquisitionFailureInTokenException() {
        AzureException azureFailure = new AzureException("credentials rejected");
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.error(azureFailure));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(TechServicesTokenException.class)
                .hasMessageContaining("acquire a Tech Services access token")
                .hasCause(azureFailure);
    }

    @Test
    void missingJwtExpiryForcesRefresh() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(AzureTechServicesTokenProvider.ACCESS_TOKEN, String.class)).thenReturn("no-expiry");
        Jwt jwt = mock(Jwt.class);
        when(jwt.getExpiresAt()).thenReturn(null);
        when(jwtDecoder.decode("no-expiry")).thenReturn(jwt);
        AccessToken freshToken = accessToken("fresh");
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(freshToken));

        assertThat(provider.getAccessToken()).isEqualTo("fresh");
    }

    @Test
    void cancelsAcquisitionWhenItTimesOut() throws Exception {
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(tokenRequest);
        when(tokenRequest.toFuture()).thenReturn(pendingToken);
        TimeoutException timeout = new TimeoutException("Azure token request timed out");
        when(pendingToken.get(60, TimeUnit.SECONDS)).thenThrow(timeout);

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(TechServicesTokenException.class)
                .hasCause(timeout);
        verify(pendingToken).cancel(true);
    }

    @Test
    void rejectsEmptyAzureTokenResponse() {
        when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.empty());

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(TechServicesTokenException.class)
                .hasMessageContaining("empty Tech Services access token");
    }

    @Test
    void wrapsSynchronousAzureFailure() {
        AzureException failure = new AzureException("Azure request rejected");
        when(credential.getToken(any(TokenRequestContext.class))).thenThrow(failure);

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(TechServicesTokenException.class)
                .hasCause(failure);
    }

    private AccessToken accessToken(String value) {
        AccessToken token = mock(AccessToken.class);
        when(token.getToken()).thenReturn(value);
        return token;
    }
}
