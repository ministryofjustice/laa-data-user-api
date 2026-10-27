package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.TriggerCcmsRoleSyncResult;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

@Service
@RequiredArgsConstructor
public class CcmsRoleSyncService {

    private final UserProfileCommandRepository repository;
    private final CcmsRoleSyncProfileService profileService;

    public TriggerCcmsRoleSyncResult sync(String actorOid) {
        var profiles = repository.findUserProfilesForCcmsSync();
        int processed = 0;
        int successful = 0;
        CcmsSyncException failure = null;
        for (var profile : profiles) {
            var result = profileService.syncProfile(profile.getId(), actorOid);
            if (!result.processed()) {
                continue;
            }
            processed++;
            if (result.failure() == null) {
                successful++;
            } else {
                failure = CcmsRoleSyncProfileService.preferFailure(failure, result.failure());
            }
        }
        if (failure != null) {
            throw new CcmsSyncException(failure.getStatus(),
                    "CCMS role sync completed: " + successful + " profiles succeeded; "
                            + (processed - successful) + " profiles failed and remain eligible for retry.",
                    failure);
        }
        return new TriggerCcmsRoleSyncResult(true, processed);
    }
}
