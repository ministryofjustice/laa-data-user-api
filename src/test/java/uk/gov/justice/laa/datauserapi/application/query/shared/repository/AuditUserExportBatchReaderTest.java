package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.EntityManagerProxy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportBatch;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditUserExportMapper;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.integration.BaseIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditUserExportBatchReaderTest {

    /**
     * Pure unit coverage of the keyset cursor-advance logic and the {@code EntityManager.clear()}
     * call, isolated from real persistence so the invariant under test (clear exactly once, after
     * mapping, cursor set to the last returned id) fails loudly if any of those is removed.
     */
    @ExtendWith(MockitoExtension.class)
    static class CursorAndClearBehaviour {

        private static final AuditUserExportCriteria CRITERIA =
                new AuditUserExportCriteria(null, null, null, null, null, null, null);

        @Mock
        private AuditUserExportQueryRepository repository;
        @Mock
        private AuditUserExportMapper mapper;
        @Mock
        private EntityManager entityManager;

        private AuditUserExportBatchReader batchReader;

        @BeforeEach
        void setUp() {
            batchReader = new AuditUserExportBatchReader(repository, mapper, entityManager);
        }

        @Test
        void readBatch_returnsLastSeenIdAndClearsPersistenceContextAfterMapping() {
            UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
            List<UUID> ids = List.of(firstId, secondId);
            AuditUserExportRow row = new AuditUserExportRow(
                    "Name", "email@example.com", "Firm", "F1", "No", "No", "App", "ACTIVE", "Role");

            when(repository.findMatchingProfileIds(CRITERIA, null, Limit.of(2))).thenReturn(ids);
            when(repository.findDetailsWithRoles(ids)).thenReturn(List.of());
            when(mapper.toRows(ids, List.of())).thenReturn(List.of(row, row));

            AuditUserExportBatch batch = batchReader.readBatch(CRITERIA, null, 2);

            assertThat(batch.rows()).containsExactly(row, row);
            assertThat(batch.lastSeenId()).isEqualTo(secondId);
            verify(entityManager).clear();
        }

        @Test
        void readBatch_withNoMatches_returnsPreviousCursorAndStillClears() {
            UUID previousCursor = UUID.fromString("00000000-0000-0000-0000-000000000009");
            when(repository.findMatchingProfileIds(CRITERIA, previousCursor, Limit.of(500))).thenReturn(List.of());

            AuditUserExportBatch batch = batchReader.readBatch(CRITERIA, previousCursor, 500);

            assertThat(batch.rows()).isEmpty();
            assertThat(batch.lastSeenId()).isEqualTo(previousCursor);
            verify(entityManager).clear();
        }
    }

    /**
     * Proves the actual JPA-session-level invariant the fix depends on through the real
     * application context and its real, production-wired {@link EntityManagerFactory}: with one
     * {@link EntityManager} bound to the thread for the whole test — exactly what
     * {@code OpenEntityManagerInViewFilter} does for the whole HTTP request in production, since
     * {@code spring.jpa.open-in-view} is left at its Spring Boot default ({@code true}) — the
     * number of entities Hibernate still manages after each batch stays bounded instead of
     * growing with every batch read.
     */
    @SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:silas-status-refresh-schema.sql"
    })
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    static class DetachmentBehaviour extends BaseIntegrationTest {

        private static final UUID FIRM_ID = new UUID(0, 10);

        @Autowired
        private AuditUserExportBatchReader batchReader;
        @Autowired
        private EntityManagerFactory entityManagerFactory;
        @Autowired
        private JdbcTemplate jdbc;
        @MockitoBean
        private TechServicesClient client;
        @MockitoBean(name = "jwtDecoder")
        private JwtDecoder decoder;

        private EntityManager requestScopedEntityManager;

        @BeforeEach
        void seed() {
            jdbc.execute("TRUNCATE entra_user, user_profile, firm, app, app_role, office CASCADE");
            jdbc.update(
                    "INSERT INTO firm (id, name, code, type, enabled) VALUES (?, 'Firm', 'F1', 'LEGAL_SERVICES_PROVIDER', TRUE)",
                    FIRM_ID);
            for (int i = 0; i < 4; i++) {
                UUID userId = new UUID(0, 100 + i);
                UUID profileId = new UUID(0, 200 + i);
                jdbc.update("""
                    INSERT INTO entra_user (id, entra_oid, first_name, last_name, email, status, enabled, invitation_status)
                    VALUES (?, ?, 'First', 'Last', ?, 'ACTIVE', TRUE, 'VERIFICATION_SUCCESS')
                    """, userId, userId.toString(), "user" + i + "@example.test");
                jdbc.update("""
                    INSERT INTO user_profile (id, entra_user_id, user_type, active_profile, firm_id, silas_status,
                        unrestricted_office_access)
                    VALUES (?, ?, 'EXTERNAL', TRUE, ?, 'COMPLETE', FALSE)
                    """, profileId, userId, FIRM_ID);
            }

            // Simulates Open EntityManager in View: one EntityManager bound to this thread for
            // every batch's transaction, just as OpenEntityManagerInViewFilter binds one for the
            // whole HTTP request. Without this, each @Transactional readBatch call would get its
            // own short-lived EntityManager and the bug this test targets could not occur.
            requestScopedEntityManager = entityManagerFactory.createEntityManager();
            TransactionSynchronizationManager.bindResource(
                    entityManagerFactory, new EntityManagerHolder(requestScopedEntityManager));
        }

        @AfterEach
        void tearDown() {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestScopedEntityManager.close();
        }

        @Test
        void readingMultipleBatches_keepsPersistenceContextBoundedToOneBatch() {
            AuditUserExportCriteria criteria = new AuditUserExportCriteria(null, null, null, null, null, null, null);

            AuditUserExportBatch firstBatch = batchReader.readBatch(criteria, null, 2);
            int entitiesAfterFirstBatch = managedEntityCount();

            AuditUserExportBatch secondBatch = batchReader.readBatch(criteria, firstBatch.lastSeenId(), 2);
            int entitiesAfterSecondBatch = managedEntityCount();

            assertThat(firstBatch.rows()).hasSize(2);
            assertThat(secondBatch.rows()).hasSize(2);
            // Each batch's entities are detached by the end of readBatch, so retention does not
            // accumulate across the shared, request-scoped persistence context; without the fix
            // entitiesAfterSecondBatch would be roughly double entitiesAfterFirstBatch.
            assertThat(entitiesAfterFirstBatch).isZero();
            assertThat(entitiesAfterSecondBatch).isZero();
        }

        private int managedEntityCount() {
            EntityManager rawEntityManager = ((EntityManagerProxy) requestScopedEntityManager).getTargetEntityManager();
            Session session = rawEntityManager.unwrap(Session.class);
            PersistenceContext persistenceContext = ((SessionImplementor) session).getPersistenceContext();
            return persistenceContext.getNumberOfManagedEntities();
        }
    }
}
