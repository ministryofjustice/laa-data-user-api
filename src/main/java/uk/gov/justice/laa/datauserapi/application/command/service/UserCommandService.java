package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

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

    @Transactional(readOnly = true)
    public boolean isMultiFirmUser(UUID userEntraObjectId) {
        return findEntraUser(userEntraObjectId).isMultiFirmUser();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findActiveFirmId(UUID userEntraObjectId) {
        EntraUser entraUser = findEntraUser(userEntraObjectId);
        return Optional.ofNullable(entraUser.getUserProfiles()).stream()
                .flatMap(Collection::stream)
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .map(UserProfile::getFirm)
                .map(Firm::getId);
    }

    private EntraUser findEntraUser(UUID userEntraObjectId) {
        return entraUserCommandRepository.findByEntraOid(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found for oid: " + userEntraObjectId));
    }

}
