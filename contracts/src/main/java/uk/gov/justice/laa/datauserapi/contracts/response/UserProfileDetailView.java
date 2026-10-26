package uk.gov.justice.laa.datauserapi.contracts.dto;

package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;

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
        UserProfileSilasStatus profileStatus,
        boolean hasAppRoles,
        UUID userEntraObjectId,
        boolean unrestrictedOfficeAccess,
        boolean lastSyncSuccessful,
        List<AppRoleView> roles,
        List<OfficeView> offices
) {}
