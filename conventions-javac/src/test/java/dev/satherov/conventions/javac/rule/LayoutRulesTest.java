package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.layout.BlockSpacingRule;
import dev.satherov.conventions.javac.rule.layout.ClassicSwitchRule;
import dev.satherov.conventions.javac.rule.layout.EmptyBodyRule;
import dev.satherov.conventions.javac.rule.layout.FormatterGuardRule;
import dev.satherov.conventions.javac.rule.layout.IfBracesRule;
import dev.satherov.conventions.javac.rule.layout.ImportsRule;
import dev.satherov.conventions.javac.rule.layout.MemberOrderRule;
import dev.satherov.conventions.javac.rule.layout.NumericSuffixRule;
import dev.satherov.conventions.javac.rule.layout.OneTypeRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about layout.
///
class LayoutRulesTest {

    @Test
    void importsFollowTheGroups() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Getter;

                import dev.example.other.Helper;

                import com.google.errorprone.annotations.CheckReturnValue;

                import com.sun.source.tree.Tree;

                import org.jspecify.annotations.Nullable;

                import java.util.List;
                import java.util.Map;
                import javax.lang.model.element.Element;

                class Sample { }
                """, """
                package dev.example.other;

                public class Helper { }
                """);

        assertThat(report.getAllOf(ImportsRule.ID)).isEmpty();
    }

    @Test
    void importsFlagOrderSpacingWildcardsNestedAndStatic() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jspecify.annotations.Nullable;
                import lombok.Getter;

                import java.util.List;

                import java.util.Map;
                import java.util.Map.Entry;
                import java.util.concurrent.*;

                import static java.util.Objects.requireNonNull;

                class Sample { }
                """);

        assertThat(report.getAllLinesOf(ImportsRule.ID)).containsExactly(4L, 8L, 9L, 10L, 12L);
    }

    @Test
    void importsAllowStaticImportsInTests() {
        Javac.Report report = Javac.test("""
                package dev.example;

                import java.util.List;

                import static java.util.Objects.requireNonNull;

                class SampleTest { }
                """);

        assertThat(report.getAllOf(ImportsRule.ID)).isEmpty();
    }

    @Test
    void memberOrderFlagsTheFirstMemberOutOfPlace() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    static final int MAX = 1;

                    int count;

                    void run() { }

                    Sample() { }

                    class Nested { }

                    static class Other {

                        void run() { }

                        static int LIMIT = 2;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(MemberOrderRule.ID)).containsExactly(11L, 19L);
    }

    @Test
    void memberOrderPutsRecordComponentsFirst() {
        Javac.Report report = Javac.main("""
                package dev.example;

                record Point(int x, int y) {

                    static final Point ORIGIN = new Point(0, 0);
                }
                """);

        assertThat(report.getAllOf(MemberOrderRule.ID)).isEmpty();
    }

    @Test
    void oneTypeFlagsASecondTopLevelType() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample { }

                class Second { }
                """);

        assertThat(report.getAllLinesOf(OneTypeRule.ID)).containsExactly(5L);
    }

    @Test
    void emptyBodyIsOneSpaceInBraces() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void first() {}

                    void second() {
                    }

                    void third() { }

                    void fourth() {
                        // Left empty on purpose.
                    }

                    void fifth(int value) {
                        switch (value) {
                            case 1 -> {
                            }
                            case 2 -> { }
                            default -> {
                            }
                        }
                    }

                    class Nested {}
                }
                """);

        assertThat(report.getAllLinesOf(EmptyBodyRule.ID)).containsExactly(5L, 7L, 18L, 21L, 26L);
    }

    @Test
    void ifBracesAllowOnlyOneLineThrowingGuards() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void run(int value) {
                        if (value < 0) throw new IllegalArgumentException("negative");
                        if (value == 0) return;
                        if (value == 1) {
                            return;
                        } else return;
                    }

                    void check(int value) {
                        if (value > 9) throw new IllegalArgumentException("too large: " + value);
                    }
                }
                """);

        assertThat(report.getAllLinesOf(IfBracesRule.ID)).containsExactly(7L, 10L, 14L);
    }

    @Test
    void blockSpacingFollowsNestedBlocks() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.List;

                class Sample {

                    int run(List<Integer> values) {
                        int total = 0;
                        for (int value : values) {
                            total += value;
                        }
                        if (total < 0) {
                            return 0;
                        }

                        values.forEach(value -> {
                            System.out.println(value);
                        });
                        do {
                            total--;
                        } while (total > 9);
                        try {
                            total++;
                        } finally {
                            total--;
                        }
                        // The total is final now.
                        return total;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(BlockSpacingRule.ID)).containsExactly(12L, 28L);
    }

    @Test
    void classicSwitchIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    int run(int value) {
                        switch (value) {
                            case 1:
                                return 1;
                            default:
                                return 0;
                        }
                    }

                    int arrows(int value) {
                        return switch (value) {
                            case 1 -> 1;
                            default -> 0;
                        };
                    }
                }
                """);

        assertThat(report.getAllLinesOf(ClassicSwitchRule.ID)).containsExactly(6L);
    }

    @Test
    void numericSuffixIsUppercase() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    long a = 5l;

                    float b = 1.0f;

                    double c = 1.0d;

                    long d = 5_000L;

                    int e = 0xff;
                }
                """);

        assertThat(report.getAllLinesOf(NumericSuffixRule.ID)).containsExactly(5L, 7L, 9L);
    }

    @Test
    void formatterGuardWrapsLongClauses() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.io.Serializable;

                class Sample {

                    // @formatter:off
                    @SuppressWarnings("serial")
                    class Guarded
                            implements
                                Runnable,
                                Serializable
                    {
                    // @formatter:on

                        @Override
                        public void run() { }
                    }

                    abstract class Unguarded implements Runnable, Serializable { }

                    abstract class Single implements Runnable { }
                }
                """);

        assertThat(report.getAllOf(FormatterGuardRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "Guarded has two or more supertypes or permitted types; open the declaration with // @formatter:off",
                        "Unguarded has two or more supertypes or permitted types; open the declaration with // @formatter:off",
                        "Unguarded opens its body with { on a line of its own, followed by // @formatter:on"
                );
    }

    @Test
    void formatterGuardOpensBelowTheAnnotations() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.io.Serializable;

                class Sample {

                    @SuppressWarnings("serial")
                    // @formatter:off
                    class Guarded
                            implements
                                Runnable,
                                Serializable
                    {
                    // @formatter:on

                        @Override
                        public void run() { }
                    }
                }
                """);

        assertThat(report.getAllOf(FormatterGuardRule.ID)).isEmpty();
    }
}
