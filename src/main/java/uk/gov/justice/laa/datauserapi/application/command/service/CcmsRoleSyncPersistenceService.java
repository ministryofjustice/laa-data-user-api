package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.application.command.mapper.CcmsSyncProfileMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CcmsRoleSyncPersistenceService {
    private final UserProfileCommandRepository repository;
    private final CcmsSyncProfileMapper mapper;
    private final Clock clock;

    public CcmsRoleSyncPersistenceService(UserProfileCommandRepository repository, CcmsSyncProfileMapper mapper,
                                         @Qualifier("ccmsRoleSyncClock") Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CcmsSyncProfile> load(UUID id) {
        return repository.findCcmsSyncProfile(id).map(mapper::toSnapshot);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordResult(UUID id, boolean successful, String actorOid) {
        return repository.updateCcmsSyncResult(id, successful, actorOid, LocalDateTime.now(clock)) == 1;
    }
}
