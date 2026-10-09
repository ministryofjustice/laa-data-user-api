package uk.gov.justice.laa.datauserapi.contracts.dto;


import java.util.List;
import java.util.UUID;

public record UserProfileDetailView(
        UUID userProfileId,
        boolean activeProfile,
        String userType,
        String email,
        String fullName,
        String firmName,
        String firmId,
        boolean multiFirmUser,
        String accountStatus,
        String profileStatus,
        boolean hasAppRoles,
        UUID userEntraObjectId,
        boolean unrestrictedOfficeAccess,
        boolean lastSyncSuccessful,
        List<AppRoleView> roles,
        List<OfficeView> offices
) {}
