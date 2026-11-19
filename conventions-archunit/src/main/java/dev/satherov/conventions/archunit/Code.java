package dev.satherov.conventions.archunit;

import lombok.experimental.UtilityClass;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

import java.util.Optional;

///
/// The predicates that tell main code from test code, by the directory a class was compiled into.
///
/// A class counts as test code where ArchUnit's own [ImportOption.DoNotIncludeTests] would leave it out: the test
/// output of Gradle, Maven, or IntelliJ.
///
@UtilityClass
public class Code {

    ///
    /// The import option that recognises test output.
    ///
    private static final ImportOption NOT_TESTS = new ImportOption.DoNotIncludeTests();

    ///
    /// The classes of main code.
    ///
    public static final DescribedPredicate<JavaClass> MAIN = DescribedPredicate.describe("are main code", Code::isMain);

    ///
    /// The classes of test code.
    ///
    public static final DescribedPredicate<JavaClass> TEST = DescribedPredicate.describe("are test code", Code::isTest);

    ///
    /// Checks if the given class was compiled from main sources.
    ///
    private static boolean isMain(JavaClass type) {
        return Code.locationOf(type)
                .map(Code.NOT_TESTS::includes)
                .orElse(false);
    }

    ///
    /// Checks if the given class was compiled from test sources.
    ///
    private static boolean isTest(JavaClass type) {
        return Code.locationOf(type)
                .map(location -> !Code.NOT_TESTS.includes(location))
                .orElse(false);
    }

    ///
    /// Gets where the given class was compiled to, or an empty optional for a type ArchUnit only knows by name, such as
    /// a JDK type, which is neither main nor test code.
    ///
    private static Optional<Location> locationOf(JavaClass type) {
        return type.getSource()
                .map(source -> Location.of(source.getUri()));
    }
}
