package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserAccountHandlerTest {

    @Mock
    private UserAccountQueryService queryService;

    @Mock
    private UserViewMapper userViewMapper;

    @Test
    void allowsInternalActorToViewInternalUser() {
        EntraUserDto actor = user(UserType.INTERNAL, Set.of());
        EntraUserDto target = user(UserType.INTERNAL, Set.of());
        UserView view = mock(UserView.class);
        when(queryService.findByEntraOid("actor")).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId("target"))
                .thenReturn(target);
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        GetUserAccountHandler handler = handler();

        assertThat(handler.handle(new GetUserAccountQuery("target", "actor")))
                .isSameAs(view);
    }

    @Test
    void hidesExternalUserWhenActorLacksPermission() {
        EntraUserDto actor = user(UserType.INTERNAL, Set.of());
        EntraUserDto target = user(UserType.EXTERNAL, Set.of());
        when(queryService.findByEntraOid("actor")).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId("target"))
                .thenReturn(target);

        assertThatThrownBy(() -> handler().handle(
                new GetUserAccountQuery("target", "actor")))
                .isInstanceOf(uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException.class);
    }

    private GetUserAccountHandler handler() {
        return new GetUserAccountHandler(queryService, userViewMapper);
    }

    private EntraUserDto user(UserType userType, Set<AppRole> roles) {
        UserProfile profile = mock(UserProfile.class);
        lenient().when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(userType);
        lenient().when(profile.getAppRoles()).thenReturn(roles);
        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }
}
