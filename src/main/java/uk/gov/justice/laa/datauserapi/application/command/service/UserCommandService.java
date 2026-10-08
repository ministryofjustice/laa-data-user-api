package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.UserType;


import java.util.UUID;

@Service
public class UserCommandService {

    private final EntraUserCommandRepository entraUserCommandRepository;

    public UserCommandService(EntraUserCommandRepository entraUserCommandRepository) {
        this.entraUserCommandRepository = entraUserCommandRepository;
    }

    public boolean isExternalUser(UUID userEntraObjectId) {
        UserType userType = getUserType(userEntraObjectId);
        return UserType.EXTERNAL.equals(userType);
    }

    public boolean isInternalUser(UUID userEntraObjectId) {
        UserType userType = getUserType(userEntraObjectId);
        return UserType.INTERNAL.equals(userType);
    }

    private UserType getUserType(UUID userEntraObjectId) {
        EntraUser entraUser = entraUserCommandRepository.findByEntraOid(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new RuntimeException("Entra user not found for oid: " + userEntraObjectId));
        return entraUser.getUserProfiles().stream().map(UserProfile::getUserType).findFirst().orElse(null);
    }

    public UserProfile getActiveUserProfile(UUID userEntraObjectId) {
        EntraUser entraUser = entraUserCommandRepository.findByEntraOid(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found for oid: " + userEntraObjectId));
        return entraUser.getUserProfiles().stream().filter(UserProfile::isActiveProfile).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Active profile not found for user oid: " + userEntraObjectId));
    }

}
