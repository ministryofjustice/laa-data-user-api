package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class GetActiveProfileQueryMapper {

    private final ModelMapper modelMapper;

    public GetActiveProfileQueryMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public UserProfileDetailView mapProfileToDetailView(UserProfile activeProfile) {

        EntraUserDto user = modelMapper.map(activeProfile.getEntraUser(), EntraUserDto.class);

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
                activeProfile.getSilasStatus(),
                !roles.isEmpty(),
                UUID.fromString(user.getEntraOid()),
                activeProfile.isUnrestrictedOfficeAccess(),
                activeProfile.isLastCcmsSyncSuccessful(),
                roles,
                offices
        );
    }

    private AppRoleView mapAppRole(AppRole role) {
        return new AppRoleView(
                role.getId().toString(),
                role.getApp().getId().toString(),
                role.getApp().getName(),
                role.getApp().getEntraAppId(),
                role.getName(),
                role.getDescription(),
                role.isAuthzRole(),
                role.getRoleIdentifier(),
                role.isLegacySync(),
                role.getOrdinal(),
                role.getUserTypeRestriction() == null || Arrays.stream(role.getUserTypeRestriction()).distinct().count() != 1
                        ? "BOTH" : role.getUserTypeRestriction()[0].name(),
                role.getFirmTypeRestriction() != null
                        ? Arrays.stream(role.getFirmTypeRestriction()).map(Enum::name).toList() : List.of()
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
