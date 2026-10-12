package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserView;

import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;


import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class UserViewMapper {

    public UserView mapToUserView(EntraUserDto user) {
        UserProfile activeProfile = user.getUserProfiles().stream()
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException(
                        "No active profile found for user: " + user.getEntraOid()));

        UserAccountSummaryView userAccount = new UserAccountSummaryView(
                user.getEntraOid(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserAccountStatus(),
                user.isMultiFirmUser(),
                user.isMailOnly(),
                user.getLastSyncedOn(),
                user.getCreatedDate(),
                user.getLastModified(),
                user.getUserProfiles().size()
        );

        UserProfileDetailView profile = mapProfile(activeProfile);

        return new UserView(userAccount, profile);
    }

    private UserProfileDetailView mapProfile(UserProfile profile) {

        EntraUser user = profile.getEntraUser();

        List<AppRoleView> roles = profile.getAppRoles()
                .stream()
                .map(this::mapAppRole)
                .toList();

        List<OfficeView> offices = profile.getOffices()
                .stream()
                .map(this::mapOffice)
                .toList();

        return new UserProfileDetailView(
                profile.getId(),
                profile.isActiveProfile(),
                profile.getUserType(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                profile.getFirm().getName(),
                profile.getFirm().getId().toString(),
                user.isMultiFirmUser(),
                user.getUserAccountStatus(),
                profile.getUserProfileStatus(),
                !roles.isEmpty(),

                UUID.fromString(user.getEntraOid()),
                profile.isUnrestrictedOfficeAccess(),
                profile.isLastCcmsSyncSuccessful(),
                roles,
                offices
        );
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
                String.valueOf(mapUserTypeRestriction(role.getUserTypeRestriction())),
                role.getFirmTypeRestriction() == null
                        ? List.of()
                        : List.of(Arrays.toString(role.getFirmTypeRestriction()))
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

    private AppRoleUserType mapUserTypeRestriction(UserType[] restrictions) {

        if (restrictions == null || restrictions.length == 0) {
            return AppRoleUserType.BOTH;
        }

        boolean hasInternal = Arrays.stream(restrictions).anyMatch(type -> type == UserType.INTERNAL);
        boolean hasExternal = Arrays.stream(restrictions).anyMatch(type -> type == UserType.EXTERNAL);

        if (hasInternal && hasExternal) {
            return AppRoleUserType.BOTH;
        }
        if (hasInternal) {
            return AppRoleUserType.INTERNAL;
        }
        return AppRoleUserType.EXTERNAL;
    }

    public UserAccountSummaryView mapToUserAccountSummaryView(
            EntraUserDto user) {

        return new UserAccountSummaryView(
                user.getEntraOid(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserAccountStatus(),
                user.isMultiFirmUser(),
                user.isMailOnly(),
                user.getLastSyncedOn(),
                user.getCreatedDate(),
                user.getLastModified(),
                user.getUserProfiles().size()
        );
    }
}
