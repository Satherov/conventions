package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

///
/// The rules about the names of types.
///
@UtilityClass
public class NamingRules {

    ///
    /// No type is a `Manager`, `Service`, or `Helper`, and no utility class is a bare `Utils`, `Util`, `Helpers`,
    /// or a `Constants` holder: a type is named for the whole of what it is.
    ///
    @ArchTest
    public static final ArchRule NO_KIND_NAMES = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .should()
            .haveSimpleNameEndingWith("Manager")
            .orShould()
            .haveSimpleNameEndingWith("Service")
            .orShould()
            .haveSimpleNameEndingWith("Helper")
            .orShould()
            .haveSimpleNameEndingWith("Constants")
            .orShould()
            .haveSimpleName("Utils")
            .orShould()
            .haveSimpleName("Util")
            .orShould()
            .haveSimpleName("Helpers")
            .allowEmptyShould(true);

    ///
    /// No interface takes an `I` prefix.
    ///
    @ArchTest
    public static final ArchRule NO_INTERFACE_PREFIX = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .and()
            .areInterfaces()
            .should()
            .haveNameMatching(".*[.$]I[A-Z][a-z][^.$]*")
            .allowEmptyShould(true);

    ///
    /// No type takes an `Abstract` or `Base` prefix: the abstract type takes the plain noun and the concrete ones
    /// qualify it.
    ///
    @ArchTest
    public static final ArchRule NO_ABSTRACT_PREFIX = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .should()
            .haveNameMatching(".*[.$](Abstract|Base)[A-Z][^.$]*")
            .allowEmptyShould(true);

    ///
    /// No name marks a version or a quality of the type: no `New`, `Legacy`, `Enhanced`, `Improved`, `Advanced`, or
    /// `Smart`, and no `V2`, `Old`, `Fixed`, `Final`, `Temp`, `Backup`, or `Copy` at its end.
    ///
    @ArchTest
    public static final ArchRule NO_VERSION_NAMES = ArchRuleDefinition.noClasses()
            .that(Code.MAIN)
            .should()
            .haveNameMatching(".*[.$][^.$]*(New|Legacy|Enhanced|Improved|Advanced|Smart)([A-Z][^.$]*)?")
            .orShould()
            .haveNameMatching(".*(V\\d+|Old|Fixed|Final|Temp|Backup|Copy)")
            .allowEmptyShould(true);

    ///
    /// Every throwable ends in `Exception`.
    ///
    @ArchTest
    public static final ArchRule EXCEPTIONS_END_IN_EXCEPTION = ArchRuleDefinition.classes()
            .that(Code.MAIN)
            .and()
            .areAssignableTo(Throwable.class)
            .should()
            .haveSimpleNameEndingWith("Exception")
            .allowEmptyShould(true);

    ///
    /// A test double is named for what it does, never `Mock<Type>`.
    ///
    @ArchTest
    public static final ArchRule NO_MOCK_NAMES = ArchRuleDefinition.noClasses()
            .should()
            .haveSimpleNameStartingWith("Mock")
            .allowEmptyShould(true);
}
