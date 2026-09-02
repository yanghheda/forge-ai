package ai.forge.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

    private static final String BASE_PACKAGE = "ai.forge.server";

    private static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..infrastructure.persistence..")
            .because("Controller 必须通过应用服务访问持久化能力");

    private static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..infrastructure..")
            .because("应用层不能反向依赖 Controller 或基础设施实现");

    private static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "ai.forge.server..domain..")
            .because("领域层必须保持框架无关");

    private static final ArchRule SWAGGER_ANNOTATIONS_STAY_IN_CONTROLLERS = noClasses()
            .that()
            .resideOutsideOfPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.swagger.v3.oas.annotations..")
            .because("Swagger 注解只允许出现在 Controller");

    @Test
    void productionCodeRespectsLayerAndSwaggerBoundaries() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE_PACKAGE);

        CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE.check(productionClasses);
        APPLICATION_DOES_NOT_DEPEND_ON_OUTER_LAYERS.check(productionClasses);
        DOMAIN_IS_FRAMEWORK_INDEPENDENT.check(productionClasses);
        SWAGGER_ANNOTATIONS_STAY_IN_CONTROLLERS.check(productionClasses);
        assertNoCrossModuleRepositoryAccess(productionClasses, BASE_PACKAGE);
    }

    @Test
    void controllerRepositoryViolationIsActuallyRejected() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages("ai.forge.server.architecture.fixture.persistence");

        assertThatThrownBy(() -> CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE.check(fixtureClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadController")
                .hasMessageContaining("BadRepository");
    }

    @Test
    void swaggerAnnotationOutsideControllerIsActuallyRejected() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages("ai.forge.server.architecture.fixture.swagger");

        assertThatThrownBy(() -> SWAGGER_ANNOTATIONS_STAY_IN_CONTROLLERS.check(fixtureClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadAnnotatedService")
                .hasMessageContaining("Operation");
    }

    @Test
    void crossModuleRepositoryAccessIsActuallyRejected() {
        String fixturePackage = "ai.forge.server.architecture.fixture.cross";
        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(fixturePackage);

        assertThatThrownBy(() -> assertNoCrossModuleRepositoryAccess(fixtureClasses, fixturePackage))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadCrossModuleService")
                .hasMessageContaining("TargetRepository");
    }

    private void assertNoCrossModuleRepositoryAccess(JavaClasses classes, String basePackage) {
        List<String> violations = new ArrayList<>();
        classes.forEach(source -> source.getDirectDependenciesFromSelf().forEach(dependency -> {
            String sourcePackage = source.getPackageName();
            String targetPackage = dependency.getTargetClass().getPackageName();
            if (isPersistencePackage(targetPackage)
                    && targetPackage.startsWith(basePackage + ".")
                    && !moduleOf(sourcePackage, basePackage).equals(moduleOf(targetPackage, basePackage))) {
                violations.add(source.getName() + " depends on " + dependency.getTargetClass().getName());
            }
        }));
        if (!violations.isEmpty()) {
            throw new AssertionError("禁止跨模块访问 Repository：" + String.join(", ", violations));
        }
    }

    private boolean isPersistencePackage(String packageName) {
        return packageName.contains(".repository") || packageName.contains(".infrastructure.persistence");
    }

    private String moduleOf(String packageName, String basePackage) {
        if (!packageName.startsWith(basePackage + ".")) {
            return packageName;
        }
        String relativePackage = packageName.substring(basePackage.length() + 1);
        int separator = relativePackage.indexOf('.');
        return separator < 0 ? relativePackage : relativePackage.substring(0, separator);
    }
}
