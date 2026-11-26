package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Limit;
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
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(AuditUserExportQueryRepositoryTest.Config.class)
@Testcontainers
class AuditUserExportQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID FIRM_ID = new UUID(0, 10);
    private static final UUID OTHER_FIRM_ID = new UUID(0, 11);
    private static final UUID APP_ID = new UUID(0, 20);
    private static final UUID ROLE_ID = new UUID(0, 30);
    private static final UUID OFFICE_ID = new UUID(0, 40);

    private static final UUID EXTERNAL_USER_ID = new UUID(0, 1);
    private static final UUID EXTERNAL_PROFILE_ID = new UUID(0, 2);
    private static final UUID MULTI_FIRM_USER_ID = new UUID(0, 3);
    private static final UUID MULTI_FIRM_PROFILE_IN_FIRM_ID = new UUID(0, 4);
    private static final UUID MULTI_FIRM_PROFILE_IN_OTHER_FIRM_ID = new UUID(0, 5);
    private static final UUID INTERNAL_USER_ID = new UUID(0, 6);
    private static final UUID INTERNAL_PROFILE_ID = new UUID(0, 7);
    private static final UUID NEVER_ACTIVATED_USER_ID = new UUID(0, 8);
    private static final UUID NEVER_ACTIVATED_PROFILE_ID = new UUID(0, 9);

    @Autowired
    private AuditUserExportQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE entra_user, user_profile, firm, app, app_role, office CASCADE");
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Firm', 'F1', 'LEGAL_SERVICES_PROVIDER', TRUE)", FIRM_ID);
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Other', 'F2', 'LEGAL_SERVICES_PROVIDER', TRUE)", OTHER_FIRM_ID);

        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid, security_group_oid)
            VALUES (?, 'App', 'App description', 'LAA', 1, 'https://app', TRUE, ?, ?, ?)
            """, APP_ID, APP_ID.toString(), APP_ID.toString(), APP_ID.toString());
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, 'Role', 'Role', 1, ?, FALSE, FALSE, 'ROLE1')
            """, ROLE_ID, APP_ID);
        jdbc.update("INSERT INTO office (id, code, firm_id, city) VALUES (?, '0001', ?, 'Town')", OFFICE_ID, FIRM_ID);

        insertUser(EXTERNAL_USER_ID, "external@example.test", "ACTIVE");
        insertProfile(EXTERNAL_PROFILE_ID, EXTERNAL_USER_ID, "EXTERNAL", FIRM_ID);
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", EXTERNAL_PROFILE_ID, ROLE_ID);
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", EXTERNAL_PROFILE_ID, OFFICE_ID);

        insertUser(MULTI_FIRM_USER_ID, "multi@example.test", "ACTIVE");
        insertProfile(MULTI_FIRM_PROFILE_IN_FIRM_ID, MULTI_FIRM_USER_ID, "EXTERNAL", FIRM_ID);
        insertProfile(MULTI_FIRM_PROFILE_IN_OTHER_FIRM_ID, MULTI_FIRM_USER_ID, "EXTERNAL", OTHER_FIRM_ID);

        insertUser(INTERNAL_USER_ID, "internal@example.test", "ACTIVE");
        insertProfile(INTERNAL_PROFILE_ID, INTERNAL_USER_ID, "INTERNAL", null);

        insertUser(NEVER_ACTIVATED_USER_ID, "never-activated@example.test", "ACTIVATION_REQUIRED");
        insertProfile(NEVER_ACTIVATED_PROFILE_ID, NEVER_ACTIVATED_USER_ID, "EXTERNAL", FIRM_ID);
    }

    @Test
    void findMatchingProfileIds_firmScoped_returnsOnlyProfilesInThatFirm() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F1", null, null, null, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactlyInAnyOrder(
                EXTERNAL_PROFILE_ID, MULTI_FIRM_PROFILE_IN_FIRM_ID, NEVER_ACTIVATED_PROFILE_ID);
    }

    @Test
    void findMatchingProfileIds_multiFirmUser_isRepresentedOnlyInRequestedFirmContext() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F2", null, null, null, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactly(MULTI_FIRM_PROFILE_IN_OTHER_FIRM_ID);
    }

    @Test
    void findMatchingProfileIds_internalOnly_excludesExternalProfiles() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactly(INTERNAL_PROFILE_ID);
    }

    @Test
    void findMatchingProfileIds_neverActivatedFilter_returnsOnlyActivationRequiredUsers() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F1", null, null, null, true);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactly(NEVER_ACTIVATED_PROFILE_ID);
    }

    @Test
    void findMatchingProfileIds_officeFilter_returnsOnlyProfilesAtThatOffice() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F1", "0001", null, null, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactly(EXTERNAL_PROFILE_ID);
    }

    @Test
    void findMatchingProfileIds_appRoleFilter_returnsOnlyProfilesHoldingThatRole() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F1", null, null, ROLE_ID, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactly(EXTERNAL_PROFILE_ID);
    }

    @Test
    void findDetailsWithRoles_hydratesEntraUserAndRoles() {
        List<UserProfile> profiles =
                repository.findDetailsWithRoles(List.of(EXTERNAL_PROFILE_ID));

        assertThat(profiles).hasSize(1);
        UserProfile profile = profiles.getFirst();
        assertThat(profile.getEntraUser().getEmail()).isEqualTo("external@example.test");
        assertThat(profile.getFirm().getCode()).isEqualTo("F1");
        assertThat(profile.getAppRoles()).extracting(role -> role.getId()).containsExactly(ROLE_ID);
    }

    @Test
    void findMatchingProfileIds_officeFilter_includesUnrestrictedOfficeAccessProfilesWithNoExplicitOffice() {
        UUID unrestrictedUserId = new UUID(0, 50);
        UUID unrestrictedProfileId = new UUID(0, 51);
        insertUser(unrestrictedUserId, "unrestricted@example.test", "ACTIVE");
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                unrestricted_office_access)
            VALUES (?, ?, 'EXTERNAL', TRUE, ?, 'COMPLETE', TRUE)
            """, unrestrictedProfileId, unrestrictedUserId, FIRM_ID);

        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, "F1", "0001", null, null, null);

        List<UUID> ids = repository.findMatchingProfileIds(criteria, null, Limit.of(50));

        assertThat(ids).containsExactlyInAnyOrder(EXTERNAL_PROFILE_ID, unrestrictedProfileId);
    }

    @Test
    void findMatchingProfileIds_profileDeletedBetweenBatches_doesNotSkipLaterProfiles() {
        // Seeded profiles, ordered by id ascending: EXTERNAL_PROFILE_ID (2) < MULTI_FIRM_PROFILE_IN_FIRM_ID (4)
        // < MULTI_FIRM_PROFILE_IN_OTHER_FIRM_ID (5) < INTERNAL_PROFILE_ID (7) < NEVER_ACTIVATED_PROFILE_ID (9).
        AuditUserExportCriteria allProfiles = new AuditUserExportCriteria(null, null, null, null, null, null, null);

        List<UUID> firstBatch = repository.findMatchingProfileIds(allProfiles, null, Limit.of(2));
        assertThat(firstBatch).containsExactly(EXTERNAL_PROFILE_ID, MULTI_FIRM_PROFILE_IN_FIRM_ID);
        UUID lastSeenId = firstBatch.get(firstBatch.size() - 1);

        // Simulate a profile within the already-read batch being deleted mid-export. Offset
        // pagination would shift the next OFFSET left by one and skip a not-yet-exported profile;
        // keyset pagination (id > lastSeenId) must not be affected by this at all.
        jdbc.update("DELETE FROM user_profile_app_role WHERE user_profile_id = ?", EXTERNAL_PROFILE_ID);
        jdbc.update("DELETE FROM user_profile_office WHERE user_profile_id = ?", EXTERNAL_PROFILE_ID);
        jdbc.update("DELETE FROM user_profile WHERE id = ?", EXTERNAL_PROFILE_ID);

        List<UUID> secondBatch = repository.findMatchingProfileIds(allProfiles, lastSeenId, Limit.of(2));

        assertThat(secondBatch).containsExactly(MULTI_FIRM_PROFILE_IN_OTHER_FIRM_ID, INTERNAL_PROFILE_ID);
    }

    private void insertUser(UUID id, String email, String status) {
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
            VALUES (?, ?, 'First', 'Last', ?, ?, TRUE, 'VERIFICATION_SUCCESS')
            """, id, id.toString(), email, status);
    }

    private void insertProfile(UUID id, UUID userId, String userType, UUID firmId) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                unrestricted_office_access)
            VALUES (?, ?, ?, TRUE, ?, 'COMPLETE', FALSE)
            """, id, userId, userType, firmId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AuditUserExportQueryRepository.class)
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
