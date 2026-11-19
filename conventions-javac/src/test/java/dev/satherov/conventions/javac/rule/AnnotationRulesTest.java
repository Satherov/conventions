package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.annotation.AnnotationLineRule;
import dev.satherov.conventions.javac.rule.annotation.DeprecatedRule;
import dev.satherov.conventions.javac.rule.annotation.GeneratedRule;
import dev.satherov.conventions.javac.rule.annotation.InternalOnceRule;
import dev.satherov.conventions.javac.rule.annotation.OverrideRule;
import dev.satherov.conventions.javac.rule.annotation.PackageInfoRule;
import dev.satherov.conventions.javac.rule.annotation.SuppressWarningsRule;
import dev.satherov.conventions.javac.rule.annotation.UnmodifiableRule;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about annotations.
///
class AnnotationRulesTest {

    @Test
    void overrideFlagsMissingAnnotations() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.function.Supplier;

                class Sample implements Supplier<String>, Comparable<Sample> {

                    public String get() {
                        return "";
                    }

                    @Override
                    public int compareTo(Sample other) {
                        return 0;
                    }

                    public String toString() {
                        return "";
                    }

                    static void helper() { }
                }
                """);

        assertThat(report.getAllLinesOf(OverrideRule.ID)).containsExactly(7L, 16L);
    }

    @Test
    void overrideFlagsRedeclarationsInSubinterfaces() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.function.Supplier;

                interface Named extends Supplier<String> {

                    String get();
                }
                """);

        assertThat(report.getAllLinesOf(OverrideRule.ID)).containsExactly(7L);
    }

    @Test
    void overrideLeavesLombokMethodsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.EqualsAndHashCode;
                import lombok.ToString;

                @ToString
                @EqualsAndHashCode
                class Sample {

                    private int count;
                }
                """);

        assertThat(report.getAllOf(OverrideRule.ID)).isEmpty();
    }

    @Test
    void suppressWarningsTakesLintKeysAndInspectionIds() {
        Javac.Report report = Javac.main("""
                package dev.example;

                @SuppressWarnings({"unchecked", "ParameterNameDiffersFromOverriddenParameter"})
                class Sample {

                    @SuppressWarnings({"rawtypes", "UnusedReturnValue"})
                    Sample() { }

                    @SuppressWarnings(value = "DataFlowIssue")
                    void first() { }

                    @SuppressWarnings("not a key")
                    void second() { }
                }
                """);

        assertThat(report.getAllOf(SuppressWarningsRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("not a key is neither a javac lint key nor an IntelliJ inspection id");
    }

    @Test
    void suppressWarningsStaysOffFieldsParametersAndLocals() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    @SuppressWarnings("rawtypes")
                    private int count;

                    void run(@SuppressWarnings("rawtypes") int value) {
                        @SuppressWarnings("rawtypes")
                        int local = 0;
                    }

                    record Pair(@SuppressWarnings("rawtypes") int left) { }
                }
                """);

        assertThat(report.getAllLinesOf(SuppressWarningsRule.ID)).containsExactly(5L, 8L, 9L, 13L);
    }

    @Test
    void internalOnceFlagsMembersOfInternalTypes() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.ApiStatus;

                @ApiStatus.Internal
                class Sample {

                    @ApiStatus.Internal
                    void run() { }

                    static class Nested {

                        @ApiStatus.Internal
                        int count;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(InternalOnceRule.ID)).containsExactly(9L, 14L);
    }

    @Test
    void annotationLineFlagsAnnotationsSharingALine() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.Unmodifiable;
                import org.jspecify.annotations.Nullable;

                import java.util.List;

                @SuppressWarnings("unused") class Sample {

                    @Deprecated void old() { }

                    private @Nullable String name;

                    @Unmodifiable List<String> names() {
                        return List.of();
                    }

                    void take(@Nullable String text, @Deprecated int count) { }

                    record Pair(@Unmodifiable List<String> left, String right) { }
                }
                """);

        assertThat(report.getAllLinesOf(AnnotationLineRule.ID)).containsExactly(8L, 10L);
    }

    @Test
    void annotationLinePlacesTypeUseAnnotationsBeforeTheType() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.Unmodifiable;
                import org.jspecify.annotations.Nullable;

                import java.util.List;

                class Sample {

                    @Nullable private String name;

                    @Unmodifiable
                    List<String> aliases() {
                        return List.of();
                    }

                    @Unmodifiable public List<String> tags() {
                        return List.of();
                    }

                    public @Unmodifiable List<String> names() {
                        return List.of();
                    }
                }
                """);

        assertThat(report.getAllLinesOf(AnnotationLineRule.ID)).containsExactly(10L, 12L, 17L);
    }

    @Test
    void deprecatedIsFlaggedInMainOnly() {
        String source = """
                package dev.example;

                class Sample {

                    @Deprecated
                    void old() { }
                }
                """;

        assertThat(Javac.main(source).getAllLinesOf(DeprecatedRule.ID)).containsExactly(5L);
        assertThat(Javac.test(source).getAllOf(DeprecatedRule.ID)).isEmpty();
    }

    @Test
    void generatedIsRequiredOnGeneratedTypesOnly() {
        Path root = Javac.temporary();
        try {
            List<Path> files = Javac.write(root, """
                    package dev.example;

                    class Plain { }
                    """, """
                    package dev.example;

                    import javax.annotation.processing.Generated;

                    @Generated("codegen")
                    class Marked { }
                    """);
            Javac.Report report = Javac.compile(root, List.of("set=main", "generated=" + root.resolve("src")), files);

            assertThat(report.getAllOf(GeneratedRule.ID))
                    .extracting(Javac.Finding::file)
                    .containsExactly("dev/example/Plain.java");
        } finally {
            Javac.delete(root);
        }
    }

    @Test
    void generatedLeavesWrittenTypesAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Plain { }
                """);

        assertThat(report.getAllOf(GeneratedRule.ID)).isEmpty();
    }

    @Test
    void packageInfoIsRequired() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample { }
                """);

        assertThat(report.getAllOf(PackageInfoRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("package dev.example has no package-info.java");
    }

    @Test
    void packageInfoOnDiskCoversAPartialCompilation() {
        Path root = Javac.temporary();
        try {
            List<Path> files = Javac.write(root, """
                    package dev.example;

                    class Sample { }
                    """);
            Files.writeString(files.getFirst().resolveSibling("package-info.java"), "package dev.example;\n");
            Javac.Report report = Javac.compile(root, List.of("set=main"), files);

            assertThat(report.getAllOf(PackageInfoRule.ID)).isEmpty();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            Javac.delete(root);
        }
    }

    @Test
    void packageInfoHoldsItsDocAndTheTwoAnnotations() {
        Javac.Report report = Javac.main("""
                @NullMarked
                @Deprecated
                package dev.example;

                import org.jspecify.annotations.NullMarked;
                """);

        assertThat(report.getAllOf(PackageInfoRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "@Deprecated on a package; a package carries only @NullMarked and @CheckReturnValue",
                        "package has no @CheckReturnValue",
                        "package has no doc comment"
                );
    }

    @Test
    void packageInfoAcceptsTheFullForm() {
        Javac.Report report = Javac.main("""
                ///
                /// The example package.
                ///
                @NullMarked
                @CheckReturnValue
                package dev.example;

                import com.google.errorprone.annotations.CheckReturnValue;

                import org.jspecify.annotations.NullMarked;
                """);

        assertThat(report.getAllOf(PackageInfoRule.ID)).isEmpty();
    }

    @Test
    void unmodifiableMarksReturnedCollections() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.Unmodifiable;
                import org.jetbrains.annotations.UnmodifiableView;

                import java.util.AbstractList;
                import java.util.List;
                import java.util.Map;
                import java.util.Set;

                public class Sample extends AbstractList<String> {

                    public List<String> names() {
                        return List.of();
                    }

                    public @Unmodifiable Map<String, String> byId() {
                        return Map.of();
                    }

                    @UnmodifiableView
                    public Set<String> view() {
                        return Set.of();
                    }

                    private List<String> hidden() {
                        return List.of();
                    }

                    @Override
                    public List<String> subList(int from, int to) {
                        return List.of();
                    }

                    @Override
                    public String get(int index) {
                        return "";
                    }

                    @Override
                    public int size() {
                        return 0;
                    }
                }
                """);

        assertThat(report.getAllOf(UnmodifiableRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactlyInAnyOrder(
                        "method names returns a collection without @Unmodifiable or @UnmodifiableView",
                        "method hidden returns a collection without @Unmodifiable or @UnmodifiableView"
                );
    }

    @Test
    void unmodifiableMarksRecordComponents() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.Unmodifiable;

                import java.util.List;

                public record Group(@Unmodifiable List<String> members, List<String> guests, String name) { }
                """, """
                package dev.example;

                import java.util.List;

                record Team(List<String> players) { }
                """);

        assertThat(report.getAllOf(UnmodifiableRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactlyInAnyOrder(
                        "component guests is a collection without @Unmodifiable or @UnmodifiableView",
                        "component players is a collection without @Unmodifiable or @UnmodifiableView"
                );
    }

    @Test
    void unmodifiableHoldsForOverridesOfProjectMethods() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.Unmodifiable;

                import java.util.List;

                public interface Named {

                    @Unmodifiable
                    List<String> names();
                }
                """, """
                package dev.example;

                import java.util.List;

                public class Person implements Named {

                    @Override
                    public List<String> names() {
                        return List.of();
                    }
                }
                """);

        assertThat(report.getAllOf(UnmodifiableRule.ID))
                .extracting(Javac.Finding::file)
                .containsExactly("dev/example/Person.java");
    }
}
