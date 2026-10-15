package uk.gov.justice.laa.datauserapi.config.jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT decoder for development, local, and test environments.
 * Parses OID and scope from a synthetic bearer value or decodes claims directly
 * from an unverified JWT (e.g. from laa-landing-page OBO/access tokens).
 * Does not validate cryptographic signatures and must not be used in production.
 */
@Slf4j
@Configuration
@Profile({ "dev", "local", "test" })
public class DevJwtDecoderConfig {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("oid__(?<oid>[^_]+)(?:_scope__(?<scope>.+))?");

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            if (token == null || token.isBlank()) {
                throw new JwtException("Dev JWT decoder failed - Malformed or missing token");
            }

            try {
                // 1. Check for synthetic token format: oid__<oid>_scope__<scope>
                Matcher matcher = TOKEN_PATTERN.matcher(token);
                if (matcher.matches()) {
                    String oid = matcher.group("oid");
                    String scope = Optional.ofNullable(matcher.group("scope"))
                            .filter(s -> !s.isBlank())
                            .orElse("");

                    return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", "dev-user")
                        .claim("oid", oid)
                        .claim("idtyp", "user")
                        .claim("scp", scope)
                        .claim("roles", "USER")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
                }

                // 2. Fall back to parsing claims from a standard JWT without verifying signature
                if (token.startsWith("ey")) {
                    return decodeJwtClaimsWithoutValidation(token);
                }

                throw new JwtException("Malformed token: does not match synthetic pattern or JWT format");
            } catch (JwtException e) {
                throw e;
            } catch (Exception e) {
                throw new JwtException("Dev JWT decoder failed - Malformed or missing token", e);
            }
        };
    }

    private Jwt decodeJwtClaimsWithoutValidation(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();

            final Map<String, Object> headers = new LinkedHashMap<>(signedJwt.getHeader().toJSONObject());
            Map<String, Object> claims = new LinkedHashMap<>(claimsSet.getClaims());

            // In local dev, add the read scope for upstream tokens that only carry Microsoft Graph scopes.
            Object scpClaim = claims.get("scp");
            Object scopeClaim = claims.get("scope");
            String existingScopes = "";
            if (scpClaim != null) {
                existingScopes = scpClaim.toString();
            } else if (scopeClaim != null) {
                existingScopes = scopeClaim.toString();
            }

            Set<String> scopes = new LinkedHashSet<>(Arrays.asList(existingScopes.split("\\s+")));
            scopes.removeIf(String::isBlank);
            scopes.add("user_data.read");

            claims.put("scp", String.join(" ", scopes));

            Instant issuedAt = claimsSet.getIssueTime() != null
                    ? claimsSet.getIssueTime().toInstant()
                    : Instant.now();
            Instant expiresAt = claimsSet.getExpirationTime() != null
                    ? claimsSet.getExpirationTime().toInstant()
                    : Instant.now().plusSeconds(3600);

            return new Jwt(token, issuedAt, expiresAt, headers, Collections.unmodifiableMap(claims));
        } catch (Exception e) {
            log.warn("Failed to parse JWT payload in dev decoder: {}", e.getMessage());
            throw new JwtException("Dev JWT decoder failed - unable to parse token payload", e);
        }
    }
}