package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserProfileDetailsService {

    private final UserProfileQueryRepository repository;
    private final QueryAuthorisationService authorisationService;
    private final UserProfileQueryMapper queryMapper;
    private final GetActiveProfileQueryMapper viewMapper;

    public UserProfileDetailsService(UserProfileQueryRepository repository,
                                     QueryAuthorisationService authorisationService,
                                     UserProfileQueryMapper queryMapper,
                                     GetActiveProfileQueryMapper viewMapper) {
        this.repository = repository;
        this.authorisationService = authorisationService;
        this.queryMapper = queryMapper;
        this.viewMapper = viewMapper;
    }

    public UserProfileDetailView getActiveProfile(String userEntraObjectId, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = authorisationService.requireActiveProfile(actor);
        UserProfileDto targetProfile = queryMapper.toActiveProfile(
                repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId), userEntraObjectId);
        String targetFirmId = targetProfile.getFirm() != null ? targetProfile.getFirm().getId().toString() : null;
        if (!authorisationService.canViewProfile(actorProfile, targetProfile.getUserType(), targetFirmId)) {
            throw new ResourceNotFoundException("Active profile not found for user: " + userEntraObjectId);
        }
        return viewMapper.mapProfileToDetailView(targetProfile);
    }

    public List<UserProfileSummaryView> getProfiles(String userEntraObjectId, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = authorisationService.requireActiveProfile(actor);
        return repository.findAllProfilesByUserEntraObjectId(userEntraObjectId).orElse(List.of()).stream()
                .filter(profile -> authorisationService.canViewProfile(
                        actorProfile, profile.userType(), profile.firmId()))
                .collect(Collectors.toList());
    }
}
