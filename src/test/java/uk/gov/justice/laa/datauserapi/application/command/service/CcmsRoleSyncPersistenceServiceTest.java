package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.application.command.mapper.CcmsSyncProfileMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CcmsRoleSyncPersistenceServiceTest {

    private final UserProfileCommandRepository repository = mock(UserProfileCommandRepository.class);
    private final CcmsSyncProfileMapper mapper = mock(CcmsSyncProfileMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.ofHours(2));
    private final CcmsRoleSyncPersistenceService service =
            new CcmsRoleSyncPersistenceService(repository, mapper, clock);
    private final UUID id = UUID.randomUUID();

    @Test
    void loadsEntityAndReturnsMappedSnapshot() {
        UserProfile entity = UserProfile.builder().id(id).build();
        CcmsSyncProfile snapshot = new CcmsSyncProfile(id, "user-oid", UUID.randomUUID(),
                "First", "Last", "user@example.test", UserType.INTERNAL, null,
                Map.of("application", Set.of("role")));
        when(repository.findCcmsSyncProfile(id)).thenReturn(Optional.of(entity));
        when(mapper.toSnapshot(entity)).thenReturn(snapshot);

        assertThat(service.load(id)).containsSame(snapshot);

        verify(repository).findCcmsSyncProfile(id);
        verify(mapper).toSnapshot(entity);
        verifyNoMoreInteractions(repository, mapper);
    }

    @Test
    void missingProfileReturnsEmptyWithoutMapping() {
        when(repository.findCcmsSyncProfile(id)).thenReturn(Optional.empty());

        assertThat(service.load(id)).isEmpty();

        verify(repository).findCcmsSyncProfile(id);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recordsResultThroughNarrowUpdateUsingInjectedClock(boolean successful) {
        LocalDateTime expectedTimestamp = LocalDateTime.of(2026, 1, 2, 5, 4, 5);
        when(repository.updateCcmsSyncResult(id, successful, "actor-oid", expectedTimestamp)).thenReturn(1);

        assertThat(service.recordResult(id, successful, "actor-oid")).isTrue();

        verify(repository).updateCcmsSyncResult(id, successful, "actor-oid", expectedTimestamp);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(mapper);
    }

    @Test
    void returnsFalseWhenProfileNoLongerExists() {
        LocalDateTime expectedTimestamp = LocalDateTime.of(2026, 1, 2, 5, 4, 5);
        when(repository.updateCcmsSyncResult(id, false, "actor-oid", expectedTimestamp)).thenReturn(0);

        assertThat(service.recordResult(id, false, "actor-oid")).isFalse();

        verify(repository).updateCcmsSyncResult(id, false, "actor-oid", expectedTimestamp);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(mapper);
    }
}
