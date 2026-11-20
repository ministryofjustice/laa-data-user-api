package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;

import java.time.LocalDateTime;

/** Account-level facts of a user, selected without loading the user's profiles. */
public record AuditAccountRow(
        String entraOid,
        String email,
        String firstName,
        String lastName,
        UserAccountStatus userAccountStatus,
        boolean multiFirmUser,
        boolean mailOnly,
        LocalDateTime lastSyncedOn,
        LocalDateTime createdDate,
        LocalDateTime lastModified,
        InvitationStatus invitationStatus
) {}
