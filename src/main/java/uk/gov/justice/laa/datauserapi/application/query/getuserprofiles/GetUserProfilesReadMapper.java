package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.shared.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetUserProfilesReadMapper implements Mapper<Page<UserProfile>, PagedReadModel<GetUserProfilesReadModel>> {

    @Override
    public PagedReadModel<GetUserProfilesReadModel> map(Page<UserProfile> userProfilesPage) {
        List<GetUserProfilesReadModel> readModels = userProfilesPage.get()
                .map(this::mapUserProfileToGetUserProfilesReadModel)
                .toList();
        PageMetadata pageMetadata = new PageMetadata(userProfilesPage.getNumber(), userProfilesPage.getSize(), userProfilesPage.getTotalElements(), userProfilesPage.getTotalPages());
        return new PagedReadModel<>(readModels, pageMetadata);
    }


    private GetUserProfilesReadModel mapUserProfileToGetUserProfilesReadModel(UserProfile userProfile) {
        Optional<EntraUser> entraUser = Optional.ofNullable(userProfile.getEntraUser());
        String email = entraUser.map(EntraUser::getEmail).orElse(null);
        String firstName = entraUser.map(EntraUser::getFirstName).orElse(null);
        String lastName = entraUser.map(EntraUser::getLastName).orElse(null);
        String fullName = buildFullName(firstName, lastName);
        Optional<Firm> firm = Optional.ofNullable(userProfile.getFirm());
        String firmName = firm.map(Firm::getName).orElse(null);
        String firmId = firm.map(Firm::getId).map(UUID::toString).orElse(null);
        Boolean multiFirmUser = entraUser.map(EntraUser::isMultiFirmUser).orElse(null);
        String accountStatus = entraUser.map(EntraUser::getUserAccountStatus).map(UserAccountStatus::name).orElse(null);
        String profileStatus = Optional.ofNullable(userProfile.getUserProfileStatus()).map(UserProfileStatus::toString).orElse(null);
        Boolean hasAppRoles = userProfile.getAppRoles() != null && !userProfile.getAppRoles().isEmpty();
        return new GetUserProfilesReadModel(
                userProfile.getId().toString(),
                userProfile.isActiveProfile(),
                userProfile.getUserType().toString(),
                email,
                fullName,
                firmName,
                firmId,
                multiFirmUser,
                accountStatus,
                profileStatus,
                hasAppRoles
        );
    }

    private static String buildFullName(String firstName, String lastName) {
        if(firstName == null) {
            return null;
        }
        else if(lastName == null) {
            return firstName;
        }
        else {
            return firstName + " " + lastName;
        }
    }
}
