package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchUsersHandlerTest {

    @Mock
    private UserAccountQueryService queryService;

    @Mock
    private UserViewMapper userViewMapper;

    @Captor
    private ArgumentCaptor<UserSearchCriteria> criteriaCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    void enrichesSearchWithInternalActorContextAndDefaultsPaging() {
        EntraUserDto actor = actor(UserType.INTERNAL, null);
        when(queryService.findByEntraOid("actor")).thenReturn(actor);
        when(queryService.searchUsers(any(), any())).thenReturn(Page.empty());

        SearchUsersHandler handler = new SearchUsersHandler(queryService, userViewMapper);
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        UserAccountSummaryPage result = handler.handle(new SearchUsersQuery(criteria, "actor"));

        assertThat(result.items()).isEmpty();
        verify(queryService).searchUsers(criteriaCaptor.capture(), pageableCaptor.capture());
        assertThat(criteriaCaptor.getValue().actorInternal()).isTrue();
        assertThat(criteriaCaptor.getValue().actorExternal()).isFalse();
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    private EntraUserDto actor(UserType userType, UUID firmId) {
        UserProfile profile = mock(UserProfile.class);
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(userType);
        when(profile.getAppRoles()).thenReturn(Set.of());
        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));
        return actor;
    }
}
