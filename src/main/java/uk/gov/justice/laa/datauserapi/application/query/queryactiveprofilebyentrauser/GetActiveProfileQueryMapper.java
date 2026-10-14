package uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class GetActiveProfileQueryMapper {

    public UserProfileDetailView mapProfileToDetailView(UserProfile activeProfile) {
        EntraUser user = activeProfile.getEntraUser();

        List<AppRoleView> roles = activeProfile.getAppRoles().stream()
                .map(this::mapAppRole)
                .toList();

        List<OfficeView> offices = activeProfile.getOffices().stream()
                .map(this::mapOffice)
                .toList();

        return new UserProfileDetailView(
                activeProfile.getId(),
                activeProfile.isActiveProfile(),
                activeProfile.getUserType(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                activeProfile.getFirm() != null ? activeProfile.getFirm().getName() : "",
                activeProfile.getFirm() != null ? activeProfile.getFirm().getId().toString() : "",
                user.isMultiFirmUser(),
                user.getUserAccountStatus(),
                activeProfile.getUserProfileStatus(),
                !roles.isEmpty(),
                UUID.fromString(user.getEntraOid()),
                activeProfile.isUnrestrictedOfficeAccess(),
                activeProfile.isLastCcmsSyncSuccessful(),
                roles,
                offices
        );
    }

    public List<UserProfileSummaryView> mapToProfileSummaryList(EntraUser user) {
        return user.getUserProfiles().stream()
                .map(profile -> new UserProfileSummaryView(
                        profile.getId(),
                        profile.isActiveProfile(),
                        profile.getUserType(),
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        profile.getFirm() != null ? profile.getFirm().getName() : "",
                        profile.getFirm() != null ? profile.getFirm().getId().toString() : "",
                        user.isMultiFirmUser(),
                        user.getUserAccountStatus(),
                        profile.getUserProfileStatus(),
                        !profile.getAppRoles().isEmpty()
                ))
                .toList();
    }

    private AppRoleView mapAppRole(AppRole role) {
        return new AppRoleView(
                role.getId(),
                role.getApp().getId(),
                role.getApp().getName(),
                role.getName(),
                role.getDescription(),
                role.isAuthzRole(),
                role.getCcmsCode(),
                role.isLegacySync(),
                role.getOrdinal(),
                role.getUserTypeRestriction() != null ? role.getUserTypeRestriction().toString() : "BOTH",
                role.getFirmTypeRestriction() != null ? List.of(Arrays.toString(role.getFirmTypeRestriction())) : List.of()
        );
    }

    private OfficeView mapOffice(Office office) {
        return new OfficeView(
                office.getId().toString(),
                office.getFirm().getId().toString(),
                office.getAddress().getPostcode(),
                office.getAddress().getAddressLine1(),
                office.getAddress().getAddressLine2(),
                office.getAddress().getAddressLine3(),
                office.getAddress().getCity()
        );
    }
}
