package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.FirmDirectoryActorQueryRepository;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;

/**
 * Authorization rules for the temporary Provider Data (firm/office) query endpoints.
 */
@Service
@Transactional(readOnly = true)
public class FirmDirectoryAuthorizationService {

    private final FirmDirectoryActorQueryRepository actorQueryRepository;

    public FirmDirectoryAuthorizationService(FirmDirectoryActorQueryRepository actorQueryRepository) {
        this.actorQueryRepository = actorQueryRepository;
    }

    /**
     * Only internal users holding {@link Permission#VIEW_FIRM_DIRECTORY} may access the firm directory.
     */
    public void requireFirmDirectoryAccess(UUID entraUserId) {
        UserType userType = getActiveUserType(entraUserId);
        if (userType != UserType.INTERNAL) {
            throw new AccessDeniedException("External users cannot access the firm directory");
        }
        if (!actorQueryRepository.findActivePermissions(entraUserId.toString()).contains(Permission.VIEW_FIRM_DIRECTORY)) {
            throw new AccessDeniedException("Missing VIEW_FIRM_DIRECTORY permission");
        }
    }

    /**
     * Search requires an active profile but not VIEW_FIRM_DIRECTORY. Internal users may search all
     * firms; external users are restricted to their active firm and must have one.
     */
    public FirmSearchAccess resolveSearchAccess(UUID entraUserId) {
        UserType userType = getActiveUserType(entraUserId);
        if (userType == UserType.INTERNAL) {
            return new FirmSearchAccess(true, null);
        }
        String firmCode = actorQueryRepository.findActiveFirmCode(entraUserId.toString())
                .orElseThrow(() -> new AccessDeniedException("External user has no active firm"));
        return new FirmSearchAccess(false, firmCode);
    }

    private UserType getActiveUserType(UUID entraUserId) {
        return actorQueryRepository.findActiveUserType(entraUserId.toString())
                .orElseThrow(() -> new AccessDeniedException("No active user profile found"));
    }
}
