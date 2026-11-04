package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedView;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedViewMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.List;
import java.util.UUID;

public class GetUserProfilesResponseMapperTest {

    private GetUserProfilesResponseMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new GetUserProfilesResponseMapper();
    }

    @Test
    public void testMapShouldReturnOkResponseWithEmptyItemsAndPageMetadata() {
        PageMetadata pageMetadata = PageMetadata.builder()
                .pageNumber(0)
                .pageSize(10)
                .totalElements(0)
                .totalPages(0)
                .build();
        PagedReadModel<GetUserProfilesReadModel> readModel = PagedReadModel.<GetUserProfilesReadModel>builder()
                .items(List.of())
                .page(pageMetadata)
                .build();

        ResponseEntity<PagedView<UserProfileSummaryView>> response = mapper.map(readModel);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertTrue(response.getBody().items().isEmpty());
        Assertions.assertEquals(new PagedViewMetadata(0, 10, 0, 0), response.getBody().page());
    }

    @Test
    public void testMapShouldConvertSingleReadModelToUserProfileSummaryView() {
        UUID userProfileId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        GetUserProfilesReadModel readModel = GetUserProfilesReadModel.builder()
                .userProfileId(userProfileId)
                .activeProfile(Boolean.TRUE)
                .userType(UserType.INTERNAL)
                .email("user@example.com")
                .fullName("Test User")
                .firmName("Test Firm")
                .firmId("firm-123")
                .multiFirmUser(Boolean.FALSE)
                .accountStatus(UserAccountStatus.ACTIVE)
                .profileStatus(UserProfileStatus.COMPLETE)
                .hasAppRoles(Boolean.TRUE)
                .build();
        PageMetadata pageMetadata = PageMetadata.builder()
                .pageNumber(1)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = PagedReadModel.<GetUserProfilesReadModel>builder()
                .items(List.of(readModel))
                .page(pageMetadata)
                .build();

        ResponseEntity<PagedView<UserProfileSummaryView>> response = mapper.map(pagedReadModel);

        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(1, response.getBody().items().size());
        UserProfileSummaryView view = response.getBody().items().iterator().next();

        Assertions.assertAll(
                () -> Assertions.assertEquals(userProfileId, view.userProfileId()),
                () -> Assertions.assertEquals(Boolean.TRUE, view.activeProfile()),
                () -> Assertions.assertEquals(UserType.INTERNAL, view.userType()),
                () -> Assertions.assertEquals("user@example.com", view.email()),
                () -> Assertions.assertEquals("Test User", view.fullName()),
                () -> Assertions.assertEquals("Test Firm", view.firmName()),
                () -> Assertions.assertEquals("firm-123", view.firmId()),
                () -> Assertions.assertEquals(Boolean.FALSE, view.multiFirmUser()),
                () -> Assertions.assertEquals(UserAccountStatus.ACTIVE, view.accountStatus()),
                () -> Assertions.assertEquals(UserProfileStatus.COMPLETE, view.profileStatus()),
                () -> Assertions.assertEquals(Boolean.TRUE, view.hasAppRoles()),
                () -> Assertions.assertEquals(new PagedViewMetadata(1, 20, 1, 1), response.getBody().page())
        );
    }

    @Test
    public void testMapShouldPreserveNullBooleanFieldsAndMapMultipleItems() {
        UUID userProfileId1 = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID userProfileId2 = UUID.fromString("33333333-3333-4333-8333-333333333333");

        GetUserProfilesReadModel firstReadModel = GetUserProfilesReadModel.builder()
                .userProfileId(userProfileId1)
                .activeProfile(null)
                .userType(UserType.EXTERNAL)
                .email("first@example.com")
                .fullName("First User")
                .firmName("First Firm")
                .firmId("firm-1")
                .multiFirmUser(null)
                .accountStatus(UserAccountStatus.ACTIVE)
                .profileStatus(UserProfileStatus.COMPLETE)
                .hasAppRoles(null)
                .build();
        GetUserProfilesReadModel secondReadModel = GetUserProfilesReadModel.builder()
                .userProfileId(userProfileId2)
                .activeProfile(Boolean.FALSE)
                .userType(UserType.EXTERNAL)
                .email("second@example.com")
                .fullName("Second User")
                .firmName("Second Firm")
                .firmId("firm-2")
                .multiFirmUser(Boolean.TRUE)
                .accountStatus(UserAccountStatus.DEACTIVATED)
                .profileStatus(UserProfileStatus.PENDING)
                .hasAppRoles(Boolean.FALSE)
                .build();
        PageMetadata pageMetadata = PageMetadata.builder()
                .pageNumber(2)
                .pageSize(50)
                .totalElements(2)
                .totalPages(1)
                .build();
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = PagedReadModel.<GetUserProfilesReadModel>builder()
                .items(List.of(firstReadModel, secondReadModel))
                .page(pageMetadata)
                .build();

        ResponseEntity<PagedView<UserProfileSummaryView>> response = mapper.map(pagedReadModel);

        Assertions.assertNotNull(response.getBody());
        List<UserProfileSummaryView> views = response.getBody().items().stream().toList();
        Assertions.assertEquals(2, views.size());
        Assertions.assertAll(
                () -> Assertions.assertEquals(userProfileId1, views.getFirst().userProfileId()),
                () -> Assertions.assertFalse(views.getFirst().activeProfile()),
                () -> Assertions.assertFalse(views.getFirst().multiFirmUser()),
                () -> Assertions.assertFalse(views.getFirst().hasAppRoles()),
                () -> Assertions.assertEquals(userProfileId2, views.get(1).userProfileId()),
                () -> Assertions.assertEquals(Boolean.FALSE, views.get(1).activeProfile()),
                () -> Assertions.assertEquals(Boolean.TRUE, views.get(1).multiFirmUser()),
                () -> Assertions.assertEquals(Boolean.FALSE, views.get(1).hasAppRoles()),
                () -> Assertions.assertEquals(new PagedViewMetadata(2, 50, 2, 1), response.getBody().page())
        );
    }
}
