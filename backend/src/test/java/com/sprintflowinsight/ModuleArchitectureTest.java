package com.sprintflowinsight;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.RestController;

class ModuleArchitectureTest {
  @Test
  void moduleDependenciesAreAcyclicAndOnlyUseDeclaredInterfaces() {
    ApplicationModules.of(SprintFlowApplication.class).verify();
  }

  @Test
  void controllersCannotReachRepositoriesAndDependenciesUseConstructors() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.sprintflowinsight");
    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(Repository.class)
        .check(classes);
    noFields().should().beAnnotatedWith(Autowired.class).check(classes);
    noFields().should().beAnnotatedWith(Value.class).check(classes);
  }

  @Test
  void generateModuleDocumentation() {
    new Documenter(
            ApplicationModules.of(SprintFlowApplication.class),
            Documenter.Options.defaults().withOutputFolder("../docs/architecture"))
        .writeDocumentation();
  }
}
