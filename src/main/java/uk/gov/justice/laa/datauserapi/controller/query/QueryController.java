package uk.gov.justice.laa.datauserapi.controller.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.contracts.response.PagedView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesQueryMapper;
import uk.gov.justice.laa.datauserapi.controller.query.mapper.GetUserProfilesResponseMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queries")
public class QueryController {

    private final GetUserProfilesHandler getUserProfilesHandler;
    private final GetUserProfilesQueryMapper getUserProfilesQueryMapper;
    private final GetUserProfilesResponseMapper getUserProfilesResponseMapper;

    @GetMapping("/user-profiles")
    public ResponseEntity<PagedView<UserProfileSummaryView>> getUserProfiles(
            @ModelAttribute GetUserProfilesRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        GetUserProfilesQuery query = getUserProfilesQueryMapper.map(request);
        PagedReadModel<GetUserProfilesReadModel> result = getUserProfilesHandler.handle(query);
        PagedView<UserProfileSummaryView> response = getUserProfilesResponseMapper.map(result);
        return ResponseEntity.ok(response);
    }
}
