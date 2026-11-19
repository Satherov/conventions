package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

///
/// The rules about test code. They need main and test classes imported together.
///
@UtilityClass
public class TestRules {

    ///
    /// The suffix of a test class.
    ///
    public static final String SUFFIX = "Test";

    ///
    /// A test class `<Type>Test` sits in the package of the main type `<Type>` it tests.
    ///
    @ArchTest
    public static final ArchRule TESTS_MIRROR_MAIN = ArchRuleDefinition.classes()
            .that(Code.TEST)
            .and()
            .areTopLevelClasses()
            .and()
            .haveSimpleNameEndingWith(TestRules.SUFFIX)
            .should(TestRules.sitInThePackageOfTheirType())
            .allowEmptyShould(true);

    ///
    /// Gets the root package of the import the given class belongs to.
    ///
    private static JavaPackage rootOf(JavaClass type) {
        JavaPackage root = type.getPackage();
        while (root.getParent().isPresent()) {
            root = root.getParent().get();
        }

        return root;
    }

    ///
    /// Records a violation for a test class whose type exists in main code, but only in other packages.
    ///
    private static ArchCondition<JavaClass> sitInThePackageOfTheirType() {
        return new ArchCondition<>("sit in the package of the type they test") {

            ///
            /// The packages of every top-level main type, by its simple name.
            ///
            private final Map<String, Set<String>> packagesByName = new HashMap<>();

            ///
            /// Notes where every main type lives, reaching every imported class through the root package of the tests.
            ///
            @Override
            public void init(Collection<JavaClass> tests) {
                this.packagesByName.clear();
                tests.stream()
                        .findFirst()
                        .map(TestRules::rootOf)
                        .map(JavaPackage::getClassesInPackageTree)
                        .orElse(Set.of())
                        .stream()
                        .filter(JavaClass::isTopLevelClass)
                        .filter(Code.MAIN)
                        .forEach(type -> this.packagesByName.computeIfAbsent(type.getSimpleName(), name -> new HashSet<>()).add(type.getPackageName()));
            }

            ///
            /// Checks the package of the test against those of the type it is named for.
            ///
            @Override
            public void check(JavaClass test, ConditionEvents events) {
                String name = test.getSimpleName();
                Set<String> packages = this.packagesByName.getOrDefault(name.substring(0, name.length() - TestRules.SUFFIX.length()), Set.of());
                if (!packages.isEmpty() && !packages.contains(test.getPackageName())) {
                    events.add(SimpleConditionEvent.violated(test, test.getName() + " tests a type in " + String.join(" or ", packages) + "; move it there"));
                }
            }
        };
    }
}
