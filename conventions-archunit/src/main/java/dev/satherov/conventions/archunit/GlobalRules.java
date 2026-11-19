package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

///
/// Every global rule, for a project's architecture test to pull in at once:
///
/// ```java
/// @AnalyzeClasses(packages = "dev.satherov.example")
/// class ArchitectureTest {
///
///     @ArchTest
///     static final ArchTests GLOBAL = ArchTests.in(GlobalRules.class);
/// }
/// ```
///
/// The rules that apply to main code only tell it from test code by the directory it was compiled into, so the test
/// imports both. A rule that takes a project type, such as [ShapeRules#describedThrough(Class)], is added on its own.
///
@UtilityClass
public class GlobalRules {

    ///
    /// The rules about packages and layers.
    ///
    @ArchTest
    public static final ArchTests LAYERS = ArchTests.in(LayerRules.class);

    ///
    /// The rules about type names.
    ///
    @ArchTest
    public static final ArchTests NAMES = ArchTests.in(NamingRules.class);

    ///
    /// The rules about type and member shapes.
    ///
    @ArchTest
    public static final ArchTests SHAPES = ArchTests.in(ShapeRules.class);

    ///
    /// The rules about test code.
    ///
    @ArchTest
    public static final ArchTests TESTS = ArchTests.in(TestRules.class);
}
