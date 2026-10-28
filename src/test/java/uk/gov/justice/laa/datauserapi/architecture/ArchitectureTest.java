package uk.gov.justice.laa.datauserapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandler;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;
import uk.gov.justice.laa.datauserapi.security.RequiresSystemAdminScope;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "uk.gov.justice.laa.datauserapi",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    private static final String COMMAND_PACKAGE = "..application.command..";
    private static final String QUERY_PACKAGE = "..application.query..";
    private static final String CONTRACTS_PACKAGE = "..contracts..";
    private static final String ENTITY_PACKAGE = "..entity..";
    private static final String CLIENT_PACKAGE = "..client..";

    // =========================================================================
    // 1. CQRS Lite Strict Segregation Rules
    // =========================================================================

    @ArchTest
    static final ArchRule command_side_must_not_depend_on_query_side = noClasses()
            .that().resideInAPackage(COMMAND_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(QUERY_PACKAGE)
            .because("In CQRS Lite, Command side must be strictly isolated from Query side");

    @ArchTest
    static final ArchRule query_side_must_not_depend_on_command_side = noClasses()
            .that().resideInAPackage(QUERY_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(COMMAND_PACKAGE)
            .because("In CQRS Lite, Query side must not depend on Command execution or state modification");

    @ArchTest
    static final ArchRule query_side_must_not_access_external_mutation_clients = noClasses()
            .that().resideInAPackage(QUERY_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(CLIENT_PACKAGE)
            .because("Query side should read from read-models/views, not trigger external client integration");

    @ArchTest
    static final ArchRule query_repositories_must_not_expose_write_operations = noClasses()
            .that().resideInAPackage("..application.query.shared.repository..")
            .should().beAssignableTo(CrudRepository.class)
            .because("CQRS-lite query repositories expose read operations only");

    @ArchTest
    static final ArchRule query_services_and_handlers_must_be_read_only = classes()
            .that().resideInAnyPackage("..application.query.service..", "..application.query.handler..")
            .should(declareReadOnlyTransaction());

    @ArchTest
    static final ArchRule query_repository_methods_must_be_reads = classes()
            .that().resideInAPackage("..application.query.shared.repository..")
            .should(exposeOnlyReadMethods());

    @ArchTest
    static final ArchRule query_transaction_overrides_must_be_read_only = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(QUERY_PACKAGE)
            .should(useOnlyReadOnlyTransactions());

    @ArchTest
    static final ArchRule read_models_must_not_contain_entities = classes()
            .that().resideInAnyPackage("..application.query.dto..", "..dto..", "..contracts.response..")
            .should(containNoPersistenceEntities());

    @ArchTest
    static final ArchRule query_results_must_not_contain_entities = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(
                    "..application.query.service..", "..application.query.handler..", "..application.query.controller..")
            .and().arePublic()
            .should(returnNoPersistenceEntities());

    @ArchTest
    static final ArchRule query_services_must_not_depend_on_persistence_entities = noClasses()
            .that().resideInAPackage("..application.query.service..")
            .should().dependOnClassesThat().resideInAPackage(ENTITY_PACKAGE)
            .because("Query services should return query DTOs rather than expose persistence entities");

    @ArchTest
    static final ArchRule query_services_must_delegate_entity_mapping = noClasses()
            .that().resideInAPackage("..application.query.service..")
            .should().dependOnClassesThat().resideInAPackage("org.modelmapper..")
            .because("Entity mapping belongs in query mappers, not services");

    // =========================================================================
    // 2. Layer & Access Boundary Rules
    // =========================================================================

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("Controllers must delegate to handlers/services and never interact directly with database repositories");

    @ArchTest
    static final ArchRule repositories_should_only_be_accessed_by_handlers_services_and_mappers = classes()
            .that().resideInAPackage("..repository..")
            .should().onlyBeAccessed().byAnyPackage(
                    "..application.command.handler..",
                    "..application.command.service..",
                    "..application.command.mapper..",
                    "..application.query.service..",
                    "..application.query.handler..",
                    "..repository..",
                    "..authorisation.."
            )
            .because("Repositories should only be accessed by application services, handlers, or mappers");

    @ArchTest
    static final ArchRule contracts_must_not_depend_on_persistence_entities = noClasses()
            .that().resideInAPackage(CONTRACTS_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(ENTITY_PACKAGE)
            .because("Contracts module is an API boundary contract and must remain decoupled from JPA entity models");

    // =========================================================================
    // 3. Command Side Structural Layering
    // =========================================================================

    @ArchTest
    static final ArchRule command_subsystem_layered_architecture = layeredArchitecture()
            .consideringAllDependencies()
            .layer("CommandController").definedBy("..application.command.controller..")
            .layer("CommandHandler").definedBy("..application.command.handler..")
            .layer("CommandMapper").definedBy("..application.command.mapper..")
            .layer("CommandService").definedBy("..application.command.service..")
            .layer("CommandRepository").definedBy("..application.command.shared.repository..")

            .whereLayer("CommandController").mayNotBeAccessedByAnyLayer()
            .whereLayer("CommandHandler").mayOnlyBeAccessedByLayers("CommandController")
            .whereLayer("CommandRepository").mayOnlyBeAccessedByLayers("CommandHandler", "CommandService", "CommandMapper");

    // =========================================================================
    // 4. Query Side Structural Layering
    // =========================================================================

    @ArchTest
    static final ArchRule query_subsystem_layered_architecture = layeredArchitecture()
            .consideringAllDependencies()
            .layer("QueryController").definedBy("..application.query.controller..")
            .layer("QueryService").definedBy("..application.query.service..")
            .layer("QueryRepository").definedBy("..application.query.shared.repository..")
            .layer("QueryHandler").definedBy("..application.query.handler..")
            .layer("Authorisation").definedBy("..authorisation..")

            .whereLayer("QueryController").mayNotBeAccessedByAnyLayer()
            .whereLayer("QueryService").mayOnlyBeAccessedByLayers("QueryController", "QueryHandler")
            .whereLayer("QueryRepository").mayOnlyBeAccessedByLayers("QueryService", "Authorisation", "QueryHandler");

    // =========================================================================
    // 5. Naming Conventions & Stereotype Annotations
    // =========================================================================

    @ArchTest
    static final ArchRule controllers_must_reside_in_controller_package = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..")
            .andShould().beAnnotatedWith(RestController.class)
            .orShould().beAnnotatedWith(Controller.class);

    @ArchTest
    static final ArchRule command_handlers_must_implement_command_handler_interface = classes()
            .that().resideInAPackage("..application.command.handler..")
            .and().haveSimpleNameEndingWith("Handler")
            .and().doNotHaveSimpleName("CommandHandler") // Exclude the interface itself
            .should().implement(CommandHandler.class)
            .andShould().beAnnotatedWith(Component.class);

    @ArchTest
    static final ArchRule services_must_reside_in_service_package = classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..")
            .andShould().beAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule repositories_must_reside_in_repository_package = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..repository..");

    // =========================================================================
    // 6. General Spring & Clean Code Rules
    // =========================================================================

    @ArchTest
    static final ArchRule no_autowired_field_injection_should_be_used = noFields()
            .should().beAnnotatedWith(Autowired.class);


    @ArchTest
    static final ArchRule application_packages_are_free_of_cycles =
            slices().matching("uk.gov.justice.laa.datauserapi.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllers_do_not_access_persistence_details =
            noClasses().that().resideInAPackage("..application..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..entity..", "..repository..");

    @ArchTest
    static final ArchRule entities_do_not_depend_on_application_code =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application..");

    // =========================================================================
    // 7. Security Scope Rules
    // =========================================================================

    @ArchTest
    static final ArchRule rest_controllers_must_declare_a_required_scope = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(PreAuthorize.class)
            .orShould().beMetaAnnotatedWith(PreAuthorize.class)
            .because("Every REST controller must declare its required scope via @RequiresReadScope or @RequiresAdminScope");

    @ArchTest
    static final ArchRule command_controllers_must_require_admin_scope = classes()
            .that().resideInAPackage("..application.command.controller..")
            .and().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(RequiresAdminScope.class)
            .orShould().beMetaAnnotatedWith(RequiresAdminScope.class)
            .orShould().beAnnotatedWith(RequiresSystemAdminScope.class)
            .because("Command endpoints mutate state and must require the user_data.admin or system.admin scope");

    @ArchTest
    static final ArchRule query_controllers_must_require_read_scope = classes()
            .that().resideInAPackage("..application.query.controller..")
            .and().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(RequiresReadScope.class)
            .orShould().beMetaAnnotatedWith(RequiresReadScope.class)
            .because("Query endpoints must declare the user_data.read scope requirement");

    static ArchCondition<JavaClass> exposeOnlyReadMethods() {
        return new ArchCondition<>("expose only read repository methods") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getAllMethods()) {
                    if (method.getOwner().isEquivalentTo(Object.class)) {
                        continue;
                    }
                    boolean read = method.getName().matches("(find|read|get|query|search|stream|count|exists).*")
                            && !method.isAnnotatedWith(Modifying.class)
                            && !method.isAnnotatedWith(Procedure.class);
                    if (method.isAnnotatedWith(Query.class)) {
                        Query query = method.getAnnotationOfType(Query.class);
                        read &= isSelectQuery(query.value()) && isSelectQuery(query.countQuery())
                                && query.name().isBlank() && query.countName().isBlank();
                    }
                    events.add(new SimpleConditionEvent(method, read,
                            method.getFullName() + " must expose a read operation without mutation SQL or @Modifying"));
                }
            }
        };
    }

    private static boolean isSelectQuery(String query) {
        return query.isBlank() || query.stripLeading().toLowerCase(Locale.ROOT).matches("select\\s[\\s\\S]*");
    }

    static ArchCondition<JavaClass> declareReadOnlyTransaction() {
        return new ArchCondition<>("declare a read-only transaction") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean readOnly = item.isAnnotatedWith(Transactional.class)
                        && item.getAnnotationOfType(Transactional.class).readOnly()
                        && !item.isAnnotatedWith(jakarta.transaction.Transactional.class);
                events.add(new SimpleConditionEvent(item, readOnly,
                        item.getName() + " must declare @Transactional(readOnly = true)"));
                item.getAllMethods().forEach(method -> useOnlyReadOnlyTransactions().check(method, events));
            }
        };
    }

    static ArchCondition<JavaMethod> useOnlyReadOnlyTransactions() {
        return new ArchCondition<>("use only read-only transaction overrides") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean readOnly = !method.isAnnotatedWith(jakarta.transaction.Transactional.class)
                        && (!method.isAnnotatedWith(Transactional.class)
                        || method.getAnnotationOfType(Transactional.class).readOnly());
                events.add(new SimpleConditionEvent(method, readOnly,
                        method.getFullName() + " must not declare a writable query transaction"));
            }
        };
    }

    static ArchCondition<JavaClass> containNoPersistenceEntities() {
        return new ArchCondition<>("contain no persistence entities, including nested generic fields") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                checkEntityGraph(item, item, new HashSet<>(), events);
            }
        };
    }

    static ArchCondition<JavaMethod> returnNoPersistenceEntities() {
        return new ArchCondition<>("return no persistence entities, including nested generic fields") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                checkEntityGraph(method.getReturnType(), method, new HashSet<>(), events);
            }
        };
    }

    private static void checkEntityGraph(JavaType type, Object origin, Set<String> visited, ConditionEvents events) {
        for (JavaClass rawType : type.getAllInvolvedRawTypes()) {
            if (!visited.add(rawType.getName())) {
                continue;
            }
            if (rawType.isArray()) {
                checkEntityGraph(rawType.getComponentType(), origin, visited, events);
                continue;
            }
            boolean entity = rawType.isAnnotatedWith(jakarta.persistence.Entity.class)
                    || rawType.getPackageName().startsWith("uk.gov.justice.laa.datauserapi.entity");
            if (entity) {
                events.add(SimpleConditionEvent.violated(origin,
                        origin + " exposes persistence entity " + rawType.getName()));
            } else if (rawType.getPackageName().startsWith("uk.gov.justice.laa.datauserapi")) {
                rawType.getAllFields().stream()
                        .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                        .forEach(field -> checkEntityGraph(field.getType(), origin, visited, events));
                rawType.getAllMethods().stream()
                        .filter(method -> !method.getModifiers().contains(JavaModifier.STATIC))
                        .forEach(method -> checkEntityGraph(method.getReturnType(), origin, visited, events));
            }
        }
    }
}
