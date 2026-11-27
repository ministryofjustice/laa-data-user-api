package uk.gov.justice.laa.datauserapi.application.query.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import uk.gov.justice.laa.datauserapi.exception.InvalidRequestException;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialRoleViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.ProfileTrialQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

@Component
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProfileTrialReadMapper {
    private final ProfileTrialQueryRepository repository;
    private final ProfileTrialRoleViewMapper roleMapper;
    private final EntityManager entityManager;

    public ProfileTrialReadMapper(ProfileTrialQueryRepository repository, ProfileTrialRoleViewMapper roleMapper,
                                  EntityManager entityManager) {
        this.repository = repository;
        this.roleMapper = roleMapper;
        this.entityManager = entityManager;
    }

    public UserProfileSummaryPage search(ProfileTrialQuery query) {
        entityManager.clear();
        var criteria = query.criteria();
        int number = criteria.pageNumber() == null ? 0 : criteria.pageNumber();
        int size = criteria.pageSize() == null ? 20 : criteria.pageSize();
        Sort sort = Sort.by("id");
        if (criteria.sortBy() != null && !criteria.sortBy().isEmpty()) {
            // Only explicit persistence fields are accepted; never interpolate caller-supplied paths.
            List<Sort.Order> orders = new ArrayList<>();
            for (String value : criteria.sortBy()) {
                String[] parts = value.split(",", -1);
                if (!Set.of("id", "createdDate", "userType").contains(parts[0]) || parts.length > 2
                        || (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase(Locale.ROOT)))) {
                    throw new InvalidRequestException(
                            InvalidRequestException.Reason.INVALID_PROFILE_SORT,
                            "Unsupported profile sort");
                }
                orders.add(new Sort.Order(parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC, parts[0]));
            }
            sort = Sort.by(orders).and(sort);
        }
        var page = repository.findAll(specification(query), PageRequest.of(number, size, sort));
        Set<UUID> ids = page.stream().map(UserProfile::getId).collect(Collectors.toSet());
        var profiles = ids.isEmpty() ? List.<UserProfile>of() : repository.findDetails(ids);
        var byId = profiles.stream().collect(Collectors.toMap(UserProfile::getId, profile -> profile));
        List<UserProfileSummaryView> items = page.stream().map(value -> summary(byId.get(value.getId()))).toList();
        return new UserProfileSummaryPage(items, new PageMetadata(number, size, page.getTotalElements(), page.getTotalPages()));
    }

    public UserProfileDetailView detail(ProfileTrialQuery query) {
        entityManager.clear();
        var target = query.plan().input().target();
        UserProfile profile = repository.findDetails(Set.of(target.id())).stream()
                .filter(p -> p.getUserType() == target.userType())
                .filter(p -> Objects.equals(p.getFirm() == null ? null : p.getFirm().getId(), target.firmId()))
                .findFirst().orElseThrow(() -> new UserProfileNotFoundException(target.id()));
        repository.findOffices(Set.of(target.id()));
        var user = profile.getEntraUser();
        return new UserProfileDetailView(profile.getId(), profile.isActiveProfile(), profile.getUserType(),
                user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                profile.getFirm() == null ? "" : profile.getFirm().getName(),
                profile.getFirm() == null ? "" : profile.getFirm().getId().toString(),
                user.isMultiFirmUser(), user.getUserAccountStatus(), profile.getSilasStatus(), !profile.getAppRoles().isEmpty(),
                UUID.fromString(user.getEntraOid()), profile.isUnrestrictedOfficeAccess(), profile.isLastCcmsSyncSuccessful(),
                profile.getAppRoles().stream().sorted(Comparator.comparing(role -> role.getId()))
                        .map(roleMapper::toView).toList(),
                profile.getOffices().stream().sorted(Comparator.comparing(office -> office.getId()))
                        .map(ProfileTrialReadMapper::officeView).toList());
    }

    private static OfficeView officeView(Office office) {
        var address = office.getAddress();
        return new OfficeView(office.getId().toString(), office.getFirm() == null ? null : office.getFirm().getId().toString(),
                address == null ? null : address.getPostcode(), address == null ? null : address.getAddressLine1(),
                address == null ? null : address.getAddressLine2(), address == null ? null : address.getAddressLine3(),
                address == null ? null : address.getCity());
    }

    private Specification<UserProfile> specification(ProfileTrialQuery query) {
        return (root, criteriaQuery, builder) -> {
            var filters = query.plan().decision().queryFilters();
            var criteria = query.criteria();
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("userType").in(filters.userTypes()));
            if (filters.firmId() != null) {
                predicates.add(builder.equal(root.get("firm").get("id"), filters.firmId()));
            }
            if (criteria.firmId() != null) {
                predicates.add(builder.equal(root.get("firm").get("id"), criteria.firmId()));
            }
            if (criteria.userType() != null) {
                predicates.add(builder.equal(root.get("userType"), criteria.userType()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("silasStatus"), criteria.status()));
            }
            if (criteria.user() != null && !criteria.user().isBlank()) {
                var user = root.join("entraUser");
                String pattern = "%" + escape(criteria.user().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(builder.like(builder.lower(user.get("email")), pattern, '\\'),
                        builder.like(builder.lower(builder.concat(builder.concat(user.get("firstName"), " "), user.get("lastName"))), pattern, '\\')));
            }
            if (criteria.firm() != null && !criteria.firm().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("firm").get("name")),
                        "%" + escape(criteria.firm().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (Boolean.TRUE.equals(criteria.multiFirm())) {
                predicates.add(builder.isTrue(root.get("entraUser").get("multiFirmUser")));
            }
            if (criteria.appId() != null || criteria.appRoleId() != null) {
                var role = root.join("appRoles");
                if (criteria.appId() != null) {
                    predicates.add(builder.equal(role.get("app").get("entraAppId"), criteria.appId().toString()));
                }
                if (criteria.appRoleId() != null) {
                    predicates.add(builder.equal(role.get("id"), criteria.appRoleId()));
                }
                criteriaQuery.distinct(true);
            }
            if (Boolean.TRUE.equals(criteria.firmAdmin())) {
                var adminRole = root.join("appRoles");
                predicates.add(builder.isTrue(adminRole.get("authzRole")));
                predicates.add(builder.equal(adminRole.get("name"),
                        uk.gov.justice.laa.datauserapi.model.AuthzRole.FIRM_USER_MANAGER.getRoleName()));
                criteriaQuery.distinct(true);
            }
            if (criteria.officeId() != null) {
                predicates.add(builder.equal(root.join("offices").get("code"), criteria.officeId()));
                criteriaQuery.distinct(true);
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UserProfileSummaryView summary(UserProfile p) {
        var u = p.getEntraUser();
        return new UserProfileSummaryView(p.getId(), p.isActiveProfile(), p.getUserType(), u.getEmail(),
                u.getFirstName() + " " + u.getLastName(), p.getFirm() == null ? "" : p.getFirm().getName(),
                p.getFirm() == null ? "" : p.getFirm().getId().toString(), u.isMultiFirmUser(), u.getUserAccountStatus(),
                p.getSilasStatus(), !p.getAppRoles().isEmpty());
    }
}
