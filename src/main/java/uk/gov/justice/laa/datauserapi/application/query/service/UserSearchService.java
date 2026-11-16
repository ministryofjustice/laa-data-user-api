package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;


import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;
    private final QueryAuthorisationService authorisationService;

    public UserSearchService(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper, QueryAuthorisationService authorisationService) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
        this.authorisationService = authorisationService;
    }

    public UserAccountSummaryPage search(SearchUsersQuery query) {
        EntraUserDto actor = queryService.findByEntraOid(query.actorOid());

        UserProfileDto actorProfile = authorisationService.requireActiveProfile(actor, query.actorOid());

        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        UserSearchCriteria criteria = query.criteria();
        UUID actorFirmId = actorProfile.getUserType() == UserType.EXTERNAL
                ? (actorProfile.getFirm() == null ? null : actorProfile.getFirm().getId()) : null;

        UserSearchCriteria enrichedCriteria = new UserSearchCriteria(criteria.pageNumber(), criteria.pageSize(),
                criteria.sortBy(), criteria.user(), criteria.emailQuery(), criteria.firm(), criteria.firmId(), criteria.appId(),
                criteria.appEntraClientId(), criteria.appRoleId(), criteria.userType(), criteria.userAccountStatus(),
                criteria.neverActivatedFilter(), actorProfile.getUserType() == UserType.INTERNAL,
                actorProfile.getUserType() == UserType.EXTERNAL, actorFirmId, canViewExternalUsers);

        int pageNumber = enrichedCriteria.pageNumber() == null ? 0 : enrichedCriteria.pageNumber();
        int pageSize = enrichedCriteria.pageSize() == null ? 20 : enrichedCriteria.pageSize();
        pageSize = Math.min(pageSize, 100);

        Pageable pageable = PageRequest.of(pageNumber, pageSize, toSort(enrichedCriteria.sortBy()));
        Page<EntraUserDto> page = queryService.searchUsers(enrichedCriteria, pageable);

        return userViewMapper.mapToSummaryPage(page);
    }

    private Sort toSort(List<String> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return Sort.unsorted();
        }

        if (sortValues.size() == 2
                && !sortValues.get(0).contains(",")
                && ("asc".equalsIgnoreCase(sortValues.get(1))
                || "desc".equalsIgnoreCase(sortValues.get(1)))) {

            sortValues = List.of(
                    sortValues.get(0) + "," + sortValues.get(1));
        }

        return Sort.by(
                sortValues.stream()
                        .map(sortValue -> {
                            String[] parts = sortValue.split(",");

                            Sort.Direction direction =
                                    parts.length > 1
                                            ? Sort.Direction.fromString(parts[1])
                                            : Sort.Direction.ASC;

                            return new Sort.Order(direction, parts[0]);
                        })
                        .toList()
        );
    }
}
