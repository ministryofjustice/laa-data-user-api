package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;


import java.util.Set;
import java.util.UUID;

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

    private static final String ACTOR_ID = UUID.randomUUID().toString();
    private static final String TARGET_ID = UUID.randomUUID().toString();

    @Test
    void allowsInternalActorToViewInternalUser() {
        EntraUserDto actor = user(UserType.INTERNAL, Set.of());
        EntraUserDto target = user(UserType.INTERNAL, Set.of());
        UserView view = mock(UserView.class);
        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(target);
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        GetUserAccountHandler handler = handler();

        assertThat(handler.handle(new GetUserAccountQuery(TARGET_ID, ACTOR_ID)))
                .isSameAs(view);
    }

    @Test
    void hidesExternalUserWhenActorLacksPermission() {
        EntraUserDto actor = user(UserType.INTERNAL, Set.of());
        EntraUserDto target = user(UserType.EXTERNAL, Set.of());
        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(target);

        assertThatThrownBy(() -> handler().handle(
                new GetUserAccountQuery(TARGET_ID, ACTOR_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void allowsInternalActorWithPermissionToViewExternalUser() {
        AppRole role = mock(AppRole.class);
        when(role.getPermissions()).thenReturn(Set.of(Permission.VIEW_EXTERNAL_USER));

        EntraUserDto actor = user(UserType.INTERNAL, Set.of(role));
        EntraUserDto target = user(UserType.EXTERNAL, Set.of());

        UserView view = mock(UserView.class);

        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(target);
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        UserView result = handler().handle(
                new GetUserAccountQuery(TARGET_ID, ACTOR_ID));

        assertThat(result).isSameAs(view);
    }

    @Test
    void allowsExternalActorToViewUserFromSameFirm() {
        Firm firm = mock(Firm.class);
        when(firm.getId()).thenReturn(UUID.randomUUID());

        EntraUserDto actor = user(UserType.EXTERNAL, Set.of());
        EntraUserDto target = user(UserType.EXTERNAL, Set.of());

        when(actor.getUserProfiles().stream().findFirst().get().getFirm()).thenReturn(firm);
        when(target.getUserProfiles().stream().findFirst().get().getFirm()).thenReturn(firm);

        String targetId = UUID.randomUUID().toString();

        UserView view = mock(UserView.class);

        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(targetId))
                .thenReturn(target);
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        assertThat(handler().handle(
                new GetUserAccountQuery(targetId, ACTOR_ID)))
                .isSameAs(view);
    }

    @Test
    void hidesExternalUserFromDifferentFirm() {
        Firm actorFirm = mock(Firm.class);
        Firm targetFirm = mock(Firm.class);

        when(actorFirm.getId()).thenReturn(UUID.randomUUID());
        when(targetFirm.getId()).thenReturn(UUID.randomUUID());

        EntraUserDto actor = user(UserType.EXTERNAL, Set.of());
        EntraUserDto target = user(UserType.EXTERNAL, Set.of());

        when(actor.getUserProfiles().stream().findFirst().get().getFirm()).thenReturn(actorFirm);
        when(target.getUserProfiles().stream().findFirst().get().getFirm()).thenReturn(targetFirm);

        String targetId = UUID.randomUUID().toString();

        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(targetId))
                .thenReturn(target);

        assertThatThrownBy(() ->
                handler().handle(new GetUserAccountQuery(targetId, ACTOR_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenActorHasNoActiveProfile() {
        UserProfile profile = mock(UserProfile.class);
        when(profile.isActiveProfile()).thenReturn(false);

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);

        assertThatThrownBy(() ->
                handler().handle(new GetUserAccountQuery(
                        UUID.randomUUID().toString(),
                        ACTOR_ID)))
                .isInstanceOf(InvalidActorContextException.class);
    }

    @Test
    void throwsWhenActorUserTypeCannotBeDetermined() {
        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(null);

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid(ACTOR_ID)).thenReturn(actor);

        assertThatThrownBy(() ->
                handler().handle(new GetUserAccountQuery(
                        UUID.randomUUID().toString(),
                        ACTOR_ID)))
                .isInstanceOf(InvalidActorContextException.class);
    }


    private GetUserAccountHandler handler() {
        return new GetUserAccountHandler(queryService, userViewMapper);
    }

    private EntraUserDto user(UserType userType, Set<AppRole> roles) {
        UserProfile profile = mock(UserProfile.class);
        lenient().when(profile.isActiveProfile()).thenReturn(true);
        lenient().when(profile.getUserType()).thenReturn(userType);
        lenient().when(profile.getAppRoles()).thenReturn(roles);
        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }
}
