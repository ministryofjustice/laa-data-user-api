package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountDetailsServiceTest {

    @Mock
    private UserAccountQueryService queryService;

    @Mock
    private UserViewMapper userViewMapper;

    private static final String ACTOR_ID = UUID.randomUUID().toString();
    private static final String TARGET_ID = UUID.randomUUID().toString();

    @Test
    void mapsUserToView() {
        EntraUserDto target = mock(EntraUserDto.class);
        UserView view = mock(UserView.class);
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(Optional.of(target));
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        UserAccountDetailsService service = new UserAccountDetailsService(queryService, userViewMapper);
        UserView result = service.getAccount(TARGET_ID, ACTOR_ID);

        assertThat(result).isSameAs(view);
    }

    @Test
    void throwsUserNotFoundWhenAccountIsMissing() {
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(Optional.empty());

        UserAccountDetailsService service = new UserAccountDetailsService(queryService, userViewMapper);

        assertThatThrownBy(() -> service.getAccount(TARGET_ID, ACTOR_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with ID: " + TARGET_ID);
    }
}
