package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.EntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetUserResolverTest {

    @Mock
    private EntraUserQueryRepository entraUserQueryRepository;

    @Mock
    private UserAccountQueryMapper userAccountQueryMapper;

    private TargetUserResolver resolver;
    private String targetOid;

    @BeforeEach
    void setUp() {
        resolver = new TargetUserResolver(entraUserQueryRepository, userAccountQueryMapper);
        targetOid = UUID.randomUUID().toString();
    }

    @Test
    void resolvesTargetUserWhenFound() {
        EntraUser entity = EntraUser.builder()
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .multiFirmUser(false)
                .entraOid(targetOid)
                .build();

        EntraUserDto expectedDto = EntraUserDto.builder()
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .multiFirmUser(false)
                .entraOid(targetOid)
                .build();

        when(entraUserQueryRepository.findByEntraOid(targetOid))
                .thenReturn(Optional.of(entity));
        when(userAccountQueryMapper.toDto(any(), any()))
                .thenReturn(expectedDto);

        TargetsUser message = () -> UUID.fromString(targetOid);
        EntraUserDto result = (EntraUserDto) resolver.resolveFor(message);

        assertThat(result).isNotNull();
        assertThat(result.getEntraOid()).isEqualTo(targetOid);
    }

    @Test
    void returnsNullWhenUserNotFound() {
        when(entraUserQueryRepository.findByEntraOid(targetOid))
                .thenReturn(Optional.empty());

        TargetsUser message = () -> UUID.fromString(targetOid);
        Object result = resolver.resolveFor(message);

        assertThat(result).isNull();
    }
}
