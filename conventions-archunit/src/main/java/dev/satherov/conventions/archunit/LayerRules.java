package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

///
/// The rules about packages: the `api`, `internal`, and `impl` layers and the names of packages.
///
@UtilityClass
public class LayerRules {

    ///
    /// The packages of the public surface.
    ///
    private static final String API = "..api..";

    ///
    /// The packages only the project itself uses.
    ///
    private static final String INTERNAL = "..internal..";

    ///
    /// The annotation that takes a type out of the public surface although it sits in `api`.
    ///
    private static final String INTERNAL_MARKER = "org.jetbrains.annotations.ApiStatus$Internal";

    ///
    /// The names of the layers.
    ///
    public static final Set<String> LAYERS = Set.of(
            "api",
            "internal",
            "impl"
    );

    ///
    /// The package names that sort types by their kind (records, exceptions, helpers) instead of by the feature they
    /// serve.
    ///
    public static final Set<String> KIND_PACKAGES = Set.of(
            "util",
            "utils",
            "helper",
            "helpers",
            "common",
            "misc",
            "base",
            "interfaces",
            "records",
            "exceptions"
    );

    ///
    /// No `api` type exposes an `internal` type: not as a supertype, a type parameter bound, or in the signature of a
    /// public or protected member, type arguments included.
    ///
    @ArchTest
    public static final ArchRule API_EXPOSES_NO_INTERNAL_TYPES = ArchRuleDefinition.classes()
            .that(Code.MAIN)
            .and()
            .arePublic()
            .and()
            .resideInAPackage(LayerRules.API)
            .and()
            .areNotAnnotatedWith(LayerRules.INTERNAL_MARKER)
            .should(LayerRules.exposeNoInternalTypes())
            .allowEmptyShould(true);

    ///
    /// The root of a layer holds no types: every type sits in the package of its feature.
    ///
    @ArchTest
    public static final ArchRule LAYER_ROOTS_HOLD_NO_TYPES = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .and(LayerRules.areNotPackageInfo())
            .should()
            .resideInAnyPackage("..api", "..internal", "..impl")
            .allowEmptyShould(true);

    ///
    /// No package sorts its types by kind, and no `impl` package sits inside a layer.
    ///
    @ArchTest
    public static final ArchRule NO_KIND_PACKAGES = ArchRuleDefinition.classes()
            .that(LayerRules.areNotPackageInfo())
            .should(LayerRules.resideInAFeaturePackage())
            .allowEmptyShould(true);

    ///
    /// Leaves out the class javac makes of a `package-info.java`.
    ///
    private static DescribedPredicate<JavaClass> areNotPackageInfo() {
        return DescribedPredicate.describe("are not package-info", type -> !type.getSimpleName().equals("package-info"));
    }

    ///
    /// Records every internal type the class exposes.
    ///
    private static ArchCondition<JavaClass> exposeNoInternalTypes() {
        return new ArchCondition<>("expose no internal types") {

            ///
            /// Checks the supertypes, the type parameters, and every visible member of the class.
            ///
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                type.getSuperclass().ifPresent(superclass -> LayerRules.checkExposed(type, "extends", superclass, events));
                for (JavaType implemented : type.getInterfaces()) {
                    LayerRules.checkExposed(type, "implements", implemented, events);
                }

                LayerRules.checkBounds(type, type.getTypeParameters(), events);
                for (JavaMember member : type.getMembers()) {
                    if (LayerRules.isVisible(member)) {
                        LayerRules.checkMember(type, member, events);
                    }
                }
            }
        };
    }

    ///
    /// Checks if the given member is public or protected.
    ///
    private static boolean isVisible(JavaMember member) {
        Set<JavaModifier> modifiers = member.getModifiers();
        return !modifiers.contains(JavaModifier.SYNTHETIC) && (modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED));
    }

    ///
    /// Checks the types in the signature of the given member.
    ///
    private static void checkMember(JavaClass type, JavaMember member, ConditionEvents events) {
        switch (member) {
            case JavaField field -> LayerRules.checkExposed(type, field.getFullName(), field.getType(), events);
            case JavaCodeUnit unit -> {
                LayerRules.checkExposed(type, unit.getFullName(), unit.getReturnType(), events);
                for (JavaType parameter : unit.getParameterTypes()) {
                    LayerRules.checkExposed(type, unit.getFullName(), parameter, events);
                }

                for (JavaClass thrown : unit.getThrowsClause().getTypes()) {
                    LayerRules.checkExposed(type, unit.getFullName(), thrown, events);
                }

                LayerRules.checkBounds(type, unit.getTypeParameters(), events);
            }
            default -> { }
        }
    }

    ///
    /// Checks the bounds of the given type parameters.
    ///
    private static void checkBounds(JavaClass type, List<? extends JavaTypeVariable<?>> parameters, ConditionEvents events) {
        for (JavaTypeVariable<?> parameter : parameters) {
            for (JavaType bound : parameter.getUpperBounds()) {
                LayerRules.checkExposed(type, "the bound of " + parameter.getName(), bound, events);
            }
        }
    }

    ///
    /// Records a violation for every internal type the given type involves.
    ///
    private static void checkExposed(JavaClass type, String where, JavaType exposed, ConditionEvents events) {
        PackageMatcher internal = PackageMatcher.of(LayerRules.INTERNAL);
        exposed.getAllInvolvedRawTypes()
                .stream()
                .filter(involved -> internal.matches(involved.getPackageName()))
                .forEach(involved -> events.add(SimpleConditionEvent.violated(type, type.getName() + " exposes the internal type " + involved.getName() + " in " + where)));
    }

    ///
    /// Records a violation for a class in a package named for a kind, or in an `impl` package inside a layer.
    ///
    private static ArchCondition<JavaClass> resideInAFeaturePackage() {
        return new ArchCondition<>("reside in a package named for its feature") {

            ///
            /// Checks every part of the package of the class.
            ///
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                List<String> parts = Arrays.asList(type.getPackageName().split("\\."));
                for (String part : parts) {
                    if (LayerRules.KIND_PACKAGES.contains(part)) {
                        events.add(SimpleConditionEvent.violated(type, type.getName() + " sits in the kind package " + part + "; name the package for its feature"));
                    }
                }

                int firstLayer = LayerRules.indexOfLayer(parts);
                if (firstLayer >= 0 && parts.subList(firstLayer + 1, parts.size()).contains("impl")) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " sits in an impl package inside the " + parts.get(firstLayer) + " layer"));
                }
            }
        };
    }

    ///
    /// Gets the index of the first layer name among the given package parts, or `-1` if there is none.
    ///
    private static int indexOfLayer(List<String> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (LayerRules.LAYERS.contains(parts.get(i))) {
                return i;
            }
        }

        return -1;
    }
}
