package uk.gov.justice.laa.datauserapi.client.ts;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.AzureException;
import com.azure.identity.ClientSecretCredential;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTokenException;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

@Slf4j
public class AzureTechServicesTokenProvider implements AccessTokenProvider {
    public static final String ACCESS_TOKEN = "access_token";
    private static final String TOKEN_CACHE_NAME = "tech_services_details_cache";
    private static final long EXPIRY_SKEW_SECONDS = 30;
    private static final long ACQUISITION_TIMEOUT_SECONDS = 60;

    private final ClientSecretCredential credential;
    private final CacheManager cacheManager;
    private final JwtDecoder jwtDecoder;
    private final String scope;
    private final Clock clock;

    public AzureTechServicesTokenProvider(
            ClientSecretCredential credential,
            CacheManager cacheManager,
            JwtDecoder jwtDecoder,
            String scope,
            Clock clock) {
        this.credential = credential;
        this.cacheManager = cacheManager;
        this.jwtDecoder = jwtDecoder;
        this.scope = scope;
        this.clock = clock;
    }

    @Override
    public String getAccessToken() {
        Cache cache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (cache == null) {
            log.warn("Tech Services token cache is unavailable; acquiring an uncached token",
                    kv("cacheName", TOKEN_CACHE_NAME));
        }
        String cachedToken = cache == null ? null : cache.get(ACCESS_TOKEN, String.class);
        if (cachedToken != null && isUsable(cachedToken)) {
            return cachedToken;
        }

        String token = acquireToken();
        if (cache != null) {
            cache.put(ACCESS_TOKEN, token);
        }
        return token;
    }

    private boolean isUsable(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            log.info("Invalid Tech Services access token found in cache; acquiring a replacement",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("cacheName", TOKEN_CACHE_NAME), ex);
            return false;
        }
        return jwt.getExpiresAt() != null
                && jwt.getExpiresAt().isAfter(clock.instant().plusSeconds(EXPIRY_SKEW_SECONDS));
    }

    private String acquireToken() {
        CompletableFuture<AccessToken> pendingToken = requestToken();
        AccessToken accessToken;
        try {
            accessToken = pendingToken.get(ACQUISITION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            pendingToken.cancel(true);
            Thread.currentThread().interrupt();
            throw new TechServicesTokenException("Interrupted while acquiring a Tech Services access token.", ex);
        } catch (ExecutionException ex) {
            throw new TechServicesTokenException("Unable to acquire a Tech Services access token from Azure.", ex.getCause());
        } catch (TimeoutException ex) {
            pendingToken.cancel(true);
            throw new TechServicesTokenException("Timed out acquiring a Tech Services access token from Azure.", ex);
        }
        if (accessToken == null || accessToken.getToken() == null || accessToken.getToken().isBlank()) {
            throw new TechServicesTokenException("Azure returned an empty Tech Services access token.");
        }
        return accessToken.getToken();
    }

    private CompletableFuture<AccessToken> requestToken() {
        TokenRequestContext context = new TokenRequestContext().setScopes(List.of(scope));
        try {
            return credential.getToken(context).toFuture();
        } catch (AzureException ex) {
            throw new TechServicesTokenException("Unable to acquire a Tech Services access token from Azure.", ex);
        }
    }
}
