package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.request.BulkDeactivateFirmUsersRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmCommandControllerTest {

    @Mock
    private BulkDeactivateFirmUsersHandler handler;

    @Mock
    private BulkDeactivateFirmUsersMapper mapper;

    private FirmCommandController controller;

    @BeforeEach
    void setUp() {
        controller = new FirmCommandController(handler, mapper);
    }

    @Test
    void bulkDeactivateUsers_usesAuthenticatedActorAndReturnsResult() {
        String firmId = "123456";
        UUID actorId = UUID.randomUUID();
        final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(actorId)
                .email("actor@example.com")
                .active(true)
                .build();
        BulkDeactivateFirmUsersRequest request =
                new BulkDeactivateFirmUsersRequest(firmId, DeactivateUserReason.FirmClosureorMerger);
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);
        CommandResult expected = CommandResult.success("Deactivated 1 users");
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .id(actorId)
                .entraOid(actorId.toString())
                .build();
        when(mapper.toCommand(request)).thenReturn(command);
        when(handler.handle(command, user)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.bulkDeactivateUsers(request, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mapper).toCommand(request);
        verify(handler).handle(command, user);
    }
}
