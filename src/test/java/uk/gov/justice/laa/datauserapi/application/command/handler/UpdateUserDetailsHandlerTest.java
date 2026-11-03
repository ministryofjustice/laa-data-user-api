package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserDetailsMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserDetailsHandlerTest {

    @Mock
    private EntraUserCommandRepository repository;
    @Mock
    private UpdateUserDetailsMapper mapper;
    @Mock
    private TechServicesClient techServicesClient;

    private UpdateUserDetailsHandler handler;
    private final UUID targetId = UUID.randomUUID();
    private final String target = targetId.toString();
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor").build();
    private EntraUser user;

    @BeforeEach
    void setUp() {
        handler = new UpdateUserDetailsHandler(repository, mapper, techServicesClient);
        user = EntraUser.builder().entraOid(target).firstName("Old").lastName("Name").email("old@x.com").build();
        user.setId(UUID.randomUUID());
    }

    private void tsSucceeds(String firstName, String lastName, String email) {
        when(techServicesClient.updateUserDetails(target, firstName, lastName, email))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountEnabledResponse.builder().success(true).build()));
    }

    @Test
    void throwsWhenUserNotFound() {
        when(repository.findByEntraOid(target)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UpdateUserDetailsCommand(targetId, "A", null, null), actor))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(techServicesClient);
    }

    @Test
    void updatesAllProvidedFields() {
        final CommandResult expected = CommandResult.success("ok");
        when(repository.findByEntraOid(target)).thenReturn(Optional.of(user));
        when(repository.existsByEmailIgnoreCaseAndIdNot("new@x.com", user.getId())).thenReturn(false);
        tsSucceeds("New", "Last", "new@x.com");
        when(mapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = handler.handle(new UpdateUserDetailsCommand(targetId, "New", "Last", "new@x.com"), actor);

        assertThat(result).isSameAs(expected);
        assertThat(user.getFirstName()).isEqualTo("New");
        assertThat(user.getLastName()).isEqualTo("Last");
        assertThat(user.getEmail()).isEqualTo("new@x.com");
        assertThat(user.getLastModifiedBy()).isEqualTo("actor");
        verify(repository).save(user);
    }

    @Test
    void leavesOmittedFieldsUnchanged() {
        when(repository.findByEntraOid(target)).thenReturn(Optional.of(user));
        tsSucceeds(null, "Last", null);

        handler.handle(new UpdateUserDetailsCommand(targetId, null, "Last", null), actor);

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
                TechServicesErrorResponse.builder().success(false).code("ERR").message("failed").build()));

        assertThatThrownBy(() -> handler.handle(new UpdateUserDetailsCommand(targetId, "New", null, null), actor))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tech Services API call failed");
        assertThat(user.getFirstName()).isEqualTo("Old");
        verify(repository, never()).save(any());
    }
}
