package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

import java.util.UUID;

public interface CommandControllerSupport {

    default String resolveActor(Jwt jwt) {
        if (jwt == null) {
            throw InvalidActorContextException.missingActorIdentity();
        }

        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            throw InvalidActorContextException.missingActorIdentity();
        }

        if (oid.length() != 36) {
            throw InvalidActorContextException.invalidActorIdentity(
                    new IllegalArgumentException("Actor identity must use canonical UUID format"));
        }
        try {
            UUID.fromString(oid);
        } catch (IllegalArgumentException ex) {
            throw InvalidActorContextException.invalidActorIdentity(ex);
        }

        return oid;
    }

}
