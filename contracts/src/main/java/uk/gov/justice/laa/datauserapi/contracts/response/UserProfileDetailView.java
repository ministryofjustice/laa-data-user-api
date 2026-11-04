package uk.gov.justice.laa.datauserapi.contracts.response;

import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;

import java.util.List;
import java.util.UUID;

@Builder
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
