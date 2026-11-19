package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.expression.IdentityLambdaRule;
import dev.satherov.conventions.javac.rule.expression.LocaleRule;
import dev.satherov.conventions.javac.rule.expression.NullTernaryRule;
import dev.satherov.conventions.javac.rule.expression.ObjectUnderscoreRule;
import dev.satherov.conventions.javac.rule.expression.QualificationRule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about expressions.
///
class ExpressionRulesTest {

    @Test
    void qualificationFlagsBareMembers() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    static final int MAX = 1;

                    int count;

                    int run() {
                        count = MAX;
                        return size();
                    }

                    int size() {
                        return this.count + Sample.MAX;
                    }
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "count is unqualified; write this.count",
                        "MAX is unqualified; write Sample.MAX",
                        "size is unqualified; write this.size"
                );
    }

    @Test
    void qualificationFlagsInheritedMembersAndLeavesOthersAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.ArrayList;

                import static java.lang.Math.max;

                class Sample extends ArrayList<String> {

                    enum Color {
                        RED,
                        BLUE
                    }

                    int run(Color color, int other) {
                        int local = max(other, 1);
                        int width = switch (color) {
                            case RED -> 1;
                            case BLUE -> 2;
                        };
                        return local + width + size();
                    }

                    @SuppressWarnings(value = "unchecked")
                    Sample self() {
                        return this;
                    }

                    Sample() {
                        super();
                    }
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("size is unqualified; write this.size");
    }

    @Test
    void qualificationLeavesLombokMethodsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Data;

                @Data
                class Sample {

                    private int count;
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID)).isEmpty();
    }

    @Test
    void localeIsRequired() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.Locale;

                class Sample {

                    void run(String text) {
                        text.toUpperCase();
                        text.toLowerCase(Locale.ROOT);
                        String.format("%d", 1);
                        String.format(Locale.ROOT, "%d", 1);
                        "%d".formatted(1);
                    }
                }
                """);

        assertThat(report.getAllLinesOf(LocaleRule.ID)).containsExactly(8L, 10L, 12L);
    }

    @Test
    void nullTernaryIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    String run(String text, String other, boolean ready) {
                        String first = text == null ? "" : text.strip();
                        String second = (null != text) ? text : "";
                        String third = text == null ? "none" : "some";
                        String fourth = text != null ? other.strip() : text;
                        String fifth = other == null ? text : other;
                        return ready ? first + third + fourth : second + fifth;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(NullTernaryRule.ID)).containsExactly(6L, 7L, 10L);
    }

    @Test
    void identityLambdaIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.function.Function;

                class Sample {

                    Function<String, String> same = text -> text;

                    Function<String, String> other = text -> text.strip();
                }
                """);

        assertThat(report.getAllLinesOf(IdentityLambdaRule.ID)).containsExactly(7L);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_22)
    void objectUnderscoreIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void run(StringBuilder builder) {
                        Object _ = builder.append("x");
                    }
                }
                """);

        assertThat(report.getAllLinesOf(ObjectUnderscoreRule.ID)).containsExactly(6L);
    }
}
