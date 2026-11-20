package dev.satherov.conventions.javac.source;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.annotation.SuppressWarningsRule;
import dev.satherov.conventions.javac.rule.declaration.VisibilityRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests that a finding is dropped where a method or type around it suppresses its rule.
///
class FindingsTest {

    @Test
    void suppressionOnTheMethodDropsItsFinding() {
        Javac.Report report = Javac.main("""
                package dev.example;

                public class Sample {

                    @SuppressWarnings("conventions:visibility")
                    void run() { }

                    void walk() { }
                }
                """);

        assertThat(report.getAllLinesOf(VisibilityRule.ID)).containsExactly(8L);
    }

    @Test
    void suppressionOnTheTypeDropsTheFindingsInside() {
        Javac.Report report = Javac.main("""
                package dev.example;

                @SuppressWarnings("conventions:visibility")
                public class Sample {

                    void run() { }

                    static class Nested {

                        void walk() { }
                    }
                }
                """);

        assertThat(report.getAllOf(VisibilityRule.ID)).isEmpty();
    }

    @Test
    void suppressionOfAnotherRuleKeepsTheFinding() {
        Javac.Report report = Javac.main("""
                package dev.example;

                public class Sample {

                    @SuppressWarnings("conventions:imports")
                    void run() { }
                }
                """);

        assertThat(report.getAllLinesOf(VisibilityRule.ID)).containsExactly(6L);
    }

    @Test
    void suppressionOnAFieldIsFlaggedAndKeepsTheFinding() {
        Javac.Report report = Javac.main("""
                package dev.example;

                public class Sample {

                    @SuppressWarnings("conventions:visibility")
                    int count;
                }
                """);

        assertThat(report.getAllLinesOf(VisibilityRule.ID)).containsExactly(6L);
        assertThat(report.getAllLinesOf(SuppressWarningsRule.ID)).containsExactly(5L);
    }
}
