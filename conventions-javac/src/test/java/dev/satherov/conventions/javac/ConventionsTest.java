package dev.satherov.conventions.javac;

import dev.satherov.conventions.javac.rule.annotation.OverrideRule;
import dev.satherov.conventions.javac.rule.layout.EmptyBodyRule;
import dev.satherov.conventions.javac.source.Options;
import dev.satherov.conventions.javac.source.SourceSet;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

///
/// Tests the plugin as a whole: its options, and how its findings reach javac.
///
class ConventionsTest {

    ///
    /// A file with one breach found while parsing and one found once types are known.
    ///
    private static final String BREACHES = """
            package dev.example;

            import java.util.function.Supplier;

            class Sample implements Supplier<String> {

                public String get() {
                    return "";
                }

                void run() {}
            }
            """;

    @Test
    void findingsFailTheCompilation() {
        Javac.Report report = Javac.main(ConventionsTest.BREACHES);

        assertThat(report.success()).isFalse();
    }

    @Test
    void parsedFindingsDoNotHideAnalyzedOnes() {
        Javac.Report report = Javac.main(ConventionsTest.BREACHES);

        assertThat(report.getAllLinesOf(EmptyBodyRule.ID)).containsExactly(11L);
        assertThat(report.getAllLinesOf(OverrideRule.ID)).containsExactly(7L);
    }

    @Test
    void disabledRulesDoNotRun() {
        Javac.Report report = Javac.compile(List.of("set=main", "disable=" + OverrideRule.ID + "," + EmptyBodyRule.ID), ConventionsTest.BREACHES);

        assertThat(report.getAllOf(OverrideRule.ID)).isEmpty();
        assertThat(report.getAllOf(EmptyBodyRule.ID)).isEmpty();
    }

    @Test
    void skippedDirectoriesAreNotChecked() {
        Path root = Javac.temporary();
        try {
            List<Path> files = Javac.write(root, ConventionsTest.BREACHES);
            Javac.Report report = Javac.compile(root, List.of("set=main", "skip=" + root.resolve("src")), files);

            assertThat(report.findings()).isEmpty();
            assertThat(report.success()).isTrue();
        } finally {
            Javac.delete(root);
        }
    }

    @Test
    void optionsReadEveryKey() {
        Options options = Options.from("root=dev.example", "set=test", "disable=a, b", "generated=gen" + File.pathSeparator + "more", "skip=out");

        assertThat(options.root()).isEqualTo("dev.example");
        assertThat(options.set()).isEqualTo(SourceSet.TEST);
        assertThat(options.disabled()).containsExactlyInAnyOrder("a", "b");
        assertThat(options.generated()).containsExactly(Path.of("gen").toAbsolutePath(), Path.of("more").toAbsolutePath());
        assertThat(options.skipped()).containsExactly(Path.of("out").toAbsolutePath());
    }

    @Test
    void optionsRejectUnknownInput() {
        assertThatThrownBy(() -> Options.from("verbose")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Options.from("color=red")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Options.from("set=benchmark")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownDisabledRulesFailTheCompilation() {
        assertThatThrownBy(() -> Javac.compile(List.of("disable=no-such-rule"), ConventionsTest.BREACHES))
                .hasStackTraceContaining("Unknown conventions rules to disable: no-such-rule");
    }
}
