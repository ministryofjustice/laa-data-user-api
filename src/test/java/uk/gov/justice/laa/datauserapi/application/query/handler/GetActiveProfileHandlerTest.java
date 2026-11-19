package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.time.LocalDateTime;
import java.util.Set;
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
    void delegatesToProfileService() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        AuthenticatedEntraUser actor = authenticatedTestUser();
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        when(service.getActiveProfile(userEntraObjectId)).thenReturn(expected);

        GetActiveProfileQuery query = new GetActiveProfileQuery(userEntraObjectId);
        assertThat(new GetActiveProfileHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).getActiveProfile(userEntraObjectId);
    }

    @Test
    void returnsActiveProfileWhenFound() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        when(service.getActiveProfile(userEntraObjectId)).thenReturn(expected);
        
        GetActiveProfileHandler handler = new GetActiveProfileHandler(service);
        GetActiveProfileQuery query = new GetActiveProfileQuery(userEntraObjectId);
        UserProfileDetailView result = handler.handle(query, authenticatedTestUser());
        
        assertThat(result).isSameAs(expected);
    }

    private AuthenticatedEntraUser authenticatedTestUser() {
        return new AuthenticatedEntraUser(
                UUID.randomUUID(),
                actorOid,
                "firstName",
                "lastName",
                "email@email.com",
                UserAccountStatus.ACTIVE,
                false,
                LocalDateTime.now(),
                false,
                true,
                null,
                false,
                null,
                null,
                Set.of(),
                null
        );
    }
}
