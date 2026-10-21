package uk.gov.justice.laa.datauserapi.dto;

import lombok.Builder;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.SilasAccountStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder
public record AuthenticatedEntraUser(

    UUID id,
    String entraOid,
    String firstName,
    String lastName,
    String email,
    SilasAccountStatus silasAccountStatus,
    boolean multiFirmUser,
    LocalDateTime lastSyncedOn,
    boolean mailOnly,
    boolean active,
    UUID deactivatedBy,
    boolean ccmsEbsUser,
    DeactivationType deactivationType,
    InvitationStatus invitationStatus,
    Set<AuthenticatedUserProfile> userProfiles,
    AuthenticatedUserProfile activeProfile

){}
