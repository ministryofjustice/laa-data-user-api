package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.contracts.response.TriggerCcmsRoleSyncResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TriggerCcmsRoleSyncHandlerTest {

    private final CcmsRoleSyncService service = mock(CcmsRoleSyncService.class);
    private final TriggerCcmsRoleSyncHandler handler = new TriggerCcmsRoleSyncHandler(service);
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor").build();

    @Test
    void returnsCompletedSyncResult() {
        when(service.sync("actor")).thenReturn(new TriggerCcmsRoleSyncResult(true, 2));
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor))
                .isEqualTo(new TriggerCcmsRoleSyncResult(true, 2));
        verify(service).sync("actor");
    }

    @Test
    void propagatesDownstreamFailureForHttpMapping() {
        var failure = new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "CCMS timed out");
        when(service.sync("actor")).thenThrow(failure);

        assertThatThrownBy(() -> handler.handle(new TriggerCcmsRoleSyncCommand(), actor)).isSameAs(failure);
    }

    @Test
    void propagatesUnexpectedFailure() {
        var failure = new IllegalStateException("unexpected");
        when(service.sync("actor")).thenThrow(failure);

        assertThatThrownBy(() -> handler.handle(new TriggerCcmsRoleSyncCommand(), actor)).isSameAs(failure);
    }
}
