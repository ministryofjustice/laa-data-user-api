package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.FirmDirectoryActorQueryRepository;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;

@ExtendWith(MockitoExtension.class)
class FirmAuthorisationServiceTest {

    @Mock
    private FirmDirectoryActorQueryRepository actorQueryRepository;

    private FirmAuthorisationService authorisationService;

    @BeforeEach
    void setUp() {
        authorisationService = new FirmAuthorisationService(actorQueryRepository);
    }

    @Test
    void requireFirmDirectoryAccess_throws_whenUserIsExternal() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.EXTERNAL));

        assertThatThrownBy(() -> authorisationService.requireFirmDirectoryAccess(entraUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFirmDirectoryAccess_throws_whenInternalUserLacksPermission() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.INTERNAL));
        when(actorQueryRepository.findActivePermissions(entraUserId.toString())).thenReturn(Set.of());

        assertThatThrownBy(() -> authorisationService.requireFirmDirectoryAccess(entraUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFirmDirectoryAccess_succeeds_whenInternalUserHasPermission() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.INTERNAL));
        when(actorQueryRepository.findActivePermissions(entraUserId.toString())).thenReturn(Set.of(Permission.VIEW_FIRM_DIRECTORY));

        authorisationService.requireFirmDirectoryAccess(entraUserId);
    }

    @Test
    void resolveSearchAccess_allowsAllFirms_forInternalUser() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.INTERNAL));

        FirmSearchAccess access = authorisationService.resolveSearchAccess(entraUserId);

        assertThat(access).isEqualTo(new FirmSearchAccess(true, null));
    }

    @Test
    void resolveSearchAccess_restrictsToActiveFirm_forExternalUser() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.EXTERNAL));
        when(actorQueryRepository.findActiveFirmCode(entraUserId.toString())).thenReturn(java.util.Optional.of("123456"));

        FirmSearchAccess access = authorisationService.resolveSearchAccess(entraUserId);

        assertThat(access).isEqualTo(new FirmSearchAccess(false, "123456"));
    }

    @Test
    void resolveSearchAccess_deniesExternalUser_whenNoActiveFirm() {
        UUID entraUserId = UUID.randomUUID();
        when(actorQueryRepository.findActiveUserType(entraUserId.toString())).thenReturn(java.util.Optional.of(UserType.EXTERNAL));
        when(actorQueryRepository.findActiveFirmCode(entraUserId.toString())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authorisationService.resolveSearchAccess(entraUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("External user has no active firm");
    }
}