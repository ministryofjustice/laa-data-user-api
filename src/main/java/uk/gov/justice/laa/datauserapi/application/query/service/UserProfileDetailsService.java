package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserProfileDetailsService {

    private final UserProfileQueryRepository repository;
    private final UserProfileQueryMapper queryMapper;
    private final GetActiveProfileQueryMapper viewMapper;

    public UserProfileDetailsService(UserProfileQueryRepository repository,
                                     UserProfileQueryMapper queryMapper,
                                     GetActiveProfileQueryMapper viewMapper) {
        this.repository = repository;
        this.queryMapper = queryMapper;
        this.viewMapper = viewMapper;
    }

    public UserProfileDetailView getActiveProfile(String userEntraObjectId) {
        UserProfileDto targetProfile = queryMapper.toActiveProfile(
                repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId), userEntraObjectId);
        return viewMapper.mapProfileToDetailView(targetProfile);
    }

    public List<UserProfileSummaryView> getProfiles(String userEntraObjectId) {
        return repository.findAllProfilesByUserEntraObjectId(userEntraObjectId).orElse(List.of());
    }
}
