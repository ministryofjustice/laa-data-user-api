package uk.gov.justice.laa.datauserapi.integration;

import jakarta.persistence.EntityManager;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.datauserapi.application.command.handler.SetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.command.service.SetActiveProfilePolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.command.service.SetActiveProfileService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(SetActiveProfileConcurrencyIntegrationTest.Config.class)
@Testcontainers
class SetActiveProfileConcurrencyIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final UUID USER_OID = new UUID(0, 2);
    private static final UUID PROFILE_A = new UUID(0, 3);
    private static final UUID PROFILE_B = new UUID(0, 4);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserProfileCommandRepository profiles;
    @Autowired private SetActiveProfileHandler handler;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE entra_user, user_profile CASCADE");
        jdbc.update("""
                INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled)
                VALUES (?, ?, 'First', 'Last', 'user@example.test', 'ACTIVE', TRUE)
                """, USER_ID, USER_OID.toString());
        jdbc.update("""
                INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, silas_status)
                VALUES (?, ?, 'EXTERNAL', ?, 'COMPLETE')
                """, PROFILE_A, USER_ID, true);
        jdbc.update("""
                INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, silas_status)
                VALUES (?, ?, 'EXTERNAL', ?, 'COMPLETE')
                """, PROFILE_B, USER_ID, false);
    }

    @Test
    void overlappingSwitchRefreshesBothPreloadedProfilesAfterWaitingForUserLock() throws Exception {
        CountDownLatch preloaded = new CountDownLatch(1);
        CountDownLatch firstHandled = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CommandResult> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                await(preloaded);
                CommandResult result = handler.handle(new SetActiveProfileCommand(USER_OID, PROFILE_B), actor());
                firstHandled.countDown();
                await(releaseFirst);
                return result;
            }));
            Future<CommandResult> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(profiles.findById(PROFILE_A).orElseThrow().isActiveProfile()).isTrue();
                assertThat(profiles.findById(PROFILE_B).orElseThrow().isActiveProfile()).isFalse();
                preloaded.countDown();
                await(firstHandled);
                return handler.handle(new SetActiveProfileCommand(USER_OID, PROFILE_A), actor());
            }));
            try {
                awaitWaitingOnLock();
            } finally {
                releaseFirst.countDown();
            }
            assertThat(first.get(30, TimeUnit.SECONDS).success()).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS).success()).isTrue();
            assertThat(jdbc.queryForObject("SELECT active_profile FROM user_profile WHERE id = ?", Boolean.class, PROFILE_A))
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT active_profile FROM user_profile WHERE id = ?", Boolean.class, PROFILE_B))
                    .isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE active_profile = true",
                    Integer.class)).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitWaitingOnLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Second switch did not wait for the user lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static AuthenticatedEntraUser actor() {
        AuthenticatedUserProfile a = AuthenticatedUserProfile.builder().id(PROFILE_A)
                .activeProfile(true).userType(UserType.EXTERNAL).build();
        AuthenticatedUserProfile b = AuthenticatedUserProfile.builder().id(PROFILE_B)
                .userType(UserType.EXTERNAL).build();
        return AuthenticatedEntraUser.builder().id(USER_ID).entraOid(USER_OID.toString())
                .activeProfile(a).userProfiles(Set.of(a, b)).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = EntraUserCommandRepository.class)
    @Import({RbacMethodSecurityConfig.class, SetActiveProfileHandler.class, SetActiveProfileService.class,
        SetActiveProfilePolicy.class, SetActiveProfilePolicyInputResolver.class})
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
        EntityManager entityManager(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
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
