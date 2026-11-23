package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileHandlerTest {

    private String userEntraObjectId;
    private String actorOid;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
        actorOid = UUID.randomUUID().toString();
    }

    @Test
    void delegatesToProfileServiceWithTheActor() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        AuthenticatedEntraUser actor = authenticatedTestUser();
        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        when(service.getActiveProfile(userEntraObjectId, actor)).thenReturn(expected);

        GetActiveProfileQuery query = new GetActiveProfileQuery(userEntraObjectId);
        assertThat(new GetActiveProfileHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).getActiveProfile(userEntraObjectId, actor);
    }

    private AuthenticatedEntraUser authenticatedTestUser() {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(UUID.randomUUID())
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid(actorOid)
                .activeProfile(profile)
                .build();
    }
}
