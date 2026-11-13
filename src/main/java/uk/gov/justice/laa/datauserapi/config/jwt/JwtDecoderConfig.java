package uk.gov.justice.laa.datauserapi.config.jwt;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;
import uk.gov.justice.laa.datauserapi.exception.JwtConfigurationException;

/**
 * JWT Decoder configuration for production environments.
 * Validates token signature via JWKS, issuer, audience, and timestamp.
 * Actor identity is read from the validated JWT {@code oid} claim by callers.
 */
@Slf4j
@Configuration
@Profile("prod")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        log.debug("Configuring JWT decoder",
                kv("issuerUri", properties.issuerUri()),
                kv("jwkSetUri", properties.jwkSetUri()),
                kv("audience", properties.audience()));
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(properties.audience());
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.issuerUri());

        NimbusJwtDecoder jwtDecoder;
        try {
            jwtDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        } catch (IllegalArgumentException ex) {
            log.error("JWT decoder creation failed",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex), ex);
            throw new JwtConfigurationException("JWT Decoder configuration failed", ex);
        }
        jwtDecoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator, timestampValidator));
        return jwtDecoder;
    }
}
