package uk.gov.justice.laa.datauserapi.application.query.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;


import java.util.List;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
@Slf4j
public class SearchUsersHandler {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;

    public SearchUsersHandler(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
    }

    public UserAccountSummaryPage handle(SearchUsersQuery query) {
        EntraUserDto actor = queryService.findByEntraOid(query.actorOid());

        UserProfile actorProfile = actor.getUserProfiles().stream()
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException(
                        "Actor has no active profile: " + query.actorOid()));

        if (actorProfile.getUserType() == null) {
            throw new InvalidActorContextException(
                    "Actor user type cannot be determined");
        }

        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        UserSearchCriteria criteria = query.criteria();
        UUID actorFirmId = actorProfile.getUserType() == UserType.EXTERNAL
                ? (actorProfile.getFirm() == null ? null : actorProfile.getFirm().getId()) : null;

        UserSearchCriteria enrichedCriteria = new UserSearchCriteria(criteria.pageNumber(), criteria.pageSize(),
                criteria.sortBy(), criteria.user(), criteria.firm(), criteria.firmId(), criteria.appId(),
                criteria.appRoleId(), criteria.userType(), criteria.userAccountStatus(), criteria.neverActivatedFilter(),
                actorProfile.getUserType() == UserType.INTERNAL, actorProfile.getUserType() == UserType.EXTERNAL,
                actorFirmId, canViewExternalUsers);

        int pageNumber = enrichedCriteria.pageNumber() == null ? 0 : enrichedCriteria.pageNumber();
        int pageSize = enrichedCriteria.pageSize() == null ? 20 : enrichedCriteria.pageSize();
        pageSize = Math.min(pageSize, 100);

        Pageable pageable = PageRequest.of(pageNumber, pageSize, toSort(enrichedCriteria.sortBy()));
        Page<EntraUserDto> page = queryService.searchUsers(enrichedCriteria, pageable);

        List<UserAccountSummaryView> items = page.getContent().stream()
                .map(userViewMapper::mapToUserAccountSummaryView)
                .toList();

        return new UserAccountSummaryPage(
                items,
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }

    private Sort toSort(List<String> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return Sort.unsorted();
        }
        return Sort.by(sortValues.stream().map(sortValue -> {
            String[] parts = sortValue.split(",");
            Sort.Direction direction = parts.length > 1
                    ? Sort.Direction.fromString(parts[1])
                    : Sort.Direction.ASC;
            return new Sort.Order(direction, parts[0]);
        }).toList());
    }
}
