package uk.gov.justice.laa.datauserapi.controller.query;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfileByIdQueryMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfileByIdResponseMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesQueryMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesResponseMapper;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
public class UserProfileQueryControllerTest {

    @Mock
    private GetUserProfilesHandler getUserProfilesHandler;
    @Mock
    private GetUserProfilesQueryMapper getUserProfilesQueryMapper;
    @Mock
    private GetUserProfilesResponseMapper getUserProfilesResponseMapper;
    @Mock
    private GetUserProfileByIdHandler getUserProfileByIdHandler;
    @Mock
    private GetUserProfileByIdQueryMapper getUserProfileByIdQueryMapper;
    @Mock
    private GetUserProfileByIdResponseMapper getUserProfileByIdResponseMapper;

    private UserProfileQueryController controller;

    @BeforeEach
    public void setup() {
        controller = new UserProfileQueryController(
                getUserProfilesHandler,
                getUserProfilesQueryMapper,
                getUserProfilesResponseMapper,
                getUserProfileByIdHandler,
                getUserProfileByIdQueryMapper,
                getUserProfileByIdResponseMapper);
    }

    @Test
    public void testGetUserProfiles() {
        // Given
        GetUserProfilesRequest request = GetUserProfilesRequest.builder().build();
        AuthenticatedEntraUser authenticatedUser = AuthenticatedEntraUser.builder().build();
        GetUserProfilesQuery query = GetUserProfilesQuery.builder().build();
        GetUserProfilesReadModel readModel = GetUserProfilesReadModel.builder().build();
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = PagedReadModel.<GetUserProfilesReadModel>builder()
                .items(List.of(readModel))
                .build();
        UserProfileSummaryView view = UserProfileSummaryView.builder().build();
        PagedView<UserProfileSummaryView> pagedView = PagedView.<UserProfileSummaryView>builder()
                .items(List.of(view))
                .build();
        ResponseEntity<PagedView<UserProfileSummaryView>> mockResponse = ResponseEntity.ok(pagedView);
        when(getUserProfilesQueryMapper.map(request, authenticatedUser)).thenReturn(query);
        when(getUserProfilesHandler.handle(query)).thenReturn(pagedReadModel);
        when(getUserProfilesResponseMapper.map(pagedReadModel)).thenReturn(mockResponse);

        // When
        ResponseEntity<PagedView<UserProfileSummaryView>> response = controller.getUserProfiles(request, authenticatedUser);

        Assertions.assertSame(response, mockResponse);
        verify(getUserProfilesQueryMapper, times(1)).map(eq(request), eq(authenticatedUser));
        verify(getUserProfilesHandler, times(1)).handle(eq(query));
        verify(getUserProfilesResponseMapper, times(1)).map(eq(pagedReadModel));
    }

    @Test
    public void testGetUserProfileById() {
        // Given
        UUID profileId = UUID.randomUUID();
        AuthenticatedEntraUser authenticatedUser = AuthenticatedEntraUser.builder().build();
        GetUserProfileByIdQuery query = GetUserProfileByIdQuery.builder()
                .id(profileId)
                .build();
        GetUserProfileByIdReadModel readModel = GetUserProfileByIdReadModel.builder().build();
        UserProfileDetailView view = UserProfileDetailView.builder().build();
        ResponseEntity<UserProfileDetailView> mockResponse = ResponseEntity.ok(view);
        when(getUserProfileByIdQueryMapper.map(profileId.toString(), authenticatedUser)).thenReturn(query);
        when(getUserProfileByIdHandler.handle(query)).thenReturn(readModel);
        when(getUserProfileByIdResponseMapper.map(readModel)).thenReturn(mockResponse);

        // When
        ResponseEntity<UserProfileDetailView> response = controller.getUserProfileById(profileId.toString(), authenticatedUser);

        Assertions.assertSame(response, mockResponse);
        verify(getUserProfileByIdQueryMapper, times(1)).map(eq(profileId.toString()), eq(authenticatedUser));
        verify(getUserProfileByIdHandler, times(1)).handle(eq(query));
        verify(getUserProfileByIdResponseMapper, times(1)).map(eq(readModel));
    }

}
