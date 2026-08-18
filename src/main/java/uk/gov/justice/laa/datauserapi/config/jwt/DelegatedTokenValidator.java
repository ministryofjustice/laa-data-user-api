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
        String scp = jwt.getClaimAsString("scp");
        log.debug("JWT idtyp: {}, scp: {}", idtyp, scp);

        if ("user".equals(idtyp)) {
            log.debug("Delegated token validation successful (v2.0 idtyp=user)");
            return OAuth2TokenValidatorResult.success();
        }

        if ("app".equals(idtyp)) {
            log.debug("Delegated token validation failed — app-only token (idtyp=app)");
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token",
                    "Only delegated (user) tokens are accepted — app-only tokens are not permitted", null));
        }

        // v1.0 tokens do not include idtyp. In v1.0, user-delegated tokens carry scp;
        // app-only tokens carry roles instead. Accept when scp is present.
        if (scp != null && !scp.isBlank()) {
            log.debug("Delegated token validation successful (v1.0 token, scp present)");
            return OAuth2TokenValidatorResult.success();
        }

        log.debug("Delegated token validation failed — idtyp: {}, scp: {}", idtyp, scp);
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token",
                "Only delegated (user) tokens are accepted — app-only tokens are not permitted", null));
    }
}
