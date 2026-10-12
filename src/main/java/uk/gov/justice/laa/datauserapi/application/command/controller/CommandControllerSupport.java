package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

public interface CommandControllerSupport {

    default String resolveActor(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidActorContextException("Authentication token is missing the actor identity");
        }

        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            throw new InvalidActorContextException("Authentication token is missing the actor identity");
        }

        try {
            return oid;
        } catch (IllegalArgumentException ex) {
            throw new InvalidActorContextException("Authentication token contains an invalid actor identity", ex);
        }
    }

}
