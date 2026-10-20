package uk.gov.justice.laa.datauserapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountCreationServiceTest {
    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    EntraUserClient entraUserClient;

    @Test
    void createsEntraUserAndLocalProfile() {
        UserAccountCreationService service = new UserAccountCreationService(jdbcTemplate, entraUserClient);
        CreateUserAccountCommand command = new CreateUserAccountCommand(
                "user@example.com", "First", "Last",
                CreateUserAccountCommand.UserType.EXTERNAL, true,
                "11111111-1111-1111-1111-111111111111");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0, 1);
        when(entraUserClient.createUser(command)).thenReturn("entra-oid");

        CreateUserAccountResult result = service.create(command, "actor");

        assertThat(result.success()).isTrue();
        assertThat(result.userEntraObjectId()).isEqualTo("entra-oid");
        assertThat(result.userProfileId()).isNotNull();
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsExistingEmailBeforeCallingEntra() {
        UserAccountCreationService service = new UserAccountCreationService(jdbcTemplate, entraUserClient);
        CreateUserAccountCommand command = new CreateUserAccountCommand(
                "user@example.com", "First", "Last",
                CreateUserAccountCommand.UserType.EXTERNAL, false,
                "11111111-1111-1111-1111-111111111111");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        assertThatThrownBy(() -> service.create(command, "actor"))
                .isInstanceOf(CreateUserException.class)
                .hasMessage("A user with this email already exists");
        verifyNoInteractions(entraUserClient);
    }
}
