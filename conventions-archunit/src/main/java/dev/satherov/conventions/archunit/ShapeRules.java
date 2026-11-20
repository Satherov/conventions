package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.base.ArchUnitException;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.lang.annotation.Repeatable;
import java.lang.reflect.AccessibleObject;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

///
/// The rules about the shape of types and members: which form a type takes and what its members may return.
///
@UtilityClass
public class ShapeRules {

    ///
    /// The logger types a hand-made logger field would hold.
    ///
    public static final Set<String> LOGGERS = Set.of(
            "org.slf4j.Logger",
            "java.lang.System$Logger",
            "java.util.logging.Logger",
            "org.apache.logging.log4j.Logger"
    );

    ///
    /// The name Lombok's `@Slf4j` gives its logger field.
    ///
    public static final String LOGGER_FIELD = "log";

    ///
    /// The names of members that hold a bitfield.
    ///
    private static final Pattern BITFIELD = Pattern.compile("(.*[a-z])?(Flags|Bits|Mask)|flags|bits|mask");

    ///
    /// The types whose methods open up reflection past the language's access rules.
    ///
    private static final Set<String> UNSAFE = Set.of(
            "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe"
    );

    ///
    /// A type with exactly one instance is an enum with the constant `INSTANCE`, not a class with a static
    /// `INSTANCE` field. A class that extends another class or has type parameters cannot be an enum and is left out.
    ///
    @ArchTest
    public static final ArchRule SINGLE_INSTANCE_IS_ENUM = ArchRuleDefinition.classes()
            .that(Code.MAIN)
            .and()
            .areNotEnums()
            .and(ShapeRules.canBeEnums())
            .should(ShapeRules.holdNoInstanceField())
            .allowEmptyShould(true);

    ///
    /// A type with no components is a class, never an empty record. A record that implements a sealed interface is a
    /// bodiless case of a closed hierarchy and is left out.
    ///
    @ArchTest
    public static final ArchRule NO_EMPTY_RECORDS = ArchRuleDefinition.classes()
            .that(Code.MAIN)
            .and()
            .areRecords()
            .and(DescribedPredicate.not(ShapeRules.areCasesOfSealedInterface()))
            .should(ShapeRules.haveComponents())
            .allowEmptyShould(true);

    ///
    /// The container of a `@Repeatable` annotation is nested in it as `Container`.
    ///
    @ArchTest
    public static final ArchRule REPEATABLE_CONTAINER = ArchRuleDefinition.classes()
            .that(Code.MAIN)
            .and()
            .areAnnotatedWith(Repeatable.class)
            .should(ShapeRules.nestTheirContainer())
            .allowEmptyShould(true);

    ///
    /// A logger is Lombok's `@Slf4j`, never a hand-made field.
    ///
    @ArchTest
    public static final ArchRule LOMBOK_LOGGERS = ArchRuleDefinition.fields()
            .that()
            .areDeclaredInClassesThat(Code.MAIN)
            .and(ShapeRules.holdLoggers())
            .should()
            .haveName(ShapeRules.LOGGER_FIELD)
            .andShould()
            .bePrivate()
            .andShould()
            .beStatic()
            .andShould()
            .beFinal()
            .allowEmptyShould(true);

    ///
    /// Main code loads no class by name and opens no member past its access: no `Class.forName`, no `setAccessible`,
    /// no `Unsafe`.
    ///
    @ArchTest
    public static final ArchRule NO_REFLECTION = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .should()
            .callMethodWhere(ShapeRules.openReflection())
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("sun.misc.Unsafe")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("jdk.internal.misc.Unsafe")
            .allowEmptyShould(true);

    ///
    /// A bitfield in the public surface is a typed set of flags, never a bare `int` or `long`. Members are found by
    /// name: `flags`, `bits`, `mask`, and names ending in them.
    ///
    @ArchTest
    public static final ArchRule NO_BITFIELDS_IN_API = ArchRuleDefinition.members()
            .that()
            .areDeclaredInClassesThat(Code.MAIN)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("..api..")
            .and(ShapeRules.areNamedLikeBitfields())
            .should(ShapeRules.notHoldBareNumbers())
            .allowEmptyShould(true);

    ///
    /// Creates the rule that every hand-written `toString` of a class implementing an `api` interface goes through the
    /// project's describer, so that no class prints its own format and no Lombok `@ToString` slips in.
    ///
    /// @param describer The type whose methods build the text, such as a `ToStringBuilder`.
    ///
    /// @return The rule.
    ///
    public static ArchRule describedThrough(Class<?> describer) {
        return ShapeRules.describedThrough(describer.getName());
    }

    ///
    /// Creates the rule of [#describedThrough(Class)] for a describer named by its binary name.
    ///
    /// @param describer The binary name of the type whose methods build the text.
    ///
    /// @return The rule.
    ///
    public static ArchRule describedThrough(String describer) {
        return ArchRuleDefinition.methods()
                .that()
                .haveName("toString")
                .and()
                .haveRawParameterTypes(new Class<?>[0])
                .and()
                .areDeclaredInClassesThat(Code.MAIN)
                .and()
                .areDeclaredInClassesThat(ShapeRules.implementApiInterfaces())
                .and()
                .doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should(ShapeRules.call(describer))
                .allowEmptyShould(true);
    }

    ///
    /// Matches the classes an enum could replace: no superclass but `Object` and no type parameters.
    ///
    private static DescribedPredicate<JavaClass> canBeEnums() {
        return DescribedPredicate.describe(
                "can be enums",
                type -> type.getTypeParameters().isEmpty() && type.getRawSuperclass().map(superclass -> superclass.isEquivalentTo(Object.class)).orElse(true)
        );
    }

