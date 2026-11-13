package uk.gov.justice.laa.datauserapi.config.jwt;

import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

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

            Matcher matcher = TOKEN_PATTERN.matcher(token);
            if (matcher.matches()) {
                String oid = matcher.group("oid");
                String scope = Optional.ofNullable(matcher.group("scope"))
                        .filter(s -> !s.isBlank())
                        .orElse("");
                Instant now = Instant.now();
                return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "dev-user")
                    .claim("oid", oid)
                    .claim("idtyp", "user")
                    .claim("scp", scope)
                    .claim("roles", "USER")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
            }

            if (token.startsWith("ey")) {
                return decodeJwtClaimsWithoutValidation(token);
            }

            throw new JwtException("Malformed token: does not match synthetic pattern or JWT format");
        };
    }

    private Jwt decodeJwtClaimsWithoutValidation(String token) {
        SignedJWT signedJwt = parseSignedJwt(token);
        JWTClaimsSet claimsSet = getClaimsSet(signedJwt);

        final Map<String, Object> headers = new LinkedHashMap<>(signedJwt.getHeader().toJSONObject());
        Map<String, Object> claims = new LinkedHashMap<>(claimsSet.getClaims());

        Instant issuedAt = claimsSet.getIssueTime() != null
                ? claimsSet.getIssueTime().toInstant()
                : Instant.now();
        Instant expiresAt = claimsSet.getExpirationTime() != null
                ? claimsSet.getExpirationTime().toInstant()
                : Instant.now().plusSeconds(3600);

        try {
            return new Jwt(token, issuedAt, expiresAt, headers, Collections.unmodifiableMap(claims));
        } catch (IllegalArgumentException e) {
            throw payloadException(e);
        }
    }

    private SignedJWT parseSignedJwt(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throw payloadException(e);
        }
    }

    private JWTClaimsSet getClaimsSet(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw payloadException(e);
        }
    }

    private JwtException payloadException(Exception cause) {
        log.warn("Failed to parse JWT payload in development decoder",
                ExceptionLogFields.exceptionGroup(cause), ExceptionLogFields.exceptionReason(cause), cause);
        return new JwtException("Dev JWT decoder failed - unable to parse token payload", cause);
    }
}
