package uk.gov.justice.laa.datauserapi.security.opa.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import uk.gov.justice.laa.datauserapi.application.command.handler.ProfileRoleChangeHandler;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleNotificationDetails;
import uk.gov.justice.laa.datauserapi.application.command.service.ProfileRoleChangePersistenceService;
import uk.gov.justice.laa.datauserapi.application.command.service.ProfileRoleChangeService;
import uk.gov.justice.laa.datauserapi.application.command.service.RoleChangeNotificationService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ProfileRoleChangeRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.application.query.handler.ProfileTrialQueryHandler;
import uk.gov.justice.laa.datauserapi.application.query.mapper.ProfileTrialReadMapper;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.ProfileTrialQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.ProfileTrialQueryRepository;
import uk.gov.justice.laa.datauserapi.application.shared.authorisation.ProfileTrialInputResolver;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialFactsLoader;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialRoleViewMapper;
import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.application.shared.repository.ProfileTrialFactsRepository;
import uk.gov.justice.laa.datauserapi.config.MapperConfig;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.ProfileSearchCriteria;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.exception.ProfileRoleConflictException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringJUnitConfig(ProfileTrialDatabaseIntegrationTest.Config.class)
class ProfileTrialDatabaseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("silas-status-refresh-schema.sql");
    @Container
    static final GenericContainer<?> OPA = new GenericContainer<>("openpolicyagent/opa:0.68.0")
            .withFileSystemBind(Path.of("opa/policies").toAbsolutePath().toString(), "/policies", BindMode.READ_ONLY)
            .withExposedPorts(8181).withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies");
    private static final UUID ACTOR = new UUID(0, 1);
    private static final UUID TARGET = new UUID(0, 2);
    private static final UUID OTHER = new UUID(0, 3);
    private static final UUID FIRM = new UUID(1, 1);
    private static final UUID OTHER_FIRM = new UUID(1, 2);
    private static final UUID APP = new UUID(2, 1);
    private static final UUID OTHER_APP = new UUID(2, 2);
    private static final UUID MANAGER = new UUID(3, 1);
    private static final UUID ROLE = new UUID(3, 2);
    private static final UUID OTHER_ROLE = new UUID(3, 3);
    private static final AtomicReference<Runnable> AFTER_OPA = new AtomicReference<>();
    private static final AtomicReference<Runnable> AFTER_SEARCH = new AtomicReference<>();
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProfileTrialQueryHandler queries;
    @Autowired private ProfileRoleChangeHandler commands;
    @Autowired private ProfileRoleChangeService changes;
    @Autowired private ProfileTrialFactsLoader loader;
    @Autowired private OpaAuthorizationClient client;
    @Autowired private JavaProfileTrialRules javaRules;
    @Autowired private RoleChangeNotificationService notifications;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private AuthenticatedEntraUser actor;

    @BeforeEach
    void seed() {
        org.mockito.Mockito.reset(notifications);
        jdbc.execute("CREATE TABLE IF NOT EXISTS role_assignment (id UUID PRIMARY KEY, assigning_role_id UUID, assignable_role_id UUID)");
        jdbc.execute("TRUNCATE role_assignment, user_profile_app_role, user_profile_office, role_permission,"
                + " app_role, user_profile, entra_user, office, firm, app CASCADE");
        jdbc.update("INSERT INTO firm(id,name,code,type,enabled) VALUES (?, 'Firm A','123','LEGAL_SERVICES_PROVIDER',true),"
                + "(?, 'Firm B','456','CHAMBERS',true)", FIRM, OTHER_FIRM);
        for (UUID id : List.of(ACTOR, TARGET, OTHER)) {
            jdbc.update("INSERT INTO entra_user(id,entra_oid,first_name,last_name,email,status,enabled)"
                    + " VALUES (?,?,'First','Last',?,'ACTIVE',true)", id, id.toString(), id + "@example.test");
            jdbc.update("INSERT INTO user_profile(id,entra_user_id,user_type,active_profile,legacy_user_id,firm_id,silas_status)"
                    + " VALUES (?,?,'EXTERNAL',true,?,?,'COMPLETE')", id, id, id, id.equals(OTHER) ? OTHER_FIRM : FIRM);
        }
        for (UUID id : List.of(APP, OTHER_APP)) {
            jdbc.update("INSERT INTO app(id,name,app_type,ordinal,enabled,entra_oid) VALUES (?,?,'LAA',1,true,?)",
                    id, id.toString(), id.toString());
        }
        insertRole(MANAGER, APP, "Firm User Manager", true, false);
        insertRole(ROLE, APP, "Test role", false, true);
        insertRole(OTHER_ROLE, OTHER_APP, "Other role", false, true);
        for (String permission : List.of("VIEW_EXTERNAL_USER", "ASSIGN_EXTERNAL_USER_ROLES", "REMOVE_EXTERNAL_USER_ROLES")) {
            jdbc.update("INSERT INTO role_permission VALUES (?,?)", MANAGER, permission);
        }
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?,?)", ACTOR, MANAGER);
        actor = AuthenticatedEntraUser.builder().id(ACTOR).entraOid(ACTOR.toString())
                .activeProfile(AuthenticatedUserProfile.builder().id(ACTOR).userType(UserType.EXTERNAL).firmId(FIRM).build()).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, null, "SCOPE_user_data.admin"));
    }

    @AfterEach
    void cleanUp() {
        AFTER_OPA.set(null);
        AFTER_SEARCH.set(null);
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtersAreAppliedBeforePaginationAndClientFiltersCannotWidenAccess() {
        var page = queries.handle(query(ProfileTrialOperation.SEARCH, null, FIRM), actor).page();
        assertThat(page.page().totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(queries.handle(query(ProfileTrialOperation.SEARCH, null, OTHER_FIRM), actor).page().items()).isEmpty();
        assertThatThrownBy(() -> queries.handle(query(ProfileTrialOperation.DETAIL, OTHER, null), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queries.handle(query(ProfileTrialOperation.DETAIL, UUID.randomUUID(), null), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(queries.handle(query(ProfileTrialOperation.DETAIL, TARGET, null), actor).detail()).isNotNull();
    }

    @Test
    void batchedHydrationDoesNotLeakProfilesMovedToAnotherFirmAfterPageSelection() {
        AFTER_SEARCH.set(() -> {
            var separate = new TransactionTemplate(transactions);
            separate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            separate.executeWithoutResult(status -> jdbc.update("UPDATE user_profile SET firm_id=? WHERE id=?", OTHER_FIRM, ACTOR));
        });
        var result = queries.handle(query(ProfileTrialOperation.SEARCH, null, null), actor).page();
        assertThat(result.items()).singleElement().extracting(uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView::firmId)
                .isEqualTo(FIRM.toString());
        assertThat(jdbc.queryForObject("SELECT firm_id FROM user_profile WHERE id=?", UUID.class, ACTOR)).isEqualTo(OTHER_FIRM);
    }

    @Test
    void detailMappingDoesNotTriggerPerRoleOrUnrelatedProfileQueries() {
        for (int i = 10; i < 30; i++) {
            UUID id = new UUID(3, i);
            insertRole(id, APP, "Role " + i, false, false);
            jdbc.update("INSERT INTO user_profile_app_role VALUES (?,?)", TARGET, id);
        }
        UUID office = UUID.randomUUID();
        jdbc.update("INSERT INTO office(id,code,firm_id) VALUES (?,'123456',?)", office, FIRM);
        jdbc.update("INSERT INTO user_profile_office VALUES (?,?)", TARGET, office);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var detail = queries.handle(query(ProfileTrialOperation.DETAIL, TARGET, null), actor).detail();
            assertThat(detail.roles()).hasSize(20);
            assertThat(detail.offices()).hasSize(1);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void commitsRoleChangesBeforeSendingNotificationsAndSkipsNoOps() {
        org.mockito.Mockito.when(notifications.sendMessage(
                org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anySet()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return true;
                });
        commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE, OTHER_ROLE), Map.of()), actor);
        assertThat(roleCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_sync_successful FROM user_profile WHERE id=?", Boolean.class, TARGET))
                .isTrue();
        org.mockito.Mockito.verify(notifications).sendMessage(org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(),
                org.mockito.ArgumentMatchers.eq(APP.toString()), org.mockito.ArgumentMatchers.eq(Set.of("ROLE_A")),
                org.mockito.ArgumentMatchers.eq(Set.of()));
        org.mockito.Mockito.verify(notifications).sendMessage(org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(),
                org.mockito.ArgumentMatchers.eq(OTHER_APP.toString()), org.mockito.ArgumentMatchers.eq(Set.of("ROLE_B")),
                org.mockito.ArgumentMatchers.eq(Set.of()));
        commands.handle(command(ProfileTrialOperation.UPDATE, Set.of(), Map.of(APP, Set.of())), actor);
        assertThat(jdbc.queryForList("SELECT app_role_id FROM user_profile_app_role WHERE user_profile_id=?", UUID.class, TARGET))
                .containsExactly(OTHER_ROLE);
        org.mockito.Mockito.verify(notifications).sendMessage(org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(),
                org.mockito.ArgumentMatchers.eq(APP.toString()), org.mockito.ArgumentMatchers.eq(Set.of()),
                org.mockito.ArgumentMatchers.eq(Set.of("ROLE_A")));
        commands.handle(command(ProfileTrialOperation.UPDATE, Set.of(), Map.of(APP, Set.of())), actor);
        org.mockito.Mockito.verify(notifications, org.mockito.Mockito.times(3)).sendMessage(
                org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void rollbackDoesNotLeavePartialRoleChangesWhenNotificationDetailsAreInvalid() {
        jdbc.update("UPDATE user_profile SET legacy_user_id=NULL WHERE id=?", TARGET);
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor))
                .isInstanceOf(CcmsNotificationException.class);
        assertThat(roleCount()).isZero();
    }

    @Test
    void rechecksChangedTargetAndRevokedActorFactsBeforeCommitting() {
        AFTER_OPA.set(() -> jdbc.update("UPDATE user_profile SET firm_id=? WHERE id=?", OTHER_FIRM, TARGET));
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor))
                .isInstanceOf(ProfileRoleConflictException.class);
        assertThat(roleCount()).isZero();
        jdbc.update("UPDATE user_profile SET firm_id=? WHERE id=?", FIRM, TARGET);
        AFTER_OPA.set(() -> jdbc.update("UPDATE entra_user SET enabled=false WHERE id=?", ACTOR));
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor))
                .isInstanceOf(ProfileRoleConflictException.class);
    }

    @Test
    void sameProfileUpdatesDoNotFailBecauseOfDuplicateLockIdentifiers() {
        var request = new ProfileTrialRequest(ProfileTrialOperation.ASSIGN, ACTOR, Set.of(ROLE), Map.of(), actor);
        commands.handle(new ProfileRoleChangeCommand(request, null), actor);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile_app_role WHERE user_profile_id=?", Long.class, ACTOR))
                .isEqualTo(2);
    }

    @Test
    void failedPostCommitNotificationLeavesSyncFlagUnsuccessful() {
        org.mockito.Mockito.when(notifications.sendMessage(
                org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anySet()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return false;
                });
        commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor);
        assertThat(roleCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_sync_successful FROM user_profile WHERE id=?", Boolean.class, TARGET))
                .isFalse();
        org.mockito.Mockito.verify(notifications).sendMessage(org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(),
                org.mockito.ArgumentMatchers.eq(APP.toString()), org.mockito.ArgumentMatchers.eq(Set.of("ROLE_A")),
                org.mockito.ArgumentMatchers.eq(Set.of()));
    }

    @Test
    void actualDeltasDetermineAssignAndRemovePermissions() {
        jdbc.update("INSERT INTO user_profile_app_role VALUES (?,?)", TARGET, ROLE);
        jdbc.update("DELETE FROM role_permission WHERE permission='ASSIGN_EXTERNAL_USER_ROLES'");
        commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor);
        org.mockito.Mockito.verifyNoInteractions(notifications);
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(OTHER_ROLE), Map.of()), actor))
                .isInstanceOf(AccessDeniedException.class);
        commands.handle(command(ProfileTrialOperation.UPDATE, Set.of(), Map.of(APP, Set.of())), actor);
        assertThat(roleCount()).isZero();
        org.mockito.Mockito.verify(notifications).sendMessage(org.mockito.ArgumentMatchers.<RoleNotificationDetails>any(),
                org.mockito.ArgumentMatchers.eq(APP.toString()), org.mockito.ArgumentMatchers.eq(Set.of()),
                org.mockito.ArgumentMatchers.eq(Set.of("ROLE_A")));
    }

    @Test
    void trustedRoleCompatibilityAndAssignmentRestrictionsAffectQueriesAndWrites() {
        jdbc.update("UPDATE app_role SET user_type_restriction=ARRAY['INTERNAL'] WHERE id=?", ROLE);
        jdbc.update("INSERT INTO role_assignment VALUES (?,?,?)", UUID.randomUUID(), OTHER_ROLE, OTHER_ROLE);
        var assignable = queries.handle(query(ProfileTrialOperation.ASSIGNABLE, TARGET, null), actor).roles();
        assertThat(assignable.items()).extracting(uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView::appRoleId)
                .contains(MANAGER.toString()).doesNotContain(ROLE.toString(), OTHER_ROLE.toString());
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> commands.handle(command(ProfileTrialOperation.ASSIGN, Set.of(OTHER_ROLE), Map.of()), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(roleCount()).isZero();
    }

    @Test
    void concurrentProtectedTransitionsRejectAnOutdatedSnapshot() throws Exception {
        var original = command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of());
        var input = loader.load(original.request());
        var authorised = original.withAuthorizationContext(new ProfileTrialPlan(input, javaRules.evaluate(input)));
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM user_profile WHERE id=? FOR UPDATE", UUID.class, TARGET);
                locked.countDown();
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release profile lock");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                jdbc.update("UPDATE user_profile SET firm_id=? WHERE id=?", OTHER_FIRM, TARGET);
            }));
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var waiting = executor.submit(() -> changes.change(authorised));
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock'", Long.class) == 0
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertThat(waiting.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            holder.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(() -> waiting.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
        } finally {
            release.countDown();
        }
        assertThat(roleCount()).isZero();
    }

    @Test
    void realOpaAndJavaAgreeAcrossRolesOperationsUserTypesAndRestrictions() {
        var base = loader.load(command(ProfileTrialOperation.ASSIGN, Set.of(ROLE), Map.of()).request());
        for (String role : List.of("Global Admin", "External User Admin", "Internal User Manager", "External User Manager",
                "Internal User Viewer", "External User Viewer", "Firm User Manager", "Security Response", "Audit Export",
                "Multi-firm Delegation", "External User Support", "SILAS System Administration", "Unknown")) {
            Set<Permission> permissions = client.callerPermissions(List.of(role)).orElse(List.of()).stream()
                    .map(Permission::valueOf).collect(Collectors.toSet());
            for (UserType type : UserType.values()) {
                var identity = new ProfileTrialInput.Actor(ACTOR, ACTOR, ACTOR.toString(), type, FIRM,
                        Set.of(role), Set.of(MANAGER), permissions, true);
                for (UserType targetType : UserType.values()) {
                    var target = new ProfileTrialInput.Target(TARGET, targetType,
                            targetType == UserType.INTERNAL ? null : FIRM, base.target().firmType(), Set.of());
                    for (ProfileTrialOperation operation : ProfileTrialOperation.values()) {
                        var input = new ProfileTrialInput(operation, identity, operation == ProfileTrialOperation.SEARCH ? null : target,
                                base.roles(), Set.of(ROLE), Set.of(), true);
                        assertThat(client.profileDecision(input)).as("%s/%s/%s/%s", role, type, targetType, operation)
                                .isEqualTo(javaRules.evaluate(input));
                    }
                }
            }
        }
    }

    private ProfileRoleChangeCommand command(ProfileTrialOperation operation, Set<UUID> roles, Map<UUID, Set<UUID>> apps) {
        return new ProfileRoleChangeCommand(new ProfileTrialRequest(operation, TARGET, roles, apps, actor), null);
    }

    private ProfileTrialQuery query(ProfileTrialOperation operation, UUID id, UUID firm) {
        var criteria = new ProfileSearchCriteria(0, 1, null, null, null, firm, null, null, null, null, null, null, null);
        return new ProfileTrialQuery(new ProfileTrialRequest(operation, id, Set.of(), Map.of(), actor), criteria, null);
    }

    private void insertRole(UUID id, UUID app, String name, boolean authz, boolean sync) {
        jdbc.update("INSERT INTO app_role(id,app_id,name,ordinal,authz_role,legacy_sync,role_identifier)"
                + " VALUES (?,?,?,1,?,?,?)", id, app, name, authz, sync, id.equals(MANAGER) ? "MANAGER" : id.equals(ROLE) ? "ROLE_A" : "ROLE_B");
    }

    private long roleCount() {
        return jdbc.queryForObject("SELECT count(*) FROM user_profile_app_role WHERE user_profile_id=?", Long.class, TARGET);
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = {ProfileRoleChangeRepository.class, ProfileTrialQueryRepository.class,
        ProfileTrialFactsRepository.class})
    @Import({RbacMethodSecurityConfig.class, ProfileTrialDecisionPolicy.class, ProfileTrialInputResolver.class,
        ProfileTrialFactsLoader.class, ProfileTrialRoleViewMapper.class, ProfileRoleChangeService.class,
        ProfileRoleChangePersistenceService.class,
        ProfileRoleChangeHandler.class,
        ProfileTrialQueryHandler.class, ProfileTrialQueryService.class, ProfileTrialReadMapper.class,
        GetActiveProfileQueryMapper.class, MapperConfig.class, JavaProfileTrialRules.class, ProfileTrialAuthorizer.class})
    static class Config {
        @Bean
        @org.springframework.context.annotation.Primary
        ProfileTrialQueryRepository observedQueries(
                @org.springframework.beans.factory.annotation.Qualifier("profileTrialQueryRepository") ProfileTrialQueryRepository delegate) {
            var factory = new org.springframework.aop.framework.ProxyFactory(delegate);
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                var result = invocation.proceed();
                if (invocation.getMethod().getName().equals("findAll")) {
                    var hook = AFTER_SEARCH.getAndSet(null);
                    if (hook != null) {
                        hook.run();
                    }
                }
                return result;
            });
            return (ProfileTrialQueryRepository) factory.getProxy();
        }

        @Bean DataSource dataSource() {
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setURL(POSTGRES.getJdbcUrl());
            source.setUser(POSTGRES.getUsername());
            source.setPassword(POSTGRES.getPassword());
            return source;
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
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
        ObjectMapper jsonMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        Clock profileTrialClock() {
            return Clock.systemUTC();
        }

        @Bean
        @org.springframework.context.annotation.Primary
        RoleChangeNotificationService notifications() {
            return org.mockito.Mockito.mock(RoleChangeNotificationService.class);
        }

        @Bean
        ProfileTrialConfiguration.Engines engines() {
            return new ProfileTrialConfiguration.Engines(true, true);
        }

        @Bean
        OpaAuthorizationClient client() {
            return new OpaAuthorizationClient(RestClient.builder()
                    .baseUrl("http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181))
                    .requestInterceptor((request, body, execution) -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        var response = execution.execute(request, body);
                        var hook = AFTER_OPA.getAndSet(null);
                        if (hook != null) {
                            hook.run();
                        }
                        return response;
                    }).build());
        }
    }
}
