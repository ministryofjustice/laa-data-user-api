package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@SpringJUnitConfig(AuditQueryRepositoryTest.Config.class)
@Testcontainers
class AuditQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("audit-query-schema.sql");

    private static final UUID USER_ID = new UUID(0, 1);
    private static final String USER_OID = new UUID(1, 1).toString();
    private static final UUID REASON_ID = new UUID(0, 50);
    private static final UUID DELETE_REASON_ID = new UUID(0, 51);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private AuditQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE user_account_status_audit, entra_user, disable_user_reason, delete_user_reason CASCADE");
        jdbc.update("""
            INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled)
            VALUES (?, ?, 'First', 'Last', 'user@example.test', 'ACTIVE', TRUE)
            """, USER_ID, USER_OID);
        jdbc.update("INSERT INTO disable_user_reason (id, name, description, entra_description) VALUES (?, 'Cyber Risk', 'Account deactivated because of a cyber risk', 'CyberRisk')",
                REASON_ID);
        jdbc.update("INSERT INTO delete_user_reason (id, code, label) VALUES (?, 'LeftFirm', 'Left firm')", DELETE_REASON_ID);
    }

    @Test
    void historyIsNewestFirstIncludesRowsWithoutReasonAndIgnoresOtherUsers() {
        insertAudit(UUID.randomUUID(), "ACTIVATED", BASE, USER_ID, null, null, null);
        insertAudit(UUID.randomUUID(), "DEACTIVATED", BASE.plusDays(1), USER_ID, "user@example.test", REASON_ID, null);
        insertAudit(UUID.randomUUID(), "DEACTIVATED", BASE.plusDays(2), UUID.randomUUID(), null, REASON_ID, null);

        List<AuditStatusHistoryRow> history = repository.findStatusHistory(USER_OID, PageRequest.of(0, 500));

        assertThat(history).extracting(AuditStatusHistoryRow::statusChange)
                .containsExactly(UserAccountAuditStatus.DEACTIVATED, UserAccountAuditStatus.ACTIVATED);
        assertThat(history.getFirst().deactivateReasonKey()).isEqualTo("CyberRisk");
        assertThat(history.getFirst().deactivateReasonLabel()).isEqualTo("Cyber Risk");
        assertThat(history.getFirst().userEntraObjectId()).isEqualTo(USER_OID);
        assertThat(history.getLast().deactivateReasonKey()).isNull();
    }

    @Test
    void historyIsLimitedByPageSize() {
        insertAudit(UUID.randomUUID(), "ACTIVATED", BASE, USER_ID, null, null, null);
        insertAudit(UUID.randomUUID(), "DEACTIVATED", BASE.plusDays(1), USER_ID, null, REASON_ID, null);

        assertThat(repository.findStatusHistory(USER_OID, PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    void onlyDeletedRowsAreReturnedNewestFirst() {
        insertDeleted("older@example.test", BASE);
        insertDeleted("newer@example.test", BASE.plusDays(1));
        insertAudit(UUID.randomUUID(), "DEACTIVATED", BASE.plusDays(2), UUID.randomUUID(), "deactivated@example.test",
                REASON_ID, null);

        Page<DeletedUserAuditRow> page = repository.findDeletedUsers("%", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(DeletedUserAuditRow::email)
                .containsExactly("newer@example.test", "older@example.test");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void deletedUsersReturnTheStoredSnapshot() {
        insertDeleted("external@example.test", BASE);

        DeletedUserAuditRow row = repository.findDeletedUsers("%", PageRequest.of(0, 20))
                .getContent().getFirst();

        assertThat(row.name()).isEqualTo("Deleted Person");
        assertThat(row.email()).isEqualTo("external@example.test");
        assertThat(row.deletedBy()).isEqualTo("deleter");
        assertThat(row.deletedDate()).isEqualTo(BASE);
        assertThat(row.deleteReasonCode()).isEqualTo("LeftFirm");
        assertThat(row.deleteReasonLabel()).isEqualTo("Left firm");
    }

    @Test
    void deletedByIsTheDeletingActorsEntraObjectIdWhenTheActorStillExists() {
        insertDeleted("external@example.test", BASE);
        jdbc.update("UPDATE user_account_status_audit SET status_changed_by = ?", USER_ID.toString());

        DeletedUserAuditRow row = repository.findDeletedUsers("%", PageRequest.of(0, 20))
                .getContent().getFirst();

        assertThat(row.deletedBy()).isEqualTo(USER_OID);
    }

    @Test
    void deletedUsersArePagedNewestFirst() {
        for (int i = 0; i < 5; i++) {
            insertDeleted("user" + i + "@example.test", BASE.plusDays(i));
        }

        Page<DeletedUserAuditRow> page = repository.findDeletedUsers("%", PageRequest.of(1, 2));

        assertThat(page.getContent()).extracting(DeletedUserAuditRow::email)
                .containsExactly("user2@example.test", "user1@example.test");
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    void emailPatternMatchesCaseInsensitivelyAndTreatsWildcardsLiterally() {
        insertDeleted("Alice.Smith@example.test", BASE);
        insertDeleted("bob_jones@example.test", BASE.plusDays(1));
        insertDeleted("bobXjones@example.test", BASE.plusDays(2));

        assertThat(emails("%alice%")).containsExactly("Alice.Smith@example.test");
        assertThat(emails("%bob\\_jones%")).containsExactly("bob_jones@example.test");
        assertThat(emails("%nobody%")).isEmpty();
    }

    private List<String> emails(String pattern) {
        return repository.findDeletedUsers(pattern, PageRequest.of(0, 20))
                .getContent().stream().map(DeletedUserAuditRow::email).toList();
    }

    private void insertDeleted(String email, LocalDateTime changed) {
        insertAudit(UUID.randomUUID(), "DELETED", changed, null, email, null, DELETE_REASON_ID);
    }

    private void insertAudit(UUID id, String status, LocalDateTime changed, UUID entraUserId, String email,
                             UUID disableReasonId, UUID deleteReasonId) {
        jdbc.update("""
            INSERT INTO user_account_status_audit (id, status_changed_date, status_changed_by, status_change,
                entra_user_id, user_email, user_name, disable_user_reason_id, delete_user_reason_id)
            VALUES (?, ?, 'deleter', ?, ?, ?, 'Deleted Person', ?, ?)
            """, id, Timestamp.valueOf(changed), status, entraUserId, email, disableReasonId, deleteReasonId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AuditQueryRepository.class)
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
