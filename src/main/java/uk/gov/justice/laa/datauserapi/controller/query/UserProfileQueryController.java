package uk.gov.justice.laa.datauserapi.controller.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfileByIdQueryMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfileByIdResponseMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesQueryMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesResponseMapper;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequiresReadScope
@RequiredArgsConstructor
@RequestMapping("/api/v1/queries")
public class UserProfileQueryController {

    // Get User Profiles
    private final GetUserProfilesHandler getUserProfilesHandler;
    private final GetUserProfilesQueryMapper getUserProfilesQueryMapper;
    private final GetUserProfilesResponseMapper getUserProfilesResponseMapper;

    // Get User Profile By Id
    private final GetUserProfileByIdHandler getUserProfileByIdHandler;
    private final GetUserProfileByIdQueryMapper getUserProfileByIdQueryMapper;
    private final GetUserProfileByIdResponseMapper getUserProfileByIdResponseMapper;

    @GetMapping("/user-profiles")
    public ResponseEntity<PagedView<UserProfileSummaryView>> getUserProfiles(
            @ModelAttribute GetUserProfilesRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        GetUserProfilesQuery query = getUserProfilesQueryMapper.map(request, authenticatedUser);
        PagedReadModel<GetUserProfilesReadModel> result = getUserProfilesHandler.handle(query);
        return getUserProfilesResponseMapper.map(result);
    }

    @GetMapping("/user-profiles/{userProfileId}")
    public ResponseEntity<UserProfileDetailView> getUserProfileById(@PathVariable String userProfileId,
                                                                    @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        GetUserProfileByIdQuery query = getUserProfileByIdQueryMapper.map(userProfileId, authenticatedUser);
        GetUserProfileByIdReadModel readModel = getUserProfileByIdHandler.handle(query);
        return getUserProfileByIdResponseMapper.map(readModel);
    }
}
