package uk.gov.justice.laa.datauserapi.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedAppRole(

        UUID id,
        String name,
        int ordinal,
        String ccmsCode,
        boolean legacySync,
        FirmType[] firmTypeRestriction,
        UserType[] userTypeRestriction,
        String description,
        boolean authzRole,
        AuthenticatedApp app,
        Set<Permission> permissions
) {
}
