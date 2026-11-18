package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.mapper.AppCatalogueQueryMapper;
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
    private final AppCatalogueQueryMapper mapper;

    public AppCatalogueQueryService(AppCatalogueQueryRepository repository, AppCatalogueQueryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public AppViewList getApps() {
        return new AppViewList(repository.findActiveApps(limit(MAX_APPS)));
    }

    public AppView getApp(UUID appId) {
        return repository.findActiveAppById(appId).orElseThrow(() -> new AppNotFoundException(appId));
    }

    /**
     * The cap is applied after the user type filter, so a matching role is never dropped because roles of the other
     * user type precede it. The applications' roles are guaranteed not to exceed the cap, so reading them all is safe.
     */
    public AppRoleViewList getAppRoles(UUID appId, UserType userType) {
        if (!repository.existsByIdAndEnabledTrue(appId)) {
            throw new AppNotFoundException(appId);
        }
        return new AppRoleViewList(mapper.toAppRoleViews(repository.findRolesOfActiveApp(appId), userType).stream()
                .limit(MAX_APP_ROLES)
                .toList());
    }

    public AppRoleView getAppRole(UUID appRoleId) {
        return mapper.toAppRoleView(repository.findRoleOfActiveAppById(appRoleId))
                .orElseThrow(() -> new AppRoleNotFoundException(appRoleId));
    }

    private Pageable limit(int max) {
        return PageRequest.ofSize(max);
    }
}
