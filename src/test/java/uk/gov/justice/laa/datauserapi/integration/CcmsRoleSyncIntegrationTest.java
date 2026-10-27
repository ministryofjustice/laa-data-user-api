package uk.gov.justice.laa.datauserapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
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
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncProfileService;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.application.command.service.RoleChangeNotificationService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(CcmsRoleSyncIntegrationTest.Config.class)
@Testcontainers
class CcmsRoleSyncIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("silas-status-refresh-schema.sql");

    @Autowired
    private CcmsRoleSyncService service;
    @Autowired
    private UserProfileCommandRepository repository;
    @Autowired
    private SqsClientRegistry registry;
    @Autowired
    private SqsClient sqs;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        reset(registry, sqs);
        jdbc.execute("TRUNCATE entra_user, user_profile, app_role, user_profile_app_role, app CASCADE");
        jdbc.update("""
                INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled)
                VALUES (?, 'entra-oid', 'First', 'Last', 'user@example.test', 'ACTIVE', TRUE)
                """, id(10));
        jdbc.update("""
                INSERT INTO app (id, name, description, app_type, ordinal, url, enabled, entra_oid, security_group_oid)
                VALUES (?, 'App', 'Description', 'LAA', 0, 'https://example.test', TRUE, 'app', 'group')
                """, id(20));
        jdbc.update("""
                INSERT INTO app_role (id, app_id, name, ordinal, authz_role, legacy_sync, role_identifier)
                VALUES (?, ?, 'Legacy', 0, FALSE, TRUE, 'LEGACY'),
                       (?, ?, 'Local', 1, FALSE, FALSE, 'LOCAL')
                """, id(30), id(20), id(31), id(20));
        for (int i = 1; i <= 5; i++) {
            jdbc.update("""
                    INSERT INTO user_profile (id, entra_user_id, user_type, legacy_user_id, silas_status, last_sync_successful)
                    VALUES (?, ?, 'INTERNAL', ?, 'COMPLETE', ?)
                    """, id(i), id(10), id(i + 100), i == 4);
            jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", id(i), id(i == 5 ? 31 : 30));
        }
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?, ?)", id(1), id(31));
        when(registry.getSqsQueueUrl("app")).thenReturn(Optional.of("https://sqs/queue"));
        when(registry.getSqsClient("app")).thenReturn(Optional.of(sqs));
    }

    @Test
    void partialFailureCommitsPerProfileAndRetriggerSelectsOnlyFailedProfiles() {
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenAnswer(invocation -> {
            SendMessageRequest request = invocation.getArgument(0);
            if (request.messageGroupId().equals(id(102).toString())) {
                throw ApiCallTimeoutException.create(1000);
            }
            return SendMessageResponse.builder().messageId("message").build();
        });

        assertThatThrownBy(() -> service.sync("actor"))
                .isInstanceOfSatisfying(CcmsSyncException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(ex.getMessage()).contains("2 profiles succeeded", "1 profiles failed");
                });
        assertThat(jdbc.queryForList("SELECT last_sync_successful FROM user_profile ORDER BY id", Boolean.class))
                .containsExactly(true, false, true, true, false);
        assertThat(repository.findUserProfilesForCcmsSync()).extracting(profile -> profile.getId())
                .containsExactly(id(2));
        assertThat(jdbc.queryForObject("SELECT last_modified_by FROM user_profile WHERE id = ?", String.class, id(2)))
                .isEqualTo("actor");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_app_role WHERE user_profile_id = ?",
                Integer.class, id(1))).isEqualTo(2);
        verify(sqs, times(5)).sendMessage(any(SendMessageRequest.class));

        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("retry-message").build());
        var retryResult = service.sync("retry-actor");
        assertThat(retryResult.success()).isTrue();
        assertThat(retryResult.userProfileCount()).isEqualTo(1);
        assertThat(repository.findUserProfilesForCcmsSync()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT last_modified_by FROM user_profile WHERE id = ?", String.class, id(2)))
                .isEqualTo("retry-actor");
        verify(sqs, times(6)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void unexpectedResponsesPersistFalseForEveryProfileAndReturnBadGateway() {
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());
        assertThatThrownBy(() -> service.sync("actor"))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThat(repository.findUserProfilesForCcmsSync()).hasSize(3);
        verify(sqs, times(9)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void profileResultsCommitIndependentlyOfOuterTransaction() {
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("message").build());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var result = service.sync("actor");
            assertThat(result.success()).isTrue();
            assertThat(result.userProfileCount()).isEqualTo(3);
            transaction.setRollbackOnly();
        });
        assertThat(jdbc.queryForList("SELECT last_sync_successful FROM user_profile WHERE id IN (?, ?, ?)",
                Boolean.class, id(1), id(2), id(3))).containsOnly(true);
    }

    private static UUID id(int number) {
        return new UUID(0, number);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserProfileCommandRepository.class)
    @Import({CcmsRoleSyncService.class, CcmsRoleSyncProfileService.class, RoleChangeNotificationService.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            var source = new PGSimpleDataSource();
            source.setURL(POSTGRES.getJdbcUrl());
            source.setUser(POSTGRES.getUsername());
            source.setPassword(POSTGRES.getPassword());
            return source;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
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
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        SqsClientRegistry registry() {
            return mock(SqsClientRegistry.class);
        }

        @Bean
        SqsClient sqs() {
            return mock(SqsClient.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }
}
