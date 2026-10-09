package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.UserType;

@Service
public class UserCommandService {

    private final EntraUserCommandRepository entraUserCommandRepository;

    public UserCommandService(EntraUserCommandRepository entraUserCommandRepository) {
        this.entraUserCommandRepository = entraUserCommandRepository;
    }

    public boolean isExternalUser(UUID entraUserId) {
        UserType userType = getUserType(entraUserId);
        return UserType.EXTERNAL.equals(userType);
    }

    public boolean isInternalUser(UUID entraUserId) {
        UserType userType = getUserType(entraUserId);
        return UserType.INTERNAL.equals(userType);
    }

    private UserType getUserType(UUID entraUserId) {
        EntraUser entraUser = entraUserCommandRepository.findById(entraUserId)
                .orElseThrow(() -> new RuntimeException("Entra user not found for id: " + entraUserId));
        return entraUser.getUserProfiles().stream().map(UserProfile::getUserType).findFirst().orElse(null);
    }

    public UserProfile getActiveUserProfile(UUID entraUserId) {
        EntraUser entraUser = entraUserCommandRepository.findById(entraUserId)
                .orElseThrow(() -> new RuntimeException("Entra user not found for id: " + entraUserId));
        return entraUser.getUserProfiles().stream().filter(UserProfile::isActiveProfile).findFirst()
                .orElseThrow(() -> new RuntimeException("Active profile not found for user id: " + entraUserId));
    }

}
