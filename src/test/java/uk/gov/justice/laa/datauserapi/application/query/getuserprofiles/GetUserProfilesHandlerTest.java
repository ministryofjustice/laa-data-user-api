package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileReadRepository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class GetUserProfilesHandlerTest {

    @Mock
    private UserProfileReadRepository repository;
    @Mock
    private GetUserProfilesReadMapper readMapper;

    private GetUserProfilesHandler handler;

    @BeforeEach
    public void setup() {
        handler = new GetUserProfilesHandler(repository, readMapper);
    }

    @Test
    public void testGetUserProfilesHandlerHandlesQuery() {
        // Given
        GetUserProfilesQuery query = GetUserProfilesQuery.builder()
                .pageRequest(PageRequest.of(1, 20))
                .build();
        UserProfile userProfile = UserProfile.builder()
                .build();
        Page<UserProfile> userProfilesPage = new PageImpl<>(List.of(userProfile));
        GetUserProfilesReadModel mockReadModel = GetUserProfilesReadModel.builder().build();
        PagedReadModel<GetUserProfilesReadModel> pagedReadModel = PagedReadModel.<GetUserProfilesReadModel>builder()
                .items(List.of(mockReadModel))
                .build();

        when(repository.findAll(query, query.pageRequest())).thenReturn(userProfilesPage);
        when(readMapper.map(userProfilesPage)).thenReturn(pagedReadModel);

        // When
        PagedReadModel<GetUserProfilesReadModel> readModel = handler.handle(query);

        // Then
        Assertions.assertSame(pagedReadModel, readModel);
        verify(repository, times(1)).findAll(query, query.pageRequest());
        verify(readMapper, times(1)).map(userProfilesPage);
    }

}