    ///
    /// Matches the types that implement a sealed interface directly.
    ///
    private static DescribedPredicate<JavaClass> areCasesOfSealedInterface() {
        return DescribedPredicate.describe(
                "are cases of a sealed interface",
                type -> type.getRawInterfaces().stream().anyMatch(ShapeRules::isSealed)
        );
    }

    ///
    /// Checks if the given type is sealed, or `false` if it cannot be loaded to tell.
    ///
    private static boolean isSealed(JavaClass type) {
        try {
            return type.reflect().isSealed();
        } catch (ArchUnitException.ReflectionException exception) {
            return false;
        }
    }

    ///
    /// Records a violation for a public static final field named `INSTANCE` of the class's own type.
    ///
    private static ArchCondition<JavaClass> holdNoInstanceField() {
        return new ArchCondition<>("hold no INSTANCE field of their own type") {

            ///
            /// Checks the static fields of the class.
            ///
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                type.tryGetField("INSTANCE")
                        .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                        .filter(field -> field.getRawType().equals(type))
                        .ifPresent(field -> events.add(SimpleConditionEvent.violated(type, type.getName() + " has a single INSTANCE; make it an enum with the constant INSTANCE")));
            }
        };
    }

    ///
    /// Records a violation for a record with no components, which shows as a record with no instance fields.
    ///
    private static ArchCondition<JavaClass> haveComponents() {
        return new ArchCondition<>("have components") {

            ///
            /// Checks the instance fields of the record.
            ///
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                boolean empty = type.getFields()
                        .stream()
                        .allMatch(field -> field.getModifiers().contains(JavaModifier.STATIC));
                if (empty) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " is an empty record; make it a class"));
                }
            }
        };
    }

    ///
    /// Records a violation for a repeatable annotation whose container is not nested in it as `Container`.
    ///
    private static ArchCondition<JavaClass> nestTheirContainer() {
        return new ArchCondition<>("nest their container as Container") {

            ///
            /// Checks the container the `@Repeatable` names.
            ///
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                Optional<JavaClass> container = type.tryGetAnnotationOfType(Repeatable.class.getName())
                        .flatMap(annotation -> annotation.get("value"))
                        .filter(JavaClass.class::isInstance)
                        .map(JavaClass.class::cast);
                boolean nested = container.map(found -> found.getSimpleName().equals("Container") && found.getEnclosingClass().map(type::equals).orElse(false))
                        .orElse(false);
                if (!nested) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " has its repeatable container outside " + type.getSimpleName() + ".Container"));
                }
            }
        };
    }

    ///
    /// Selects the fields that hold a logger.
    ///
    private static DescribedPredicate<JavaField> holdLoggers() {
        return DescribedPredicate.describe("hold a logger", field -> ShapeRules.LOGGERS.contains(field.getRawType().getName()));
    }

    ///
    /// Selects the calls that load a class by name or open a member past its access.
    ///
    private static DescribedPredicate<JavaMethodCall> openReflection() {
        return DescribedPredicate.describe("load a class by name or open a member past its access", call -> {
            JavaClass owner = call.getTargetOwner();
            String name = call.getName();
            boolean forName = owner.isEquivalentTo(Class.class) && name.equals("forName");
            boolean opening = owner.isAssignableTo(AccessibleObject.class) && (name.equals("setAccessible") || name.equals("trySetAccessible"));
            return forName || opening || ShapeRules.UNSAFE.contains(owner.getName());
        });
    }

    ///
    /// Selects the members named like a bitfield.
    ///
    private static DescribedPredicate<JavaMember> areNamedLikeBitfields() {
        return DescribedPredicate.describe("are named like a bitfield", member -> ShapeRules.BITFIELD.matcher(member.getName()).matches());
    }

    ///
    /// Records a violation for a field or method of the given member that holds a bare `int` or `long`.
    ///
    private static ArchCondition<JavaMember> notHoldBareNumbers() {
        return new ArchCondition<>("not hold a bare int or long") {

            ///
            /// Checks the type of the field or the return type of the method.
            ///
            @Override
            public void check(JavaMember member, ConditionEvents events) {
                JavaClass held = switch (member) {
                    case JavaField field -> field.getRawType();
                    case JavaMethod method -> method.getRawReturnType();
                    default -> null;
                };
                if (held != null && (held.isEquivalentTo(int.class) || held.isEquivalentTo(long.class))) {
                    events.add(SimpleConditionEvent.violated(member, member.getFullName() + " is a bare " + held.getName() + " bitfield; use a Set of a flag enum"));
                }
            }
        };
    }

    ///
    /// Selects the classes that implement an interface of the public surface.
    ///
    private static DescribedPredicate<JavaClass> implementApiInterfaces() {
        return DescribedPredicate.describe("implement an api interface", type -> !type.isInterface() && !type.isRecord() && !type.isEnum() && type.getAllRawInterfaces()
                .stream()
                .anyMatch(implemented -> implemented.getPackageName().contains(".api.") || implemented.getPackageName().endsWith(".api")));
    }

    ///
    /// Records a violation for a method that calls no method of the given type.
    ///
    private static ArchCondition<JavaMethod> call(String describer) {
        return new ArchCondition<>("call " + describer) {

            ///
            /// Checks the calls the method makes.
            ///
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean described = method.getMethodCallsFromSelf()
                        .stream()
                        .map(JavaAccess::getTargetOwner)
                        .map(HasName::getName)
                        .anyMatch(describer::equals);
                if (!described) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " does not build its text through " + describer));
                }
            }
        };
    }
}
