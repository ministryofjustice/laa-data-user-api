package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

@SpringJUnitConfig(UserProfileQueryRepositoryTest.Config.class)
@Testcontainers
class UserProfileQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final String USER_ENTRA_OID = USER_ID.toString();
    private static final UUID ACTIVE_PROFILE_ID = new UUID(0, 2);
    private static final UUID INACTIVE_PROFILE_ID = new UUID(0, 3);
    private static final UUID FIRM_ID = new UUID(0, 10);
    private static final UUID OTHER_FIRM_ID = new UUID(0, 11);
    private static final UUID APP_ID = new UUID(0, 20);
    private static final UUID ROLE_ID = new UUID(0, 30);
    private static final UUID OFFICE_ID = new UUID(0, 40);

    @Autowired
    private UserProfileQueryRepository repository;
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
            """, USER_ID, USER_ENTRA_OID);
        insertProfile(ACTIVE_PROFILE_ID, FIRM_ID, true);
        insertProfile(INACTIVE_PROFILE_ID, OTHER_FIRM_ID, false);

        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid,
                security_group_oid)
            VALUES (?, 'App', 'App description', 'LAA', 1, 'https://app', TRUE, ?, ?, ?)
            """, APP_ID, APP_ID.toString(), APP_ID.toString(), APP_ID.toString());
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, 'Role', 'Role', 1, ?, FALSE, FALSE, 'ROLE1')
            """, ROLE_ID, APP_ID);
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", ACTIVE_PROFILE_ID, ROLE_ID);

        jdbc.update("INSERT INTO office (id, code, firm_id, city) VALUES (?, '0001', ?, 'Town')", OFFICE_ID, FIRM_ID);
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", ACTIVE_PROFILE_ID, OFFICE_ID);
    }

    @Test
    void findAllProfilesByUserEntraObjectIdReturnsEverySummaryForTheUser() {
        List<UserProfileSummaryView> profiles =
                repository.findAllProfilesByUserEntraObjectId(USER_ENTRA_OID).orElseThrow();

        assertThat(profiles).extracting(UserProfileSummaryView::userProfileId)
                .containsExactlyInAnyOrder(ACTIVE_PROFILE_ID, INACTIVE_PROFILE_ID);
        UserProfileSummaryView active = profiles.stream()
                .filter(UserProfileSummaryView::activeProfile).findFirst().orElseThrow();
        assertThat(active.userProfileId()).isEqualTo(ACTIVE_PROFILE_ID);
        assertThat(active.firmId()).isEqualTo(FIRM_ID.toString());
        assertThat(active.hasAppRoles()).isTrue();
    }

    @Test
    void findAllProfilesByUserEntraObjectIdReturnsEmptyForAnUnknownUser() {
        assertThat(repository.findAllProfilesByUserEntraObjectId(UUID.randomUUID().toString()).orElseThrow())
                .isEmpty();
    }

    @Test
    void findActiveProfileWithDetailsAndRolesLoadsOnlyTheActiveProfileWithItsRolesAndOffices() {
        UserProfile profile = repository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OID).orElseThrow();

        assertThat(profile.getId()).isEqualTo(ACTIVE_PROFILE_ID);
        assertThat(profile.getFirm().getId()).isEqualTo(FIRM_ID);
        assertThat(profile.getAppRoles()).extracting(role -> role.getId()).containsExactly(ROLE_ID);
        assertThat(profile.getOffices()).extracting(office -> office.getId()).containsExactly(OFFICE_ID);
    }

    @Test
    void findActiveProfileWithDetailsAndRolesIsEmptyWhenNoProfileIsActive() {
        jdbc.update("UPDATE user_profile SET active_profile = FALSE WHERE id = ?", ACTIVE_PROFILE_ID);

        assertThat(repository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OID)).isEmpty();
    }

    @Test
    void findProfileVisibilityContextsReturnsFactsForEveryProfileOrderedActiveFirst() {
        List<ProfileVisibilityContext> contexts = repository.findProfileVisibilityContexts(USER_ENTRA_OID);

        assertThat(contexts).hasSize(2);
        assertThat(contexts.getFirst())
                .isEqualTo(new ProfileVisibilityContext(true, ACTIVE_PROFILE_ID, UserType.EXTERNAL, FIRM_ID));
        assertThat(contexts.get(1))
                .isEqualTo(new ProfileVisibilityContext(true, INACTIVE_PROFILE_ID, UserType.EXTERNAL, OTHER_FIRM_ID));
    }

    @Test
    void findActiveProfileVisibilityContextReturnsOnlyTheActiveProfilesFacts() {
        Optional<ProfileVisibilityContext> context = repository.findActiveProfileVisibilityContext(USER_ENTRA_OID);

        assertThat(context).contains(new ProfileVisibilityContext(true, ACTIVE_PROFILE_ID, UserType.EXTERNAL, FIRM_ID));
    }

    @Test
    void findActiveProfileVisibilityContextIsEmptyWhenNoProfileIsActive() {
        jdbc.update("UPDATE user_profile SET active_profile = FALSE WHERE id = ?", ACTIVE_PROFILE_ID);

        assertThat(repository.findActiveProfileVisibilityContext(USER_ENTRA_OID)).isEmpty();
    }

    @Test
    void findActiveProfileVisibilityContextReflectsProfileSwitchImmediately() {
        jdbc.update("UPDATE user_profile SET active_profile = FALSE WHERE id = ?", ACTIVE_PROFILE_ID);
        jdbc.update("UPDATE user_profile SET active_profile = TRUE WHERE id = ?", INACTIVE_PROFILE_ID);

        Optional<ProfileVisibilityContext> context = repository.findActiveProfileVisibilityContext(USER_ENTRA_OID);

        assertThat(context)
                .contains(new ProfileVisibilityContext(true, INACTIVE_PROFILE_ID, UserType.EXTERNAL, OTHER_FIRM_ID));
    }

    private void insertProfile(UUID id, UUID firmId, boolean active) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                unrestricted_office_access)
            VALUES (?, ?, 'EXTERNAL', ?, ?, 'COMPLETE', FALSE)
            """, id, USER_ID, active, firmId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserProfileQueryRepository.class)
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
