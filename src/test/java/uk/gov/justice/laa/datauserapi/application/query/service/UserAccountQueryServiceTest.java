package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.EntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserAccountQueryRepository;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class UserAccountQueryServiceTest {

    @Mock
    private UserAccountQueryRepository queryRepository;

    @Mock
    private EntraUserQueryRepository userRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserAccountQueryService service;

    @Test
    void shouldReturnAuditHistory() {
        UUID oid = UUID.randomUUID();

        AccountStatusHistoryView history = mock(AccountStatusHistoryView.class);

        when(queryRepository.findAuditHistoryByUserEntraObjectId(oid))
                .thenReturn(List.of(history));

        List<AccountStatusHistoryView> result =
                service.getAuditHistory(oid);

        assertThat(result).containsExactly(history);

        verify(queryRepository)
                .findAuditHistoryByUserEntraObjectId(oid);
    }

    @Test
    void shouldReturnUserStatus() {
        UUID oid = UUID.randomUUID();

        UserAccountStatusView status = mock(UserAccountStatusView.class);

        when(userRepository.findStatusByUserEntraObjectId(oid))
                .thenReturn(Optional.of(status));

        UserAccountStatusView result =
                service.getUserStatus(oid);

        assertThat(result).isSameAs(status);
    }

    @Test
    void shouldThrowWhenUserStatusNotFound() {
        UUID oid = UUID.randomUUID();

        when(userRepository.findStatusByUserEntraObjectId(oid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserStatus(oid))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + oid);
    }

    @Test
    void shouldFindUserByEntraOid() {
        String entraOid = UUID.randomUUID().toString();

        EntraUser user = EntraUser.builder().build();
        EntraUserDto dto = new EntraUserDto();

        when(userRepository.findByEntraOid(entraOid))
                .thenReturn(Optional.of(user));

        when(modelMapper.map(user, EntraUserDto.class))
                .thenReturn(dto);

        EntraUserDto result =
                service.findByEntraOid(entraOid);

        assertThat(result).isSameAs(dto);

        verify(modelMapper)
                .map(user, EntraUserDto.class);
    }

    @Test
    void shouldThrowWhenFindByEntraOidNotFound() {
        String entraOid = UUID.randomUUID().toString();

        when(userRepository.findByEntraOid(entraOid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.findByEntraOid(entraOid))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + entraOid);
    }

    @Test
    void shouldFindUserAccountSummary() {
        String entraOid = UUID.randomUUID().toString();

        EntraUser user = EntraUser.builder().build();
        EntraUserDto dto = new EntraUserDto();

        when(userRepository.findByEntraOid(entraOid))
                .thenReturn(Optional.of(user));

        when(modelMapper.map(user, EntraUserDto.class))
                .thenReturn(dto);

        EntraUserDto result =
                service.findUserAccountSummaryByUserEntraObjectId(entraOid);

        assertThat(result).isSameAs(dto);
    }

    @Test
    void shouldThrowWhenUserAccountSummaryNotFound() {
        String entraOid = UUID.randomUUID().toString();

        when(userRepository.findByEntraOid(entraOid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.findUserAccountSummaryByUserEntraObjectId(entraOid))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + entraOid);
    }

    @Test
    void shouldSearchUsers() {
        Pageable pageable = PageRequest.of(0, 20);

        UserSearchCriteria criteria = mock(UserSearchCriteria.class);

        EntraUser user = EntraUser.builder().build();
        EntraUserDto dto = new EntraUserDto();

        Page<EntraUser> users =
                new PageImpl<>(List.of(user));

        when(userRepository.searchUsers(criteria, pageable))
                .thenReturn(users);

        when(modelMapper.map(user, EntraUserDto.class))
                .thenReturn(dto);

        Page<EntraUserDto> result =
                service.searchUsers(criteria, pageable);

        assertThat(result.getContent())
                .containsExactly(dto);
    }
}