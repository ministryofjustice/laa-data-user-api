package uk.gov.justice.laa.datauserapi.config.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Custom validator that ensures the incoming JWT token carries the expected audience.
 * Mirrors the AudienceValidator in laa-landing-page for consistent local-only validation.
 */
@Slf4j
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        log.debug("Expected audience: {}", audience);
        log.debug("JWT audiences: {}", jwt.getAudience());

        if (jwt.getAudience().contains(audience)) {
            log.debug("Audience validation successful");
            return OAuth2TokenValidatorResult.success();
        }

        log.debug("Audience validation failed - expected: {} | found: {}", audience, jwt.getAudience());
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token",
                "The required audience " + audience + " is missing", null));
    }
}
