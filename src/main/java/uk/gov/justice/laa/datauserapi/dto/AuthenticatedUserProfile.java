package uk.gov.justice.laa.datauserapi.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserProfile(

    UUID id,
    boolean activeProfile,
    UserType userType,
    UUID legacyUserId,
    UUID firmId,
    String firmName,
    String firmCode,
    UserProfileStatus userProfileStatus,
    UserProfileSilasStatus silasStatus,
    boolean lastCcmsSyncSuccessful,
    boolean unrestrictedOfficeAccess,
    Set<AuthenticatedOffice> offices,
    Set<AuthenticatedAppRole> appRoles


){}
