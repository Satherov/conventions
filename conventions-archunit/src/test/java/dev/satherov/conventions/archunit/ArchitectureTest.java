package dev.satherov.conventions.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

///
/// Holds the conventions repository itself to the global rules.
///
@AnalyzeClasses(packages = "dev.satherov.conventions")
class ArchitectureTest {

    @ArchTest
    static final ArchTests GLOBAL = ArchTests.in(GlobalRules.class);
}
