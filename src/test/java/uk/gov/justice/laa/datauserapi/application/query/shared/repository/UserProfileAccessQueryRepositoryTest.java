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
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@SpringJUnitConfig(UserProfileAccessQueryRepositoryTest.Config.class)
@Testcontainers
class UserProfileAccessQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final UUID PROFILE_ID = new UUID(0, 2);
    private static final UUID OTHER_PROFILE_ID = new UUID(0, 3);
    private static final UUID UNRESTRICTED_PROFILE_ID = new UUID(0, 4);
    private static final UUID FIRMLESS_PROFILE_ID = new UUID(0, 5);
    private static final UUID FIRM_ID = new UUID(0, 10);
    private static final UUID OTHER_FIRM_ID = new UUID(0, 11);
    private static final UUID APP_A = new UUID(0, 20);
    private static final UUID APP_B = new UUID(0, 21);
    private static final UUID APP_INACTIVE = new UUID(0, 22);
    private static final UUID APP_OF_OTHER_PROFILE = new UUID(0, 23);
    private static final UUID CLIENT_A = new UUID(1, 20);
    private static final UUID CLIENT_B = new UUID(1, 21);
    private static final UUID OFFICE_1 = new UUID(0, 30);
    private static final UUID OFFICE_2 = new UUID(0, 31);
    private static final UUID OFFICE_OTHER_FIRM = new UUID(0, 32);

    @Autowired
    private UserProfileAccessQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE entra_user, user_profile, firm, app, app_role, office CASCADE");
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Firm', 'F1', 'LEGAL_SERVICES_PROVIDER', TRUE)", FIRM_ID);
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Other', 'F2', 'LEGAL_SERVICES_PROVIDER', TRUE)", OTHER_FIRM_ID);
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
            VALUES (?, ?, 'First', 'Last', 'user@example.test', 'ACTIVE', TRUE, 'VERIFICATION_SUCCESS')
            """, USER_ID, USER_ID.toString());
        insertProfile(PROFILE_ID, FIRM_ID, true, false);
        insertProfile(OTHER_PROFILE_ID, OTHER_FIRM_ID, false, true);
        insertProfile(UNRESTRICTED_PROFILE_ID, FIRM_ID, false, true);
        insertProfile(FIRMLESS_PROFILE_ID, null, false, true);

        insertApp(APP_A, "App A", 1, true, CLIENT_A);
        insertApp(APP_B, "App B", 2, true, CLIENT_B);
        insertApp(APP_INACTIVE, "App Inactive", 3, false, new UUID(1, 22));
        insertApp(APP_OF_OTHER_PROFILE, "App Other", 4, true, new UUID(1, 23));

        UUID roleA1 = insertRole(new UUID(0, 40), APP_A, "A role 1", 1);
        UUID roleA2 = insertRole(new UUID(0, 41), APP_A, "A role 2", 2);
        UUID roleB = insertRole(new UUID(0, 42), APP_B, "B role", 1);
        UUID roleInactive = insertRole(new UUID(0, 43), APP_INACTIVE, "Inactive role", 1);
        UUID roleOther = insertRole(new UUID(0, 44), APP_OF_OTHER_PROFILE, "Other profile role", 1);
        for (UUID role : List.of(roleA1, roleA2, roleB, roleInactive)) {
            jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", PROFILE_ID, role);
        }
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", OTHER_PROFILE_ID, roleOther);

        insertOffice(OFFICE_1, "0001", FIRM_ID);
        insertOffice(OFFICE_2, "0002", FIRM_ID);
        insertOffice(OFFICE_OTHER_FIRM, "0003", OTHER_FIRM_ID);
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", PROFILE_ID, OFFICE_1);
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", PROFILE_ID, OFFICE_OTHER_FIRM);
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", OTHER_PROFILE_ID, OFFICE_2);
    }

    @Test
    void visibilityContextExposesIdTypeAndFirm() {
        ProfileVisibilityContext target = repository.findVisibilityContextById(PROFILE_ID).orElseThrow();

        assertThat(target).isEqualTo(new ProfileVisibilityContext(true, PROFILE_ID, UserType.EXTERNAL, FIRM_ID));
        assertThat(repository.findVisibilityContextById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void appRolesAreSelectedForTheRequestedProfileOnly() {
        List<AppRole> roles = repository.findAppRolesByProfileId(PROFILE_ID, null, null, PageRequest.ofSize(500));

        assertThat(roles).extracting(AppRole::getName)
                .containsExactly("A role 1", "A role 2", "B role", "Inactive role");
    }

    @Test
    void appRolesCanBeFilteredBySilasAppId() {
        List<AppRole> roles = repository.findAppRolesByProfileId(PROFILE_ID, APP_B, null, PageRequest.ofSize(500));

        assertThat(roles).extracting(AppRole::getName).containsExactly("B role");
    }

    @Test
    void appRolesCanBeFilteredByEntraClientId() {
        List<AppRole> roles = repository.findAppRolesByProfileId(
                PROFILE_ID, null, CLIENT_A.toString(), PageRequest.ofSize(500));

        assertThat(roles).extracting(AppRole::getName).containsExactly("A role 1", "A role 2");
        assertThat(roles.getFirst().getApp().getEntraAppId()).isEqualTo(CLIENT_A.toString());
    }

    @Test
    void entraClientIdFilterIgnoresTheCaseOfTheStoredValue() {
        UUID mixedCaseClient = UUID.fromString("abcdef00-0000-0000-0000-00000000abcd");
        jdbc.update("UPDATE app SET entra_app_id = ? WHERE id = ?", mixedCaseClient.toString().toUpperCase(), APP_B);

        List<AppRole> roles = repository.findAppRolesByProfileId(
                PROFILE_ID, null, mixedCaseClient.toString(), PageRequest.ofSize(500));

        assertThat(roles).extracting(AppRole::getName).containsExactly("B role");
    }

    @Test
    void appRolesFilteredByAnAppTheProfileDoesNotHoldAreEmpty() {
        assertThat(repository.findAppRolesByProfileId(PROFILE_ID, APP_OF_OTHER_PROFILE, null, PageRequest.ofSize(500)))
                .isEmpty();
    }

    @Test
    void appRolesAreCapped() {
        assertThat(repository.findAppRolesByProfileId(PROFILE_ID, null, null, PageRequest.ofSize(2))).hasSize(2);
    }

    @Test
    void appsAreDistinctActiveAndDerivedFromAssignedRoles() {
        List<AppView> apps = repository.findEnabledAppsByProfileId(PROFILE_ID, PageRequest.ofSize(100));

        assertThat(apps).extracting(AppView::name).containsExactly("App A", "App B");
        assertThat(apps.getFirst().appId()).isEqualTo(APP_A.toString());
        assertThat(apps.getFirst().appEntraClientId()).isEqualTo(CLIENT_A.toString());
        assertThat(apps.getFirst().active()).isTrue();
    }

    @Test
    void appsAreEmptyWhenNoRolesAreAssigned() {
        jdbc.update("DELETE FROM user_profile_app_role WHERE user_profile_id = ?", PROFILE_ID);

        assertThat(repository.findEnabledAppsByProfileId(PROFILE_ID, PageRequest.ofSize(100))).isEmpty();
    }

    @Test
    void restrictedProfileSeesOnlyItsAssignedOfficesWithinItsFirm() {
        List<OfficeView> offices = repository.findAccessibleOfficesByProfileId(PROFILE_ID, PageRequest.ofSize(100));

        assertThat(offices).extracting(OfficeView::officeId).containsExactly(OFFICE_1.toString());
        assertThat(offices.getFirst().firmId()).isEqualTo(FIRM_ID.toString());
    }

    @Test
    void unrestrictedProfileSeesEveryOfficeOfItsFirmOnly() {
        List<OfficeView> offices = repository.findAccessibleOfficesByProfileId(
                UNRESTRICTED_PROFILE_ID, PageRequest.ofSize(100));

        assertThat(offices).extracting(OfficeView::officeId)
                .containsExactly(OFFICE_1.toString(), OFFICE_2.toString());
    }

    @Test
    void profileWithoutFirmHasNoOffices() {
        assertThat(repository.findAccessibleOfficesByProfileId(FIRMLESS_PROFILE_ID, PageRequest.ofSize(100)))
                .isEmpty();
    }

    @Test
    void officesAreCapped() {
        assertThat(repository.findAccessibleOfficesByProfileId(UNRESTRICTED_PROFILE_ID, PageRequest.ofSize(1)))
                .hasSize(1);
    }

    private void insertProfile(UUID id, UUID firmId, boolean active, boolean unrestricted) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                unrestricted_office_access)
            VALUES (?, ?, 'EXTERNAL', ?, ?, 'ACTIVE', ?)
            """, id, USER_ID, active, firmId, unrestricted);
    }

    private void insertApp(UUID id, String name, int ordinal, boolean enabled, UUID clientId) {
        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid,
                security_group_oid)
            VALUES (?, ?, ?, 'LAA', ?, ?, ?, ?, ?, ?)
            """, id, name, name + " description", ordinal, "https://" + id, enabled, clientId.toString(),
                id.toString(), id.toString());
    }

    private UUID insertRole(UUID id, UUID appId, String name, int ordinal) {
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, ?, ?, ?, ?, FALSE, FALSE, ?)
            """, id, name, name, ordinal, appId, "ROLE" + ordinal);
        return id;
    }

    private void insertOffice(UUID id, String code, UUID firmId) {
        jdbc.update("INSERT INTO office (id, code, firm_id, city) VALUES (?, ?, ?, 'Town')", id, code, firmId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserProfileAccessQueryRepository.class)
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
