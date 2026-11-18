package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@SpringJUnitConfig(AppCatalogueQueryRepositoryTest.Config.class)
@Testcontainers
class AppCatalogueQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID APP_A = new UUID(0, 20);
    private static final UUID APP_B = new UUID(0, 21);
    private static final UUID APP_INACTIVE = new UUID(0, 22);
    private static final UUID CLIENT_A = new UUID(1, 20);
    private static final UUID CLIENT_B = new UUID(1, 21);
    private static final UUID ROLE_A1 = new UUID(0, 40);
    private static final UUID ROLE_A2 = new UUID(0, 41);
    private static final UUID ROLE_B = new UUID(0, 42);
    private static final UUID ROLE_INACTIVE = new UUID(0, 43);
    private static final UUID APP_EMPTY = new UUID(0, 23);

    @Autowired
    private AppCatalogueQueryRepository appRepository;
    @Autowired
    private RoleAssignmentRestrictionQueryRepository restrictionRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE role_assignment, app, app_role CASCADE");
        insertApp(APP_B, "App B", 2, true, CLIENT_B);
        insertApp(APP_A, "App A", 1, true, CLIENT_A);
        insertApp(APP_INACTIVE, "App Inactive", 3, false, new UUID(1, 22));
        insertApp(APP_EMPTY, "App Empty", 4, true, new UUID(1, 23));

        insertRole(ROLE_A2, APP_A, "A role 2", 2);
        insertRole(ROLE_A1, APP_A, "A role 1", 1);
        insertRole(ROLE_B, APP_B, "B role", 1);
        insertRole(ROLE_INACTIVE, APP_INACTIVE, "Inactive role", 1);
    }

    @Test
    void activeAppsExcludeInactiveOnesAndAreOrderedWithBothIdentifiers() {
        List<AppView> apps = appRepository.findActiveApps(PageRequest.ofSize(100));

        assertThat(apps).extracting(AppView::name).containsExactly("App A", "App B", "App Empty");
        assertThat(apps.getFirst().appId()).isEqualTo(APP_A.toString());
        assertThat(apps.getFirst().appEntraClientId()).isEqualTo(CLIENT_A.toString());
        assertThat(apps.getFirst().appEntraObjectId()).isEqualTo(APP_A.toString());
        assertThat(apps.getFirst().active()).isTrue();
    }

    @Test
    void activeAppsAreCapped() {
        assertThat(appRepository.findActiveApps(PageRequest.ofSize(2))).hasSize(2);
    }

    @Test
    void appIsFoundByItsInternalIdAndNotByItsEntraClientId() {
        assertThat(appRepository.findActiveAppById(APP_B)).get().extracting(AppView::name).isEqualTo("App B");
        assertThat(appRepository.findActiveAppById(CLIENT_B)).isEmpty();
        assertThat(appRepository.existsByIdAndEnabledTrue(APP_B)).isTrue();
        assertThat(appRepository.existsByIdAndEnabledTrue(CLIENT_B)).isFalse();
    }

    @Test
    void inactiveOrUnknownAppsAreNotFound() {
        assertThat(appRepository.findActiveAppById(APP_INACTIVE)).isEmpty();
        assertThat(appRepository.findActiveAppById(UUID.randomUUID())).isEmpty();
        assertThat(appRepository.existsByIdAndEnabledTrue(APP_INACTIVE)).isFalse();
    }

    @Test
    void rolesOfAnActiveAppAreOrderedAndLoadedWithTheirApp() {
        List<AppRole> roles = rolesOf(APP_A, null);

        assertThat(roles).extracting(AppRole::getName).containsExactly("A role 1", "A role 2");
        assertThat(roles.getFirst().getApp().getEntraAppId()).isEqualTo(CLIENT_A.toString());
    }

    @Test
    void rolesOfAnInactiveAppAreExcludedAndAnActiveAppWithoutRolesIsEmpty() {
        assertThat(rolesOf(APP_INACTIVE, null)).isEmpty();
        assertThat(rolesOf(APP_EMPTY, null)).isEmpty();
        assertThat(rolesOf(CLIENT_A, null)).isEmpty();
    }

    @Test
    void userTypeFilterKeepsMatchingAndBothRolesAndIsTheSameRuleAsTheMapper() {
        String[] restrictions = {null, "{}", "{INTERNAL}", "{EXTERNAL}", "{INTERNAL,EXTERNAL}"};
        for (int i = 0; i < restrictions.length; i++) {
            UUID id = new UUID(0, 100 + i);
            insertRole(id, APP_EMPTY, "r" + i, i);
            jdbc.update("UPDATE app_role SET user_type_restriction = ?::text[] WHERE id = ?", restrictions[i], id);
        }

        assertThat(rolesOf(APP_EMPTY, null)).extracting(AppRole::getName).containsExactly("r0", "r1", "r2", "r3", "r4");
        assertThat(rolesOf(APP_EMPTY, UserType.INTERNAL)).extracting(AppRole::getName)
                .containsExactly("r0", "r1", "r2", "r4");
        assertThat(rolesOf(APP_EMPTY, UserType.EXTERNAL)).extracting(AppRole::getName)
                .containsExactly("r0", "r1", "r3", "r4");
        for (UserType type : UserType.values()) {
            List<String> mapperRule = new UserProfileAccessQueryMapper().toAppRoleViews(rolesOf(APP_EMPTY, null))
                    .stream()
                    .filter(view -> List.of("BOTH", type.name()).contains(view.appRoleUserTypeRestriction()))
                    .map(AppRoleView::name)
                    .toList();
            assertThat(rolesOf(APP_EMPTY, type)).extracting(AppRole::getName).containsExactlyElementsOf(mapperRule);
        }
    }

    @Test
    void rolesAreCappedAfterTheUserTypeFilter() {
        insertRole(new UUID(0, 100), APP_EMPTY, "internal", 1);
        insertRole(new UUID(0, 101), APP_EMPTY, "external", 2);
        jdbc.update("UPDATE app_role SET user_type_restriction = '{INTERNAL}' WHERE id = ?", new UUID(0, 100));
        jdbc.update("UPDATE app_role SET user_type_restriction = '{EXTERNAL}' WHERE id = ?", new UUID(0, 101));

        assertThat(appRepository.findRolesOfActiveApp(APP_EMPTY, "EXTERNAL", "INTERNAL",
                PageRequest.ofSize(1))).extracting(AppRole::getName).containsExactly("external");
    }

    private List<AppRole> rolesOf(UUID appId, UserType userType) {
        String other = userType == null ? null : userType == UserType.INTERNAL ? "EXTERNAL" : "INTERNAL";
        return appRepository.findRolesOfActiveApp(
                appId, userType == null ? null : userType.name(), other, PageRequest.ofSize(500));
    }

    @Test
    void roleIsFoundByItsLocalIdOnlyWhenItsAppIsActive() {
        assertThat(appRepository.findRoleOfActiveAppById(ROLE_B)).get()
                .extracting(AppRole::getName).isEqualTo("B role");
        assertThat(appRepository.findRoleOfActiveAppById(ROLE_INACTIVE)).isEmpty();
        assertThat(appRepository.findRoleOfActiveAppById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void restrictionsDescribeTheRoleBeingAssignedAndTheAssigningRole() {
        assign(ROLE_A1, ROLE_B);

        List<RoleAssignmentRestrictionView> items =
                restrictionRepository.findEligibleRestrictions(null, PageRequest.of(0, 20)).getContent();

        assertThat(items).containsExactly(new RoleAssignmentRestrictionView(
                ROLE_B.toString(), "B role", APP_B.toString(), CLIENT_B.toString(), "App B",
                ROLE_A1.toString(), "A role 1"));
    }

    @Test
    void appFilterMatchesTheAppOfTheRoleBeingAssignedNotTheAssigningRole() {
        assign(ROLE_A1, ROLE_B);

        assertThat(restrictionRepository.findEligibleRestrictions(APP_B, PageRequest.of(0, 20)).getContent())
                .hasSize(1);
        assertThat(restrictionRepository.findEligibleRestrictions(APP_A, PageRequest.of(0, 20)).getContent())
                .isEmpty();
        assertThat(restrictionRepository.findEligibleRestrictions(CLIENT_B, PageRequest.of(0, 20)).getContent())
                .isEmpty();
    }

    @Test
    void restrictionsAreExcludedWhenEitherApplicationIsInactive() {
        assign(ROLE_A1, ROLE_INACTIVE);
        assign(ROLE_INACTIVE, ROLE_A2);
        assign(ROLE_A1, ROLE_A2);

        List<RoleAssignmentRestrictionView> items =
                restrictionRepository.findEligibleRestrictions(null, PageRequest.of(0, 20)).getContent();

        assertThat(items).extracting(RoleAssignmentRestrictionView::appRoleName).containsExactly("A role 2");
        assertThat(items).extracting(RoleAssignmentRestrictionView::assigningAppRoleName)
                .containsExactly("A role 1");
    }

    @Test
    void restrictionsArePagedWithMetadataAndPagesBeyondTheEndAreEmpty() {
        assign(ROLE_A1, ROLE_A2);
        assign(ROLE_A1, ROLE_B);
        assign(ROLE_A2, ROLE_B);

        Page<RoleAssignmentRestrictionView> first =
                restrictionRepository.findEligibleRestrictions(null, PageRequest.of(0, 2));
        Page<RoleAssignmentRestrictionView> second =
                restrictionRepository.findEligibleRestrictions(null, PageRequest.of(1, 2));
        Page<RoleAssignmentRestrictionView> beyond =
                restrictionRepository.findEligibleRestrictions(null, PageRequest.of(5, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(1);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
    }

    private void assign(UUID assigningRole, UUID assignableRole) {
        jdbc.update("INSERT INTO role_assignment (assigning_role_id, assignable_role_id) VALUES (?, ?)",
                assigningRole, assignableRole);
    }

    private void insertApp(UUID id, String name, int ordinal, boolean enabled, UUID clientId) {
        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid,
                security_group_oid)
            VALUES (?, ?, ?, 'LAA', ?, ?, ?, ?, ?, ?)
            """, id, name, name + " description", ordinal, "https://" + id, enabled, clientId.toString(),
                id.toString(), id.toString());
    }

    private void insertRole(UUID id, UUID appId, String name, int ordinal) {
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, ?, ?, ?, ?, FALSE, FALSE, ?)
            """, id, name, name, ordinal, appId, "ROLE" + ordinal);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AppCatalogueQueryRepository.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(POSTGRES.getJdbcUrl());
            dataSource.setUser(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(EntraUser.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
