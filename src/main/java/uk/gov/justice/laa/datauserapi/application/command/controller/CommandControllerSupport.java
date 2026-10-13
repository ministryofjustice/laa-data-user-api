package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

import java.util.UUID;

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
            assert oid.length() == 36;
            // Validate that the oid is a valid UUID
            UUID.fromString(oid);
        } catch (AssertionError | IllegalArgumentException ex) {
            throw new InvalidActorContextException("Authentication token contains an invalid actor identity", ex);
        }

        return oid;
    }

}
