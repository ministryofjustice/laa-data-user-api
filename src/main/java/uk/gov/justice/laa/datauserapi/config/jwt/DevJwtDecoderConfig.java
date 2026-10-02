package uk.gov.justice.laa.datauserapi.config.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT Decoder for development, local, and test environments.
 * Accepts any Bearer token and synthesises a minimal Jwt object — NOT for production.
 * Mirrors DevJwtDecoderConfig in laa-landing-page.
 */
@Configuration
@Profile({"dev", "local", "test"})
public class DevJwtDecoderConfig {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("oid__(?<oid>[^_]+)(?:_scope__(?<scope>.+))?");

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {

                Matcher matcher = TOKEN_PATTERN.matcher(token);

                if (!matcher.matches()) {
                    throw new JwtException("Malformed token");
                }

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
            } catch (Exception e) {
                throw new RuntimeException("Dev JWT decoder failed", e);
            }
        };
    }
}
