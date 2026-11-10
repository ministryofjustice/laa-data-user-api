package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.service.UpdateUserDetailsService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserDetailsHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesCommandAndActorAndPreservesResult(boolean success) {
        UpdateUserDetailsService service = mock(UpdateUserDetailsService.class);
        UpdateUserDetailsCommand command = new UpdateUserDetailsCommand(UUID.randomUUID(), "New", null, null);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = success ? CommandResult.success("updated") : CommandResult.failure("rejected");
        when(service.update(command, actor)).thenReturn(expected);

        assertThat(user.getFirstName()).isEqualTo("Old");
        assertThat(user.getLastName()).isEqualTo("Last");
        assertThat(user.getEmail()).isEqualTo("old@x.com");
    }

    @Test
    void rejectsEmailUsedByAnotherUser() {
        when(repository.findByEntraOid(target)).thenReturn(Optional.of(user));
        when(repository.existsByEmailIgnoreCaseAndIdNot("taken@x.com", user.getId())).thenReturn(true);

        CommandResult result = handler.handle(new UpdateUserDetailsCommand(targetId, "New", null, "taken@x.com"), actor);

        assertThat(result.success()).isFalse();
        assertThat(user.getFirstName()).isEqualTo("Old");
        verifyNoInteractions(techServicesClient);
        verify(repository, never()).save(any());
    }

    @Test
    void skipsUniquenessCheckWhenEmailOnlyChangesCase() {
        when(repository.findByEntraOid(target)).thenReturn(Optional.of(user));
        tsSucceeds(null, null, "OLD@x.com");

        handler.handle(new UpdateUserDetailsCommand(targetId, null, null, "OLD@x.com"), actor);

        verify(repository, never()).existsByEmailIgnoreCaseAndIdNot(any(), any());
        assertThat(user.getEmail()).isEqualTo("OLD@x.com");
    }

    @Test
    void throwsAndDoesNotSaveWhenTechServicesFails() {
        when(repository.findByEntraOid(target)).thenReturn(Optional.of(user));
        when(techServicesClient.updateUserDetails(target, "New", null, null)).thenReturn(TechServicesApiResponse.error(
                TechServicesErrorResponse.builder().success(false)
                        .code(new TechServicesErrorCode("ERR")).message("failed").build()));

        assertThatThrownBy(() -> handler.handle(new UpdateUserDetailsCommand(targetId, "New", null, null), actor))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tech Services API call failed");
        assertThat(user.getFirstName()).isEqualTo("Old");
        verify(repository, never()).save(any());
    }
}
