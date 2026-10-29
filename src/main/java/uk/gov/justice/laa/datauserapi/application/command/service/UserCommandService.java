package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

@Service
public class UserCommandService {

    private final EntraUserCommandRepository entraUserCommandRepository;

    private final UserProfileCommandRepository userProfileCommandRepository;

    public UserCommandService(EntraUserCommandRepository entraUserCommandRepository,
                              UserProfileCommandRepository userProfileCommandRepository) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.userProfileCommandRepository = userProfileCommandRepository;
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

    @Transactional(readOnly = true)
    public boolean isDeactivated(UUID userEntraObjectId) {
        return UserAccountStatus.DEACTIVATED.equals(findEntraUser(userEntraObjectId).getUserAccountStatus());
    }

    @Transactional(readOnly = true)
    public Optional<DeactivationType> findDeactivationType(UUID userEntraObjectId) {
        return Optional.ofNullable(findEntraUser(userEntraObjectId).getDeactivationType());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findProfileFirmId(UUID userProfileId) {
        return Optional.ofNullable(findProfile(userProfileId).getFirm()).map(Firm::getId);
    }

    @Transactional(readOnly = true)
    public Optional<UserType> findProfileUserType(UUID userProfileId) {
        return Optional.ofNullable(findProfile(userProfileId).getUserType());
    }

    @Transactional(readOnly = true)
    public Optional<String> findProfileEntraOid(UUID userProfileId) {
        return Optional.ofNullable(findProfile(userProfileId).getEntraUser()).map(EntraUser::getEntraOid);
    }

    private UserProfile findProfile(UUID userProfileId) {
        return userProfileCommandRepository.findById(userProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found with ID: " + userProfileId));
    }

    private EntraUser findEntraUser(UUID userEntraObjectId) {
        return entraUserCommandRepository.findByEntraOid(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found for oid: " + userEntraObjectId));
    }

}
