package uk.gov.justice.laa.datauserapi.integration;

import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshBatchService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(SilasStatusRefreshIntegrationTest.Config.class)
@TestPropertySource(properties = "app.silas-status-refresh.batch-size=2")
@Testcontainers
class SilasStatusRefreshIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final String ACTOR = "refresh-actor";

    @Autowired
    private SilasStatusRefreshBatchService batchService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_refresh ON user_profile");
        jdbc.execute("TRUNCATE entra_user, user_profile, user_profile_app_role, user_profile_office, "
                + "app_role, role_permission, app, office, firm CASCADE");
    }

    @Test
    void traversesMultipleBatchesIncludingAccountsWithoutProfilesAndDoesNotRewriteUnchangedRows() {
        for (int i = 1; i <= 5; i++) {
            insertUser(i, i != 2, i == 4 ? "VERIFICATION_SUCCESS" : null);
        }
        insertProfile(1, 1, "INTERNAL", false);
        insertProfile(2, 2, "INTERNAL", false);
        insertProfile(3, 3, "EXTERNAL", true);

        var first = batchService.refreshBatch(null, ACTOR);
        var second = batchService.refreshBatch(first.lastUserId(), ACTOR);
        var third = batchService.refreshBatch(second.lastUserId(), ACTOR);
        var end = batchService.refreshBatch(third.lastUserId(), ACTOR);

        assertThat(first).isEqualTo(new SilasStatusRefreshBatchService.BatchResult(id(2), 2, 2));
        assertThat(second).isEqualTo(new SilasStatusRefreshBatchService.BatchResult(id(4), 2, 1));
        assertThat(third).isEqualTo(new SilasStatusRefreshBatchService.BatchResult(id(5), 1, 0));
        assertThat(end).isEqualTo(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));
        assertThat(jdbc.queryForList("SELECT status FROM entra_user ORDER BY id", String.class))
                .containsExactly("ACTIVE", "DEACTIVATED", "ACTIVATION_REQUIRED", "ACTIVE", "ACTIVATION_REQUIRED");
        assertThat(jdbc.queryForList("SELECT silas_status FROM user_profile ORDER BY id", String.class))
                .containsExactly("NO_ACCESS_ASSIGNED", "NO_ACCESS_ASSIGNED", "ACTIVATION_REQUIRED");
        assertThat(jdbc.queryForList("SELECT last_modified_by FROM entra_user", String.class))
                .containsOnly(ACTOR);
        assertThat(jdbc.queryForList("SELECT last_modified_by FROM user_profile", String.class))
                .containsOnly(ACTOR);
        var originalAudit = jdbc.queryForList("SELECT last_modified_date, last_modified_by FROM entra_user ORDER BY id");
        assertThat(batchService.refreshBatch(null, "another-actor"))
                .isEqualTo(new SilasStatusRefreshBatchService.BatchResult(id(2), 0, 0));
        assertThat(jdbc.queryForList("SELECT last_modified_date, last_modified_by FROM entra_user ORDER BY id"))
                .isEqualTo(originalAudit);
    }

    @Test
    void fetchesBothCollectionsSeparatelyAndIncludesEveryProfileOfEachUser() {
        insertUser(1, true, "VERIFICATION_SUCCESS");
        insertProfile(1, 1, "EXTERNAL", false);
        insertProfile(2, 1, "EXTERNAL", true);
        insertProfile(3, 1, "INTERNAL", false);
        jdbc.update("INSERT INTO firm (id, name, type, enabled) VALUES (?, 'Firm', 'CHAMBERS', TRUE)", id(20));
        jdbc.update("""
            INSERT INTO app_role (id, name, description, ordinal, authz_role, legacy_sync)
            VALUES (?, 'Role A', 'Description', 0, FALSE, FALSE), (?, 'Role B', 'Description', 1, FALSE, FALSE)
            """, id(21), id(22));
        jdbc.update("INSERT INTO office (id, code, firm_id) VALUES (?, 'A', ?), (?, 'B', ?)",
                id(23), id(20), id(24), id(20));
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?), (?, ?), (?, ?), (?, ?)",
                id(1), id(21), id(1), id(22), id(2), id(21), id(3), id(21));
        jdbc.update("INSERT INTO user_profile_office VALUES (?, ?), (?, ?)",
                id(1), id(23), id(1), id(24));

        var result = batchService.refreshBatch(null, ACTOR);

        assertThat(result.updatedProfiles()).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT silas_status FROM user_profile ORDER BY id", String.class))
                .containsExactly("COMPLETE", "COMPLETE", "COMPLETE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_app_role", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_office", Integer.class)).isEqualTo(2);
    }

    @Test
    void commitsEachBatchIndependentlyOfAnOuterTransactionAndReleasesRowLocks() {
        insertUser(1, true, null);
        insertProfile(1, 1, "INTERNAL", false);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            batchService.refreshBatch(null, ACTOR);
            assertThat(jdbc.queryForObject("SELECT status FROM entra_user WHERE id = ?", String.class, id(1)))
                    .isEqualTo("ACTIVE");
            jdbc.queryForList("SELECT id FROM entra_user FOR UPDATE NOWAIT");
            jdbc.queryForList("SELECT id FROM user_profile FOR UPDATE NOWAIT");
            transaction.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT status FROM entra_user WHERE id = ?", String.class, id(1)))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT silas_status FROM user_profile WHERE id = ?", String.class, id(1)))
                .isEqualTo("NO_ACCESS_ASSIGNED");
    }

    @Test
    void rollsBackBothAccountAndProfileChangesInFailedBatchButPreservesEarlierBatch() {
        for (int i = 1; i <= 3; i++) {
            insertUser(i, true, null);
            insertProfile(i, i, "INTERNAL", false);
        }
        var first = batchService.refreshBatch(null, ACTOR);
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION reject_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
                IF NEW.id = '00000000-0000-0000-0000-000000000003'::uuid THEN
                    RAISE EXCEPTION 'Deliberate batch failure';
                END IF;
                RETURN NEW;
            END $$
            """);
        jdbc.execute("CREATE TRIGGER reject_refresh BEFORE UPDATE ON user_profile "
                + "FOR EACH ROW EXECUTE FUNCTION reject_refresh()");

        assertThatThrownBy(() -> batchService.refreshBatch(first.lastUserId(), ACTOR))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Deliberate batch failure");

        assertThat(jdbc.queryForList("SELECT status FROM entra_user ORDER BY id", String.class))
                .containsExactly("ACTIVE", "ACTIVE", "UNKNOWN");
        assertThat(jdbc.queryForList("SELECT silas_status FROM user_profile ORDER BY id", String.class))
                .containsExactly("NO_ACCESS_ASSIGNED", "NO_ACCESS_ASSIGNED", "UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT last_modified_by FROM entra_user WHERE id = ?", String.class, id(3)))
                .isNull();
        jdbc.execute("DROP TRIGGER reject_refresh ON user_profile");
        assertThat(batchService.refreshBatch(first.lastUserId(), ACTOR).updatedAccounts()).isEqualTo(1);
    }

    private void insertUser(int number, boolean active, String invitation) {
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
            VALUES (?, ?, 'First', 'Last', ?, 'UNKNOWN', ?, ?)
            """, id(number), id(number).toString(), "user" + number + "@example.test", active, invitation);
    }

    private void insertProfile(int number, int userNumber, String type, boolean unrestricted) {
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, silas_status, unrestricted_office_access)
            VALUES (?, ?, ?, 'UNKNOWN', ?)
            """, id(number), id(userNumber), type, unrestricted);
    }

    private static UUID id(int number) {
        return new UUID(0, number);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {EntraUserCommandRepository.class, UserProfileCommandRepository.class})
    @org.springframework.context.annotation.Import(SilasStatusRefreshBatchService.class)
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
