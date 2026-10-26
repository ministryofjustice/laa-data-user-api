package uk.gov.justice.laa.datauserapi.integration;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmDomainMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ConflictException;
import uk.gov.justice.laa.datauserapi.model.Permission;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(ConvertToMultiFirmIntegrationTest.Config.class)
@Testcontainers
class ConvertToMultiFirmIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID TARGET_ID = new UUID(0, 1);
    private static final UUID PROFILE_ID = new UUID(0, 2);

    @Autowired
    private ConvertToMultiFirmHandler handler;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE entra_user, user_profile CASCADE");
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
            VALUES (?, ?, 'First', 'Last', 'target@example.test', 'ACTIVE', TRUE, 'VERIFICATION_SUCCESS')
            """, TARGET_ID, TARGET_ID.toString());
        jdbc.update("""
            INSERT INTO user_profile (id, entra_user_id, user_type, silas_status)
            VALUES (?, ?, 'EXTERNAL', 'ACTIVE')
            """, PROFILE_ID, TARGET_ID);
    }

    @Test
    void conversionUpdatesOnlyMultiFirmFlagAndAuditFields() {
        Map<String, Object> userBefore = jdbc.queryForMap("SELECT * FROM entra_user WHERE id = ?", TARGET_ID);
        Map<String, Object> profileBefore = jdbc.queryForMap("SELECT * FROM user_profile WHERE id = ?", PROFILE_ID);

        CommandResult result = handler.handle(command(), actor("actor-1"));

        assertThat(result.success()).isTrue();
        Map<String, Object> userAfter = jdbc.queryForMap("SELECT * FROM entra_user WHERE id = ?", TARGET_ID);
        assertThat(userAfter.get("multi_firm_user")).isEqualTo(true);
        assertThat(userAfter.get("last_modified_by")).isEqualTo("actor-1");
        assertThat(userAfter.get("last_modified_date")).isNotNull();
        assertThat(userAfter)
                .containsAllEntriesOf(without(userBefore, "multi_firm_user", "last_modified_by", "last_modified_date"));
        assertThat(jdbc.queryForMap("SELECT * FROM user_profile WHERE id = ?", PROFILE_ID)).isEqualTo(profileBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile", Integer.class)).isEqualTo(1);
    }

    @Test
    void alreadyConvertedUserIsRejectedWithoutChange() {
        handler.handle(command(), actor("actor-1"));
        Map<String, Object> afterFirst = jdbc.queryForMap("SELECT * FROM entra_user WHERE id = ?", TARGET_ID);

        assertThatThrownBy(() -> handler.handle(command(), actor("actor-2"))).isInstanceOf(ConflictException.class);

        assertThat(jdbc.queryForMap("SELECT * FROM entra_user WHERE id = ?", TARGET_ID)).isEqualTo(afterFirst);
    }

    @Test
    void secondConversionBlocksUntilFirstCommitsThenIsRejected() throws Exception {
        OverlappingConversions run = convertOverlapping(false);

        assertThat(run.first().success()).isTrue();
        assertThat(run.secondFailure()).isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForMap("SELECT multi_firm_user, last_modified_by FROM entra_user WHERE id = ?", TARGET_ID))
                .containsEntry("multi_firm_user", true)
                .containsEntry("last_modified_by", "actor-1");
    }

    @Test
    void secondConversionSucceedsWhenFirstRollsBack() throws Exception {
        OverlappingConversions run = convertOverlapping(true);

        assertThat(run.secondFailure()).isNull();
        assertThat(run.second().success()).isTrue();
        assertThat(jdbc.queryForMap("SELECT multi_firm_user, last_modified_by FROM entra_user WHERE id = ?", TARGET_ID))
                .containsEntry("multi_firm_user", true)
                .containsEntry("last_modified_by", "actor-2");
    }

    /**
     * Holds the first transaction open until PostgreSQL reports another session waiting on a lock,
     * which proves the second conversion genuinely overlapped the first before it is released.
     */
    private OverlappingConversions convertOverlapping(boolean rollbackFirst) throws Exception {
        CountDownLatch firstHandled = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CommandResult> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                CommandResult result = handler.handle(command(), actor("actor-1"));
                firstHandled.countDown();
                awaitLatch(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            Future<CommandResult> second = executor.submit(() -> {
                awaitLatch(firstHandled);
                return handler.handle(command(), actor("actor-2"));
            });

            try {
                awaitSessionWaitingOnLock();
            } finally {
                releaseFirst.countDown();
            }

            CommandResult firstResult = first.get(30, TimeUnit.SECONDS);
            try {
                return new OverlappingConversions(firstResult, second.get(30, TimeUnit.SECONDS), null);
            } catch (ExecutionException e) {
                return new OverlappingConversions(firstResult, null, e.getCause());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitSessionWaitingOnLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("The second conversion never waited on the first transaction's row lock");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record OverlappingConversions(CommandResult first, CommandResult second, Throwable secondFailure) {
    }

    private static Map<String, Object> without(Map<String, Object> row, String... columns) {
        Map<String, Object> copy = new java.util.HashMap<>(row);
        for (String column : columns) {
            copy.remove(column);
        }
        return copy;
    }

    private static ConvertToMultiFirmCommand command() {
        return new ConvertToMultiFirmCommand(TARGET_ID);
    }

    private static AuthenticatedEntraUser actor(String oid) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "role", 0, "ROLE", false, null, null,
                null, false, null, Set.of(Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM));
        return AuthenticatedEntraUser.builder().entraOid(oid).activeProfile(
                AuthenticatedUserProfile.builder().activeProfile(true).userType(UserType.INTERNAL)
                        .appRoles(Set.of(role)).build()).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = EntraUserCommandRepository.class)
    @Import({ConvertToMultiFirmHandler.class, ConvertToMultiFirmDomainMapper.class})
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
