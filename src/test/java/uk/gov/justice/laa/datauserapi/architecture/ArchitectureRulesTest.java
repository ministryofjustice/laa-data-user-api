package uk.gov.justice.laa.datauserapi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRulesTest {

    @Test
    void rejectsDeclaredAndInheritedRepositoryWrites() {
        ArchRule rule = classes().should(ArchitectureTest.exposeOnlyReadMethods());
        assertViolation(rule, WriteRepository.class);
        assertViolation(rule, InheritedWriteRepository.class);
        assertViolation(rule, ModifyingRepository.class);
        assertViolation(rule, MutationQueryRepository.class);
        assertViolation(rule, MutationCountRepository.class);
        assertViolation(rule, ProcedureRepository.class);
        assertViolation(rule, NamedQueryRepository.class);
        rule.check(importClasses(ReadRepository.class));
    }

    @Test
    void requiresReadOnlyTransactionsOnQueryComponents() {
        ArchRule rule = classes().should(ArchitectureTest.declareReadOnlyTransaction());
        assertViolation(rule, WritableComponent.class);
        assertViolation(rule, MissingTransactionComponent.class);
        assertViolation(rule, InheritedWritableComponent.class);
        rule.check(importClasses(ReadOnlyComponent.class));
    }

    @Test
    void rejectsWritableTransactionOverrides() {
        ArchRule rule = methods().should(ArchitectureTest.useOnlyReadOnlyTransactions());
        assertViolation(rule, WritableOverride.class);
        assertViolation(rule, JakartaOverride.class);
        rule.check(importClasses(ReadOnlyComponent.class));
    }

    @Test
    void rejectsNestedGenericEntityModels() {
        ArchRule rule = classes().that().haveSimpleName("NestedModel")
                .should(ArchitectureTest.containNoPersistenceEntities());
        JavaClasses models = importClasses(NestedModel.class, EntityModel.class, UserProfile.class);
        assertThat(rule.evaluate(models).hasViolation()).isTrue();
    }

    @Test
    void rejectsEntityBearingServiceResults() {
        ArchRule rule = methods().that().areDeclaredIn(ResultService.class)
                .should(ArchitectureTest.returnNoPersistenceEntities());
        JavaClasses models = importClasses(ResultService.class, NestedModel.class, EntityModel.class, UserProfile.class);
        assertThat(rule.evaluate(models).hasViolation()).isTrue();
    }

    @Test
    void rejectsEntityBearingInterfaceProjections() {
        ArchRule rule = classes().that().haveSimpleName("EntityProjection")
                .should(ArchitectureTest.containNoPersistenceEntities());
        assertThat(rule.evaluate(importClasses(EntityProjection.class, UserProfile.class)).hasViolation()).isTrue();
    }

    @Test
    void rejectsEntityArrays() {
        ArchRule rule = classes().that().haveSimpleName("ArrayModel")
                .should(ArchitectureTest.containNoPersistenceEntities());
        assertThat(rule.evaluate(importClasses(ArrayModel.class, UserProfile.class)).hasViolation()).isTrue();
    }

    @Test
    void rejectsInheritedEntityFields() {
        ArchRule rule = classes().that().haveSimpleName("InheritedModel")
                .should(ArchitectureTest.containNoPersistenceEntities());
        assertThat(rule.evaluate(importClasses(InheritedModel.class, BaseModel.class, UserProfile.class))
                .hasViolation()).isTrue();
    }

    @Test
    void safelyTraversesRecursiveEntityFreeModels() {
        classes().that().haveSimpleName("RecursiveModel")
                .should(ArchitectureTest.containNoPersistenceEntities())
                .check(importClasses(RecursiveModel.class));
    }

    private JavaClasses importClasses(Class<?>... types) {
        return new ClassFileImporter().importClasses(types);
    }

    private void assertViolation(ArchRule rule, Class<?> type) {
        assertThat(rule.evaluate(importClasses(type)).hasViolation()).isTrue();
    }

    interface ReadRepository extends Repository<UserProfile, UUID> {
        @Query("select p from UserProfile p")
        Optional<UserProfile> findProfile();
    }

    interface WriteRepository extends Repository<UserProfile, UUID> {
        UserProfile save(UserProfile profile);
    }

    interface InheritedWriteRepository extends WriteRepository {
    }

    interface ModifyingRepository extends Repository<UserProfile, UUID> {
        @Modifying
        @Query("select p from UserProfile p")
        void findAndModify();
    }

    interface MutationQueryRepository extends Repository<UserProfile, UUID> {
        @Query("update UserProfile p set p.activeProfile = false")
        void queryUpdate();
    }

    interface MutationCountRepository extends Repository<UserProfile, UUID> {
        @Query(value = "select p from UserProfile p", countQuery = "delete from UserProfile")
        List<UserProfile> findProfiles();
    }

    interface ProcedureRepository extends Repository<UserProfile, UUID> {
        @Procedure("update_profiles")
        void getProfiles();
    }

    interface NamedQueryRepository extends Repository<UserProfile, UUID> {
        @Query(name = "UserProfile.mutate")
        void findProfiles();
    }

    @Transactional(readOnly = true)
    static class InheritedWritableComponent extends WritableOverride {
    }

    @Transactional
    static class WritableComponent {
    }

    static class MissingTransactionComponent {
    }

    @Transactional(readOnly = true)
    static class ReadOnlyComponent {
        @Transactional(readOnly = true)
        public String read() {
            return "value";
        }
    }

    @Transactional(readOnly = true)
    static class WritableOverride {
        @Transactional
        public void read() {
        }
    }

    @Transactional(readOnly = true)
    static class JakartaOverride {
        @jakarta.transaction.Transactional
        public void read() {
        }
    }

    record NestedModel(Optional<List<EntityModel>> profiles) {
    }

    record EntityModel(UserProfile profile) {
    }

    interface EntityProjection {
        List<UserProfile> getProfiles();
    }

    record ArrayModel(UserProfile[] profiles) {
    }

    static class BaseModel {
        private List<UserProfile> profiles;
    }

    static class InheritedModel extends BaseModel {
    }

    record RecursiveModel(List<RecursiveModel> children) {
    }

    interface ResultService {
        Optional<List<NestedModel>> read();
    }
}
