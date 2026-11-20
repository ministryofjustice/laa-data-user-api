package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
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
    private UUID profileId;
    private UUID firmId;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
        actorOid = UUID.randomUUID().toString();
        profileId = UUID.randomUUID();
        firmId = UUID.randomUUID();
    }

    @Test
    void delegatesToProfileService() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        UserProfileDetailsVisibilityPolicyInputResolver resolver = mock(UserProfileDetailsVisibilityPolicyInputResolver.class);
        AuthenticatedEntraUser actor = authenticatedTestUser();
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        
        when(resolver.resolve(new GetActiveProfileQuery(userEntraObjectId)))
                .thenReturn(new ProfileVisibilityContext(true, profileId, UserType.EXTERNAL, firmId));
        when(service.getActiveProfile(userEntraObjectId, profileId)).thenReturn(expected);

        GetActiveProfileQuery query = new GetActiveProfileQuery(userEntraObjectId);
        assertThat(new GetActiveProfileHandler(service, resolver).handle(query, actor)).isSameAs(expected);
        verify(service).getActiveProfile(userEntraObjectId, profileId);
    }

    @Test
    void returnsActiveProfileWhenFound() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        UserProfileDetailsVisibilityPolicyInputResolver resolver = mock(UserProfileDetailsVisibilityPolicyInputResolver.class);
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        
        when(resolver.resolve(new GetActiveProfileQuery(userEntraObjectId)))
                .thenReturn(new ProfileVisibilityContext(true, profileId, UserType.EXTERNAL, firmId));
        when(service.getActiveProfile(userEntraObjectId, profileId)).thenReturn(expected);
        
        GetActiveProfileHandler handler = new GetActiveProfileHandler(service, resolver);
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
