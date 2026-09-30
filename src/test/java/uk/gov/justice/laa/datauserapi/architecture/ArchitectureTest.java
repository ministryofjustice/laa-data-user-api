package uk.gov.justice.laa.datauserapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
    static final ArchRule query_services_must_be_read_only = classes()
            .that().resideInAPackage("..application.query.service..")
            .should(new ArchCondition<>("be transactional and read-only") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean readOnly = item.isAnnotatedWith(Transactional.class)
                            && item.getAnnotationOfType(Transactional.class).readOnly();
                    String message = item.getName() + " must declare @Transactional(readOnly = true)";
                    events.add(new SimpleConditionEvent(item, readOnly, message));
                }
            });

    @ArchTest
    static final ArchRule query_services_must_not_depend_on_persistence_entities = noClasses()
            .that().resideInAPackage("..application.query.service..")
            .should().dependOnClassesThat().resideInAPackage(ENTITY_PACKAGE)
            .because("Query services should return query DTOs rather than expose persistence entities");

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
                    "..repository.."
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

            .whereLayer("QueryController").mayNotBeAccessedByAnyLayer()
            .whereLayer("QueryService").mayOnlyBeAccessedByLayers("QueryController")
            .whereLayer("QueryRepository").mayOnlyBeAccessedByLayers("QueryService");

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
}
