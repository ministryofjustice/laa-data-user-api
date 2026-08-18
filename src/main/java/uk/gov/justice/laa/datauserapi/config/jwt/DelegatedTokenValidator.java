package uk.gov.justice.laa.datauserapi.config.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that the incoming JWT is a delegated (user) token
 * Checks the {@code idtyp} claim set by Microsoft Entra ID:
 * {@code "user"} = delegated/OBO token, {@code "app"} = client credentials token.
 */
@Slf4j
public class DelegatedTokenValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String idtyp = jwt.getClaimAsString("idtyp");
        log.debug("JWT idtyp claim: {}", idtyp);

        if ("user".equals(idtyp)) {
            log.debug("Delegated token validation successful");
            return OAuth2TokenValidatorResult.success();
        }

        log.debug("Delegated token validation failed — idtyp: {}", idtyp);
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token",
                "Only delegated (user) tokens are accepted — app-only tokens are not permitted", null));
    }
}
