package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AppCatalogueQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.exception.AppNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.AppRoleNotFoundException;

/**
 * Serves the catalogue of active applications and their roles. Callers are authorised before this service is
 * reached: the query messages declare {@code VIEW_LAA_APP_METADATA}. Inactive applications and their roles are
 * not part of the catalogue.
 */
@Service
@Transactional(readOnly = true)
public class AppCatalogueQueryService {

    static final int MAX_APPS = 100;
    static final int MAX_APP_ROLES = 500;

    private final AppCatalogueQueryRepository repository;
    private final UserProfileAccessQueryMapper roleMapper;

    public AppCatalogueQueryService(AppCatalogueQueryRepository repository, UserProfileAccessQueryMapper roleMapper) {
        this.repository = repository;
        this.roleMapper = roleMapper;
    }

    public AppViewList getApps() {
        return new AppViewList(repository.findActiveApps(PageRequest.ofSize(MAX_APPS)));
    }

    public AppView getApp(UUID appId) {
        return repository.findActiveAppById(appId).orElseThrow(() -> new AppNotFoundException(appId));
    }

    /** Roles available to the user type (see the repository query); all roles if {@code null}. */
    public AppRoleViewList getAppRoles(UUID appId, UserType userType) {
        if (!repository.existsByIdAndEnabledTrue(appId)) {
            throw new AppNotFoundException(appId);
        }
        String type = userType == null ? null : userType.name();
        String otherType = userType == null ? null : opposite(userType).name();
        return new AppRoleViewList(roleMapper.toAppRoleViews(repository.findRolesOfActiveApp(
                appId, type, otherType, PageRequest.ofSize(MAX_APP_ROLES))));
    }

    private static UserType opposite(UserType userType) {
        return userType == UserType.INTERNAL ? UserType.EXTERNAL : UserType.INTERNAL;
    }

    public AppRoleView getAppRole(UUID appRoleId) {
        return roleMapper.toAppRoleViews(repository.findRoleOfActiveAppById(appRoleId).stream().toList()).stream()
                .findFirst()
                .orElseThrow(() -> new AppRoleNotFoundException(appRoleId));
    }
}
