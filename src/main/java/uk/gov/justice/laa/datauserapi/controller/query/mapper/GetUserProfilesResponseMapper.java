package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedView;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedViewMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.shared.ResponseMapper;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class GetUserProfilesResponseMapper implements ResponseMapper<PagedReadModel<GetUserProfilesReadModel>, ResponseEntity<PagedView<UserProfileSummaryView>>> {

    @Override
    public ResponseEntity<PagedView<UserProfileSummaryView>> map(PagedReadModel<GetUserProfilesReadModel> readModel) {
        Collection<GetUserProfilesReadModel> userProfilesReadModels = readModel.items();
        Collection<UserProfileSummaryView> userProfileSummaryViews = userProfilesReadModels.stream()
                .map(this::mapGetUserProfilesReadModelToUserProfileSummaryView)
                .toList();
        PageMetadata pageMetadata = readModel.page();
        PagedViewMetadata pagedViewMetadata = new PagedViewMetadata(pageMetadata.pageNumber(), pageMetadata.pageSize(), pageMetadata.totalElements(), pageMetadata.totalPages());
        PagedView<UserProfileSummaryView> view = new PagedView<>(userProfileSummaryViews, pagedViewMetadata);
        return ResponseEntity.ok(view);
    }

    private UserProfileSummaryView mapGetUserProfilesReadModelToUserProfileSummaryView(GetUserProfilesReadModel getUserProfilesReadModel) {
        return new UserProfileSummaryView(
                getUserProfilesReadModel.userProfileId(),
                getUserProfilesReadModel.activeProfile(),
                getUserProfilesReadModel.userType(),
                getUserProfilesReadModel.email(),
                getUserProfilesReadModel.fullName(),
                getUserProfilesReadModel.firmName(),
                getUserProfilesReadModel.firmId(),
                getUserProfilesReadModel.multiFirmUser(),
                getUserProfilesReadModel.accountStatus(),
                getUserProfilesReadModel.profileStatus(),
                getUserProfilesReadModel.hasAppRoles()
        );
    }
}
