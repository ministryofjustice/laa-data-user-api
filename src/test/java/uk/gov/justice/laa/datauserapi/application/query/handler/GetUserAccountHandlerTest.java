package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void returnsUserViewForValidTarget() {
        EntraUserDto target = mock(EntraUserDto.class);
        UserView view = mock(UserView.class);
        
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_ID))
                .thenReturn(target);
        when(userViewMapper.mapToUserView(target)).thenReturn(view);

        GetUserAccountHandler handler = handler();

        assertThat(handler.handle(new GetUserAccountQuery(TARGET_ID, ACTOR_ID)))
                .isSameAs(view);
    }

    @Test
    void throwsOnInvalidUuidFormat() {
        GetUserAccountHandler handler = handler();

        assertThatThrownBy(() -> handler.handle(
                new GetUserAccountQuery("invalid-uuid", ACTOR_ID)))
                .isInstanceOf(InvalidUuidFormatException.class);
    }

    private GetUserAccountHandler handler() {
        return new GetUserAccountHandler(queryService, userViewMapper);
    }
}
