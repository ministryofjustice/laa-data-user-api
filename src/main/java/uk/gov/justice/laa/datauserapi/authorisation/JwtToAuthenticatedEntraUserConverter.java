package uk.gov.justice.laa.datauserapi.authorisation;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuthenticatedEntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedApp;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedOffice;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtToAuthenticatedEntraUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthenticatedEntraUserQueryRepository entraUserQueryRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userEntraOid = jwt.getClaimAsString("oid");
        AuthenticatedEntraUser user = entraUserQueryRepository.findByEntraOid(userEntraOid)
                .map(JwtToAuthenticatedEntraUserConverter::mapEntraUserToAuthenticatedEntraUser)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String scope = jwt.getClaimAsString("scp");
        if (scope != null) {
            authorities = Arrays.stream(scope.split(" "))
                    .map(scp -> new SimpleGrantedAuthority("SCOPE_" + scp))
                    .toList();
        }
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }

    private static AuthenticatedEntraUser mapEntraUserToAuthenticatedEntraUser(EntraUser entraUser) {
        Set<AuthenticatedUserProfile> userProfiles = entraUser.getUserProfiles().stream()
                .map(JwtToAuthenticatedEntraUserConverter::mapUserProfileToAuthenticatedUserProfile)
                .collect(Collectors.toSet());
        AuthenticatedUserProfile activeProfile = userProfiles.stream()
                .filter(AuthenticatedUserProfile::activeProfile)
                .findFirst()
                .orElse(null);

        return new AuthenticatedEntraUser(
                entraUser.getId(),
                entraUser.getEntraOid(),
                entraUser.getFirstName(),
                entraUser.getLastName(),
                entraUser.getEmail(),
                entraUser.getUserAccountStatus(),
                entraUser.isMultiFirmUser(),
                entraUser.getLastSyncedOn(),
                entraUser.isMailOnly(),
                entraUser.isActive(),
                entraUser.getDeactivatedBy(),
                entraUser.isCcmsEbsUser(),
                entraUser.getDeactivationType(),
                entraUser.getInvitationStatus(),
                userProfiles,
                activeProfile
        );
    }

    private static AuthenticatedUserProfile mapUserProfileToAuthenticatedUserProfile(UserProfile userProfile) {
        Set<AuthenticatedOffice> offices = userProfile.getOffices().stream()
                .map(JwtToAuthenticatedEntraUserConverter::mapOfficeToAuthenticatedOffice)
                .collect(Collectors.toSet());
        Set<AuthenticatedAppRole> appRoles = userProfile.getAppRoles().stream()
                .map(JwtToAuthenticatedEntraUserConverter::mapAppRoleToAuthenticatedAppRole)
                .collect(Collectors.toSet());

        Optional<Firm> firm = Optional.ofNullable(userProfile.getFirm());
        UUID firmId = firm.map(Firm::getId).orElse(null);
        String firmName = firm.map(Firm::getName).orElse(null);
        String firmCode = firm.map(Firm::getCode).orElse(null);

        return new AuthenticatedUserProfile(
                userProfile.getId(),
                userProfile.isActiveProfile(),
                userProfile.getUserType(),
                userProfile.getLegacyUserId(),
                firmId,
                firmName,
                firmCode,
                userProfile.getUserProfileStatus(),
                userProfile.getSilasStatus(),
                userProfile.isLastCcmsSyncSuccessful(),
                userProfile.isUnrestrictedOfficeAccess(),
                offices,
                appRoles
        );
    }

    private static AuthenticatedAppRole mapAppRoleToAuthenticatedAppRole(AppRole appRole) {
        AuthenticatedApp app = Optional.ofNullable(appRole.getApp())
                .map(JwtToAuthenticatedEntraUserConverter::mapAppToAuthenticatedApp)
                .orElse(null);

        return new AuthenticatedAppRole(
                appRole.getId(),
                appRole.getName(),
                appRole.getOrdinal(),
                appRole.getCcmsCode(),
                appRole.isLegacySync(),
                appRole.getFirmTypeRestriction(),
                appRole.getUserTypeRestriction(),
                appRole.getDescription(),
                appRole.isAuthzRole(),
                app,
                appRole.getPermissions()
        );
    }

    private static AuthenticatedOffice mapOfficeToAuthenticatedOffice(Office office) {
        Optional<Office.Address> address = Optional.ofNullable(office.getAddress());
        String addressLine1 = address.map(Office.Address::getAddressLine1).orElse(null);
        String addressLine2 = address.map(Office.Address::getAddressLine2).orElse(null);
        String addressLine3 = address.map(Office.Address::getAddressLine3).orElse(null);
        String city = address.map(Office.Address::getCity).orElse(null);
        String postcode = address.map(Office.Address::getPostcode).orElse(null);

        return new AuthenticatedOffice(
                office.getId(),
                office.getCode(),
                addressLine1,
                addressLine2,
                addressLine3,
                city,
                postcode
        );
    }

    private static AuthenticatedApp mapAppToAuthenticatedApp(App app) {
        return new AuthenticatedApp(
                app.getId(),
                app.getName(),
                app.getDescription(),
                app.getAppType(),
                app.getOrdinal(),
                app.getUrl(),
                app.isEnabled(),
                app.getEntraAppId(),
                app.getEntraOid(),
                app.getSecurityGroupOid()
        );
    }

}
