package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.declaration.TypeParameterRule;
import dev.satherov.conventions.javac.rule.declaration.VisibilityRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about declarations.
///
class DeclarationRulesTest {

    @Test
    void visibilityIsStatedOnEveryMemberOfMain() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.VisibleForTesting;

                class Sample {

                    int count;

                    private int total;

                    Sample() { }

                    void run() { }

                    @VisibleForTesting
                    void probe() { }

                    static class Nested { }
                }
                """);

        assertThat(report.getAllLinesOf(VisibilityRule.ID)).containsExactly(7L, 11L, 13L, 18L);
    }

    @Test
    void visibilityLeavesImplicitMembersAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                interface Shape {

                    int SIDES = 0;

                    int area();

                    class Default { }
                }
                """, """
                package dev.example;

                enum Color {
                    @Deprecated
                    RED,
                    BLUE;

                    Color() { }
                }
                """, """
                package dev.example;

                record Point(int x, int y) {

                    Point {
                        if (x < 0) throw new IllegalArgumentException("negative");
                    }
                }
                """);

        assertThat(report.getAllOf(VisibilityRule.ID)).isEmpty();
    }

    @Test
    void visibilityIsLeftToTestCode() {
        Javac.Report report = Javac.test("""
                package dev.example;

                class SampleTest {

                    void works() { }
                }
                """);

        assertThat(report.getAllOf(VisibilityRule.ID)).isEmpty();
    }

    @Test
    void typeParametersAreCapitalizedWords() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Box<T, VALUE> {

                    <Result> Result map(Result value) {
                        return value;
                    }

                    <KEY_2> void take(KEY_2 key) { }
                }
                """);

        assertThat(report.getAllOf(TypeParameterRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "type parameter T; name it with a capitalized word such as VALUE",
                        "type parameter Result; name it with a capitalized word such as VALUE"
                );
    }
}
