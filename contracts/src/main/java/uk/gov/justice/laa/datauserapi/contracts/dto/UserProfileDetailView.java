package uk.gov.justice.laa.datauserapi.contracts.dto;


import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.List;
import java.util.UUID;

public record UserProfileDetailView(
        UUID userProfileId,
        boolean activeProfile,
        UserType userType,
        String email,
        String fullName,
        String firmName,
        String firmId,
        boolean multiFirmUser,
        UserAccountStatus accountStatus,
        UserProfileStatus profileStatus,
        boolean hasAppRoles,
        UUID userEntraObjectId,
        boolean unrestrictedOfficeAccess,
        boolean lastSyncSuccessful,
        List<AppRoleView> roles,
        List<OfficeView> offices
) {}
