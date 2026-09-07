package uk.gov.justice.laa.datauserapi.config.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JWT Decoder configuration for production environments.
 * Validates token signature via JWKS, issuer, audience, and timestamp.
 * Actor identity is read from the validated JWT {@code oid} claim by callers.
 */
@Slf4j
@Configuration
@Profile("prod")
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.audience}")
    private String audience;

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        log.debug("JWT Decoder — issuer: {}, jwkSetUri: {}, audience: {}", issuerUri, jwkSetUri, audience);
        try {
            OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
            OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);

            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator, timestampValidator));
            return jwtDecoder;
        } catch (Exception e) {
            log.error("Failed to create JWT Decoder: {}", e.getMessage(), e);
            throw new RuntimeException("JWT Decoder configuration failed", e);
        }
    }
}
