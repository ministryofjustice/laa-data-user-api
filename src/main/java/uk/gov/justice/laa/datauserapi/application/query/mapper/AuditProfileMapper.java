package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.dto.AppDto;
import uk.gov.justice.laa.datauserapi.dto.AppRoleDto;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.OfficeDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps only what the audit user detail renders. It deliberately avoids mapping a profile's owning account, which
 * would walk back to every other profile of that account.
 */
@Component
public class AuditProfileMapper {

    public EntraUserDto toAccountDto(AuditAccountRow account) {
        return EntraUserDto.builder()
                .entraOid(account.entraOid())
                .email(account.email())
                .firstName(account.firstName())
                .lastName(account.lastName())
                .userAccountStatus(account.userAccountStatus())
                .multiFirmUser(account.multiFirmUser())
                .mailOnly(account.mailOnly())
                .lastSyncedOn(account.lastSyncedOn())
                .createdDate(account.createdDate())
                .lastModified(account.lastModified())
                .invitationStatus(account.invitationStatus())
                .userProfiles(Set.of())
                .build();
    }

    /** Maps the loaded profiles in the order of {@code orderedIds}; the details query itself returns no set order. */
    public List<UserProfileDto> toOrderedProfileDtos(List<UserProfile> profiles, List<UUID> orderedIds) {
        Map<UUID, UserProfile> byId = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).map(this::toProfileDto).toList();
    }

    private UserProfileDto toProfileDto(UserProfile profile) {
        return UserProfileDto.builder()
                .id(profile.getId())
                .activeProfile(profile.isActiveProfile())
                .userType(profile.getUserType())
                .userProfileStatus(profile.getUserProfileStatus())
                .silasStatus(profile.getSilasStatus())
                .unrestrictedOfficeAccess(profile.isUnrestrictedOfficeAccess())
                .lastCcmsSyncSuccessful(profile.isLastCcmsSyncSuccessful())
                .firm(profile.getFirm() == null ? null
                        : FirmDto.builder().id(profile.getFirm().getId()).name(profile.getFirm().getName()).build())
                .appRoles(profile.getAppRoles() == null ? List.of()
                        : profile.getAppRoles().stream().map(this::toRoleDto).toList())
                .offices(profile.getOffices() == null ? List.of()
                        : profile.getOffices().stream().map(this::toOfficeDto).toList())
                .build();
    }

    private AppRoleDto toRoleDto(AppRole role) {
        return AppRoleDto.builder()
                .id(role.getId().toString())
                .name(role.getName())
                .ordinal(role.getOrdinal())
                .description(role.getDescription())
                .roleIdentifier(role.getRoleIdentifier())
                .legacySync(role.isLegacySync())
                .authzRole(role.isAuthzRole())
                .userTypeRestriction(role.getUserTypeRestriction())
                .firmTypeRestriction(role.getFirmTypeRestriction())
                .app(AppDto.builder()
                        .id(role.getApp().getId().toString())
                        .name(role.getApp().getName())
                        .entraAppId(role.getApp().getEntraAppId())
                        .build())
                .build();
    }

    private OfficeDto toOfficeDto(Office office) {
        Office.Address address = office.getAddress();
        return OfficeDto.builder()
                .id(office.getId())
                .code(office.getCode())
                .firmId(office.getFirm() == null ? null : office.getFirm().getId().toString())
                .address(OfficeDto.AddressDto.builder()
                        .addressLine1(address == null ? null : address.getAddressLine1())
                        .addressLine2(address == null ? null : address.getAddressLine2())
                        .addressLine3(address == null ? null : address.getAddressLine3())
                        .city(address == null ? null : address.getCity())
                        .postcode(address == null ? null : address.getPostcode())
                        .build())
                .build();
    }
}
