package uk.gov.justice.laa.datauserapi.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.dto.UserDto;
import uk.gov.justice.laa.datauserapi.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.repository.UserProfileRepository;
import uk.gov.justice.laa.datauserapi.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;

    public UserServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public Page<UserDto> findUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<UserProfile> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<UserProfile, EntraUser> entraUserJoin = root.join("entraUser", JoinType.INNER);

            if (criteria.ids() != null && !criteria.ids().isEmpty()) {
                List<UUID> uuidList = criteria.ids().stream().map(UUID::fromString).collect(Collectors.toList());
                predicates.add(entraUserJoin.get("id").in(uuidList));
            }

            if (criteria.oids() != null && !criteria.oids().isEmpty()) {
                predicates.add(entraUserJoin.get("entraOid").in(criteria.oids()));
            }

            if (criteria.emails() != null && !criteria.emails().isEmpty()) {
                predicates.add(entraUserJoin.get("email").in(criteria.emails()));
            }

            if (criteria.profileIds() != null && !criteria.profileIds().isEmpty()) {
                List<UUID> uuidList = criteria.profileIds().stream().map(UUID::fromString).collect(Collectors.toList());
                predicates.add(root.get("id").in(uuidList));
            }

            if (criteria.firms() != null && !criteria.firms().isEmpty()) {
                Join<UserProfile, Firm> firmJoin = root.join("firm", JoinType.INNER);
                List<UUID> uuidList = criteria.firms().stream().map(UUID::fromString).collect(Collectors.toList());
                predicates.add(firmJoin.get("id").in(uuidList));
            }

            if (criteria.offices() != null && !criteria.offices().isEmpty()) {
                Join<UserProfile, Office> officeJoin = root.join("offices", JoinType.INNER);
                List<UUID> uuidList = criteria.offices().stream().map(UUID::fromString).collect(Collectors.toList());
                predicates.add(officeJoin.get("id").in(uuidList));
            }

            if (criteria.roleNames() != null && !criteria.roleNames().isEmpty()) {
                Join<UserProfile, AppRole> roleJoin = root.join("appRoles", JoinType.INNER);
                predicates.add(roleJoin.get("name").in(criteria.roleNames()));
            }

            if (criteria.appNames() != null && !criteria.appNames().isEmpty()) {
                Join<UserProfile, AppRole> roleJoin = root.join("appRoles", JoinType.INNER);
                Join<AppRole, App> appJoin = roleJoin.join("app", JoinType.INNER);
                predicates.add(appJoin.get("name").in(criteria.appNames()));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userProfileRepository.findAll(spec, pageable).map(this::mapToDto);
    }

    private UserDto mapToDto(UserProfile profile) {
        EntraUser eu = profile.getEntraUser();
        Firm firm = profile.getFirm();
        return new UserDto(
                profile.getId().toString(),
                eu != null ? eu.getEntraOid() : null,
                eu != null ? eu.getEmail() : null,
                eu != null ? eu.getFirstName() : null,
                eu != null ? eu.getLastName() : null,
                profile.getUserProfileStatus() != null ? profile.getUserProfileStatus().name() : null,
                firm != null ? firm.getName() : null,
                eu != null && eu.isMultiFirmUser(),
                eu != null ? eu.getLastSyncedOn() : null
        );
    }
    @Override
    public java.util.Optional<uk.gov.justice.laa.datauserapi.dto.UserDetailDto> getUserById(UUID id) {
        return userProfileRepository.findById(id).map(this::mapToDetailDto);
    }

    private uk.gov.justice.laa.datauserapi.dto.UserDetailDto mapToDetailDto(UserProfile profile) {
        EntraUser eu = profile.getEntraUser();
        Firm firm = profile.getFirm();
        
        List<String> roleNames = profile.getAppRoles().stream()
                .map(AppRole::getName)
                .collect(Collectors.toList());
                
        List<String> officeCodes = profile.getOffices().stream()
                .map(Office::getCode)
                .collect(Collectors.toList());

        return new uk.gov.justice.laa.datauserapi.dto.UserDetailDto(
                profile.getId(),
                eu != null ? eu.getEmail() : null,
                eu != null ? eu.getEntraOid() : null,
                eu != null && eu.isMultiFirmUser(),
                profile.getUserProfileStatus(),
                profile.getSilasStatus(),
                eu != null ? eu.getFirstName() : null,
                eu != null ? eu.getLastName() : null,
                firm != null ? firm.getName() : null,
                roleNames,
                officeCodes
        );
    }
}
