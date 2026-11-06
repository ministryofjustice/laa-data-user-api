package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryprofile.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.GetUserProfilesQueryService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesHandlerTest {

    @Mock
    private GetUserProfilesQueryService getUserProfilesQueryService;

    private String userEntraObjectId;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
    }

    @Test
    void returnsProfilesFromService() {
        UserProfileSummaryView profile = new UserProfileSummaryView(
                UUID.randomUUID(), true, UserType.INTERNAL, "user@example.com", "John Doe",
                "", "", false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);
        List<UserProfileSummaryView> expected = List.of(profile);
        
        when(getUserProfilesQueryService.getUserProfiles(userEntraObjectId))
                .thenReturn(expected);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(getUserProfilesQueryService);
        GetUserProfilesQuery query = new GetUserProfilesQuery(userEntraObjectId);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).isEqualTo(expected);
        verify(getUserProfilesQueryService).getUserProfiles(userEntraObjectId);
    }
}
