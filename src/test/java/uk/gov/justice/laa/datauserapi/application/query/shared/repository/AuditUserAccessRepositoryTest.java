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
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;

@SpringJUnitConfig(AuditUserAccessRepositoryTest.Config.class)
@Testcontainers
class AuditUserAccessRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final String USER_OID = new UUID(1, 1).toString();
    private static final UUID OTHER_USER_ID = new UUID(0, 2);
    private static final UUID FIRM_ID = new UUID(0, 10);
    private static final UUID ACTIVE_PROFILE = new UUID(0, 20);
    private static final UUID OTHER_PROFILE = new UUID(0, 21);
    private static final UUID OTHER_USERS_PROFILE = new UUID(0, 22);
    private static final UUID APP_ID = new UUID(0, 30);
    private static final UUID ROLE_ID = new UUID(0, 31);
    private static final UUID OFFICE_ID = new UUID(0, 40);

    @Autowired
    private AuditUserAccessRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE entra_user, user_profile, firm, app, app_role, office CASCADE");
        jdbc.update("INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Firm', 'F1', 'LEGAL_SERVICES_PROVIDER', TRUE)", FIRM_ID);
        insertUser(USER_ID, USER_OID, "user@example.test", true);
        insertUser(OTHER_USER_ID, new UUID(1, 2).toString(), "other@example.test", false);
        insertProfile(ACTIVE_PROFILE, USER_ID, "EXTERNAL", true, FIRM_ID);
        insertProfile(OTHER_PROFILE, USER_ID, "EXTERNAL", false, FIRM_ID);
        insertProfile(OTHER_USERS_PROFILE, OTHER_USER_ID, "INTERNAL", true, null);
        jdbc.update("""
            INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_app_id, entra_oid, security_group_oid)
            VALUES (?, 'App', 'App', 'LAA', 1, 'https://app', TRUE, 'client', 'oid', 'group')
            """, APP_ID);
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, app_id, authz_role, legacy_sync, role_identifier)
            VALUES (?, 'Role', 'Role', 1, ?, FALSE, FALSE, 'ROLE1')
            """, ROLE_ID, APP_ID);
        jdbc.update("INSERT INTO user_profile_app_role (user_profile_id, app_role_id) VALUES (?, ?)", ACTIVE_PROFILE, ROLE_ID);
        jdbc.update("INSERT INTO office (id, code, firm_id, city, post_code) VALUES (?, 'O1', ?, 'Town', 'AB1 2CD')", OFFICE_ID, FIRM_ID);
        jdbc.update("INSERT INTO user_profile_office (user_profile_id, office_id) VALUES (?, ?)", ACTIVE_PROFILE, OFFICE_ID);
    }

    @Test
    void findAccountReturnsAccountFactsWithoutProfiles() {
        AuditAccountRow account = repository.findAccount(USER_OID).orElseThrow();

        assertThat(account.entraOid()).isEqualTo(USER_OID);
        assertThat(account.email()).isEqualTo("user@example.test");
        assertThat(account.firstName()).isEqualTo("First");
        assertThat(account.userAccountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(account.multiFirmUser()).isTrue();
        assertThat(account.invitationStatus()).isEqualTo(InvitationStatus.VERIFICATION_SUCCESS);
    }

    @Test
    void findAccountIsEmptyForAnUnknownAccount() {
        assertThat(repository.findAccount(new UUID(9, 9).toString())).isEmpty();
    }

    @Test
    void findProfileRowsReturnsOnlyTheAccountsProfilesWithTheirTypeAndFirm() {
        List<AuditProfileRow> rows = repository.findProfileRows(USER_OID);

        assertThat(rows).containsExactlyInAnyOrder(
                new AuditProfileRow(ACTIVE_PROFILE, true, UserType.EXTERNAL, FIRM_ID),
                new AuditProfileRow(OTHER_PROFILE, false, UserType.EXTERNAL, FIRM_ID));
    }

    @Test
    void findProfilesWithDetailsLoadsOnlyTheRequestedProfilesWithTheirRolesAndOffices() {
        List<UserProfile> profiles = repository.findProfilesWithDetails(List.of(ACTIVE_PROFILE));

        assertThat(profiles).singleElement().satisfies(profile -> {
            assertThat(profile.getId()).isEqualTo(ACTIVE_PROFILE);
            assertThat(profile.getFirm().getName()).isEqualTo("Firm");
            assertThat(profile.getAppRoles()).singleElement()
                    .satisfies(role -> assertThat(role.getApp().getName()).isEqualTo("App"));
            assertThat(profile.getOffices()).singleElement()
                    .satisfies(office -> assertThat(office.getAddress().getPostcode()).isEqualTo("AB1 2CD"));
        });
    }

    private void insertUser(UUID id, String oid, String email, boolean multiFirm) {
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status,
                multi_firm_user)
            VALUES (?, ?, 'First', 'Last', ?, 'ACTIVE', TRUE, 'VERIFICATION_SUCCESS', ?)
            """, id, oid, email, multiFirm);
    }

    private void insertProfile(UUID id, UUID userId, String type, boolean active, UUID firmId) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status)
            VALUES (?, ?, ?, ?, ?, 'COMPLETE')
            """, id, userId, type, active, firmId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AuditUserAccessRepository.class)
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
