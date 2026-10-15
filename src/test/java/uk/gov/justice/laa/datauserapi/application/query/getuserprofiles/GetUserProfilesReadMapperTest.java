package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.util.TestObjectUtil;

import java.util.List;
import java.util.Set;

public class GetUserProfilesReadMapperTest {

    private GetUserProfilesReadMapper readMapper;

    @BeforeEach
    public void setup() {
        readMapper = new GetUserProfilesReadMapper();
    }

    @Test
    public void testGetUserProfilesReadMapperCorrectlyMapsAllFields() {
        // Given
        Pageable pageable = PageRequest.of(5, 30);
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        Page<UserProfile> userProfilePage = new PageImpl<>(List.of(userProfile), pageable, 1000);

        // When
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = readMapper.map(userProfilePage);

        // Then
        GetUserProfilesReadModel readModel = pagedReadModel.items().stream()
                .findFirst()
                .orElseThrow();
        PageMetadata pageMetadata = pagedReadModel.page();

        // User Profile assertions
        Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId());
        Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile());
        Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType());
        Assertions.assertEquals(userProfile.getUserProfileStatus().name(), readModel.profileStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getUserAccountStatus().name(), readModel.accountStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getFirstName() + " " + userProfile.getEntraUser().getLastName(), readModel.fullName());

        // Page metadata assertions
        Assertions.assertEquals(userProfilePage.getNumber(), pageMetadata.pageNumber());
        Assertions.assertEquals(userProfilePage.getSize(), pageMetadata.pageSize());
        Assertions.assertEquals(userProfilePage.getTotalPages(), pageMetadata.totalPages());
        Assertions.assertEquals(userProfilePage.getTotalElements(), pageMetadata.totalElements());

    }

    @Test
    public void testGetUserProfilesReadMapperCorrectlyMapsAllFields_AppRolesNull() {
        // Given
        Pageable pageable = PageRequest.of(5, 30);
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setAppRoles(null);
        Page<UserProfile> userProfilePage = new PageImpl<>(List.of(userProfile), pageable, 1000);

        // When
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = readMapper.map(userProfilePage);

        // Then
        GetUserProfilesReadModel readModel = pagedReadModel.items().stream()
                .findFirst()
                .orElseThrow();
        PageMetadata pageMetadata = pagedReadModel.page();

        // User Profile assertions
        Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId());
        Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile());
        Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType());
        Assertions.assertEquals(userProfile.getUserProfileStatus().name(), readModel.profileStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getUserAccountStatus().name(), readModel.accountStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getFirstName() + " " + userProfile.getEntraUser().getLastName(), readModel.fullName());
        Assertions.assertFalse(readModel.hasAppRoles());

        // Page metadata assertions
        Assertions.assertEquals(userProfilePage.getNumber(), pageMetadata.pageNumber());
        Assertions.assertEquals(userProfilePage.getSize(), pageMetadata.pageSize());
        Assertions.assertEquals(userProfilePage.getTotalPages(), pageMetadata.totalPages());
        Assertions.assertEquals(userProfilePage.getTotalElements(), pageMetadata.totalElements());

    }

    @Test
    public void testGetUserProfilesReadMapperCorrectlyMapsAllFields_AppRolesEmpty() {
        // Given
        Pageable pageable = PageRequest.of(5, 30);
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setAppRoles(Set.of());
        Page<UserProfile> userProfilePage = new PageImpl<>(List.of(userProfile), pageable, 1000);

        // When
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = readMapper.map(userProfilePage);

        // Then
        GetUserProfilesReadModel readModel = pagedReadModel.items().stream()
                .findFirst()
                .orElseThrow();
        PageMetadata pageMetadata = pagedReadModel.page();

        // User Profile assertions
        Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId());
        Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile());
        Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType());
        Assertions.assertEquals(userProfile.getUserProfileStatus().name(), readModel.profileStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getUserAccountStatus().name(), readModel.accountStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getFirstName() + " " + userProfile.getEntraUser().getLastName(), readModel.fullName());
        Assertions.assertFalse(readModel.hasAppRoles());

        // Page metadata assertions
        Assertions.assertEquals(userProfilePage.getNumber(), pageMetadata.pageNumber());
        Assertions.assertEquals(userProfilePage.getSize(), pageMetadata.pageSize());
        Assertions.assertEquals(userProfilePage.getTotalPages(), pageMetadata.totalPages());
        Assertions.assertEquals(userProfilePage.getTotalElements(), pageMetadata.totalElements());

    }

    @Test
    public void testGetUserProfilesReadMapperCorrectlyMapsAllFields_FirmNull() {
        // Given
        Pageable pageable = PageRequest.of(5, 30);
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setFirm(null);
        Page<UserProfile> userProfilePage = new PageImpl<>(List.of(userProfile), pageable, 1000);

        // When
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = readMapper.map(userProfilePage);

        // Then
        GetUserProfilesReadModel readModel = pagedReadModel.items().stream()
                .findFirst()
                .orElseThrow();
        PageMetadata pageMetadata = pagedReadModel.page();

        // User Profile assertions
        Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId());
        Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile());
        Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType());
        Assertions.assertEquals(userProfile.getUserProfileStatus().name(), readModel.profileStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getUserAccountStatus().name(), readModel.accountStatus());
        Assertions.assertEquals(userProfile.getEntraUser().getFirstName() + " " + userProfile.getEntraUser().getLastName(), readModel.fullName());

        // Page metadata assertions
        Assertions.assertEquals(userProfilePage.getNumber(), pageMetadata.pageNumber());
        Assertions.assertEquals(userProfilePage.getSize(), pageMetadata.pageSize());
        Assertions.assertEquals(userProfilePage.getTotalPages(), pageMetadata.totalPages());
        Assertions.assertEquals(userProfilePage.getTotalElements(), pageMetadata.totalElements());

    }

    @Test
    public void testGetUserProfilesReadMapperCorrectlyMapsAllFields_EntraUserNull() {
        // Given
        Pageable pageable = PageRequest.of(5, 30);
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setEntraUser(null);
        Page<UserProfile> userProfilePage = new PageImpl<>(List.of(userProfile), pageable, 1000);

        // When
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = readMapper.map(userProfilePage);

        // Then
        GetUserProfilesReadModel readModel = pagedReadModel.items().stream()
                .findFirst()
                .orElseThrow();
        PageMetadata pageMetadata = pagedReadModel.page();

        // User Profile assertions
        Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId());
        Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile());
        Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType());
        Assertions.assertEquals(userProfile.getUserProfileStatus().name(), readModel.profileStatus());
        Assertions.assertNull(readModel.accountStatus());
        Assertions.assertNull(readModel.fullName());

        // Page metadata assertions
        Assertions.assertEquals(userProfilePage.getNumber(), pageMetadata.pageNumber());
        Assertions.assertEquals(userProfilePage.getSize(), pageMetadata.pageSize());
        Assertions.assertEquals(userProfilePage.getTotalPages(), pageMetadata.totalPages());
        Assertions.assertEquals(userProfilePage.getTotalElements(), pageMetadata.totalElements());

    }

}
