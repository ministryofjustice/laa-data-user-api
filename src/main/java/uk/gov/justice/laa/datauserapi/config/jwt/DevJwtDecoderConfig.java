package uk.gov.justice.laa.datauserapi.config.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * JWT Decoder for development, local, and test environments.
 * Accepts any Bearer token and synthesises a minimal Jwt object — NOT for production.
 * Mirrors DevJwtDecoderConfig in laa-landing-page.
 */
@Configuration
@Profile({"dev", "local", "test"})
public class DevJwtDecoderConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "dev-user")
                    .claim("oid", "00000000-0000-0000-0000-000000000000")
                    .claim("idtyp", "user")
                    .claim("scp", "user_data.read")
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
