package uk.gov.justice.laa.datauserapi.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
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
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

@SpringJUnitConfig(DeleteUserPersistenceTest.Config.class)
@Testcontainers
class DeleteUserPersistenceTest {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final UUID OTHER_USER_ID = new UUID(0, 2);
    private static final UUID PROFILE_ID = new UUID(0, 3);
    private static final UUID SECOND_PROFILE_ID = new UUID(0, 4);
    private static final UUID OTHER_PROFILE_ID = new UUID(0, 5);
    private static final UUID FIRM_ID = new UUID(0, 6);
    private static final UUID APP_ID = new UUID(0, 7);
    private static final UUID ROLE_ID = new UUID(0, 8);
    private static final UUID OFFICE_ID = new UUID(0, 9);
    private static final UUID REASON_ID = new UUID(0, 10);
    private static final UUID ACTOR_ID = new UUID(0, 11);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DeleteUserService service;
    @Autowired
    private RoleChangeNotificationService notifications;

    @BeforeEach
    void seed() {
        reset(notifications);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS delete_user_reason (
                id UUID PRIMARY KEY, code VARCHAR(255) NOT NULL, label VARCHAR(255) NOT NULL,
                editable_by_internal_user BOOLEAN NOT NULL, editable_by_external_user BOOLEAN NOT NULL,
                system_generated BOOLEAN NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS user_account_status_audit (
                id UUID PRIMARY KEY, status_changed_date TIMESTAMP NOT NULL, status_changed_by VARCHAR(255) NOT NULL,
                status_change VARCHAR(255) NOT NULL, entra_user_id UUID, user_email VARCHAR(255),
                user_name VARCHAR(511), disable_user_reason_id UUID, disable_type VARCHAR(20),
                delete_user_reason_id UUID REFERENCES delete_user_reason, comments VARCHAR(500),
                deleted_user_created_date TIMESTAMP
            )
            """);
        jdbc.execute("TRUNCATE user_account_status_audit, delete_user_reason, entra_user, user_profile, firm, app, app_role, office CASCADE");
        jdbc.update("INSERT INTO delete_user_reason VALUES (?, 'LeftFirm', 'Left firm', true, true, false)", REASON_ID);
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Firm', 'F1', 'LEGAL_SERVICES_PROVIDER', true)", FIRM_ID);
        insertUser(USER_ID);
        insertUser(OTHER_USER_ID);
        insertProfile(PROFILE_ID, USER_ID, true);
        insertProfile(SECOND_PROFILE_ID, USER_ID, false);
        insertProfile(OTHER_PROFILE_ID, OTHER_USER_ID, true);
        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid,
                security_group_oid)
            VALUES (?, 'App', 'Description', 'LAA', 1, 'https://app', true, ?, ?, ?)
            """, APP_ID, APP_ID.toString(), APP_ID.toString(), APP_ID.toString());
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, 'Role', 'Role', 1, ?, false, true, 'ROLE1')
            """, ROLE_ID, APP_ID);
        jdbc.update("INSERT INTO office (id, code, firm_id, city) VALUES (?, '0001', ?, 'Town')", OFFICE_ID, FIRM_ID);
        for (UUID profileId : Set.of(PROFILE_ID, SECOND_PROFILE_ID, OTHER_PROFILE_ID)) {
            jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", profileId, ROLE_ID);
            jdbc.update("INSERT INTO user_profile_office VALUES (?, ?)", profileId, OFFICE_ID);
        }
    }

    @Test
    void deletesProfilesAndUserWithoutRevivingProfilesAtFlush() {
        var actor = AuthenticatedEntraUser.builder().id(ACTOR_ID).entraOid(ACTOR_ID.toString()).build();

        assertThat(service.delete(new DeleteUserCommand(USER_ID, DeleteUserReason.LeftFirm), actor).success()).isTrue();

        assertThat(count("entra_user", USER_ID)).isZero();
        assertThat(count("user_profile", PROFILE_ID)).isZero();
        assertThat(count("user_profile", SECOND_PROFILE_ID)).isZero();
        assertThat(count("entra_user", OTHER_USER_ID)).isEqualTo(1);
        assertThat(count("user_profile", OTHER_PROFILE_ID)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT user_profile_id FROM user_profile_app_role", UUID.class))
                .containsExactly(OTHER_PROFILE_ID);
        assertThat(jdbc.queryForList("SELECT user_profile_id FROM user_profile_office", UUID.class))
                .containsExactly(OTHER_PROFILE_ID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account_status_audit WHERE entra_user_id = ?",
                Integer.class, USER_ID)).isEqualTo(1);
        verify(notifications, times(2)).sendMessage(any(UserProfile.class), eq(APP_ID.toString()),
                eq(Set.of()), eq(Set.of("ROLE1")));
    }

    @Test
    void deletesUserWithoutProfiles() {
        jdbc.update("DELETE FROM user_profile_app_role WHERE user_profile_id IN (?, ?)", PROFILE_ID, SECOND_PROFILE_ID);
        jdbc.update("DELETE FROM user_profile_office WHERE user_profile_id IN (?, ?)", PROFILE_ID, SECOND_PROFILE_ID);
        jdbc.update("DELETE FROM user_profile WHERE entra_user_id = ?", USER_ID);
        var actor = AuthenticatedEntraUser.builder().id(ACTOR_ID).entraOid(ACTOR_ID.toString()).build();

        assertThat(service.delete(new DeleteUserCommand(USER_ID, DeleteUserReason.LeftFirm), actor).success()).isTrue();

        assertThat(count("entra_user", USER_ID)).isZero();
        assertThat(count("entra_user", OTHER_USER_ID)).isEqualTo(1);
        assertThat(count("user_profile", OTHER_PROFILE_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account_status_audit WHERE entra_user_id = ?",
                Integer.class, USER_ID)).isEqualTo(1);
    }

    private void insertUser(UUID id) {
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
            VALUES (?, ?, 'First', 'Last', ?, 'ACTIVE', true, 'VERIFICATION_SUCCESS')
            """, id, id.toString(), id + "@example.test");
    }

    private void insertProfile(UUID id, UUID userId, boolean active) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                unrestricted_office_access)
            VALUES (?, ?, 'EXTERNAL', ?, ?, 'COMPLETE', false)
            """, id, userId, active, FIRM_ID);
    }

    private int count(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = EntraUserCommandRepository.class)
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
            var factory = new LocalContainerEntityManagerFactoryBean();
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

        @Bean
        RoleChangeNotificationService notifications() {
            return mock(RoleChangeNotificationService.class);
        }

        @Bean
        DeleteUserService deleteUserService(EntraUserCommandRepository users, UserProfileCommandRepository profiles,
                                            UserAccountStatusAuditRepository audits, DeleteUserReasonRepository reasons,
                                            RoleChangeNotificationService notifications, EntityManager entityManager) {
            return new DeleteUserService(users, profiles, audits, reasons, new DeleteUserMapper(), notifications,
                    mock(DeleteUserTechServicesService.class), new ModelMapper(), entityManager);
        }
    }
}
