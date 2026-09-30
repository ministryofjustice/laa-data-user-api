package uk.gov.justice.laa.datauserapi.config.jwt;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * JWT decoder for development, local, and test environments.
 * Parses OID and scope from a synthetic bearer value; it does not validate
 * signatures and must not be used in production.
 * Mirrors DevJwtDecoderConfig in laa-landing-page.
 */
@Configuration
@Profile({ "dev", "local", "test" })
public class DevJwtDecoderConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                if (!token.contains("oid__")) {
                    throw new JwtException("Malformed token: missing 'oid__' prefix");
                }

                String oid;
                int oidStart = token.indexOf("oid__") + 5;
                int oidEnd = token.indexOf("_scope__");
                if (oidEnd == -1) {
                    oidEnd = token.length();
                }

                if (oidStart >= oidEnd) {
                    throw new JwtException("Malformed token: missing OID value");
                }

                oid = token.substring(oidStart, oidEnd).trim();

                if (oid.isEmpty()) {
                    throw new JwtException("Malformed token: empty OID value");
                }

                String scope = "user.read";
                if (token.contains("_scope__")) {
                    int scopeStart = token.indexOf("_scope__") + 8;
                    scope = token.substring(scopeStart).trim();
                    if (scope.isEmpty()) {
                        throw new JwtException("Malformed token: empty scope value");
                    }
                }

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
            } catch (JwtException e) {
                throw e;
            } catch (Exception e) {
                throw new JwtException("Malformed or missing token", e);
            }
        };
    }
}